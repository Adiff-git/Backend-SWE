"""Audit tracked files and normalize newlines without changing encoding or BOM.

Usage: python tools/line_endings.py REPO --report audit.json
LFS files and binary content are always preserved. Reports include all hashes.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import re
from pathlib import Path
import subprocess


def git(root, *args, data=None):
    return subprocess.run(['git', '-C', str(root), *args], input=data,
                          stdout=subprocess.PIPE, check=True).stdout


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text_encoding(data):
    for bom, encoding in ((b'\xff\xfe\x00\x00', 'utf-32-le'),
                          (b'\x00\x00\xfe\xff', 'utf-32-be'),
                          (b'\xff\xfe', 'utf-16-le'), (b'\xfe\xff', 'utf-16-be'),
                          (b'\xef\xbb\xbf', 'utf-8')):
        if data.startswith(bom):
            return bom, encoding
    if b'\x00' in data:
        return None
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError:
        # Single-byte encodings can normalize ASCII newlines losslessly.
        text = data.decode('latin-1')
        if re.search(r'[\x00-\x08\x0b\x0e-\x1f]', text):
            return None
        return b'', 'latin-1'
    if re.search(r'[\x00-\x08\x0b\x0e-\x1f]', text):
        return None
    return b'', 'utf-8'


def audit(root):
    paths = git(root, 'ls-files', '-z').split(b'\x00')[:-1]
    attrs = git(root, 'check-attr', '-z', '--stdin', 'filter', data=b'\x00'.join(paths)+b'\x00').split(b'\x00')
    lfs = {attrs[i].decode('utf-8') for i in range(0, len(attrs)-2, 3) if attrs[i+2] == b'lfs'}
    rows = []
    for encoded in paths:
        name = encoded.decode('utf-8')
        path = root / name
        if path.is_symlink() or not path.is_file():
            rows.append({'path': name, 'kind': 'link-or-missing'})
            continue
        data = path.read_bytes()
        row = {'path': name, 'bytes': len(data), 'before_sha256': sha(data)}
        encoding = text_encoding(data)
        if name in lfs or data.startswith(b'version https://git-lfs.github.com/spec/v1\n'):
            row['kind'] = 'lfs'
        elif encoding is None:
            row['kind'] = 'binary'
        else:
            bom, codec = encoding
            text = data[len(bom):].decode(codec)
            crlf = text.count('\r\n')
            lf = text.count('\n')-crlf
            cr = text.count('\r')-crlf
            kinds = sum(bool(n) for n in (crlf, lf, cr))
            row.update(kind='text', encoding=codec, bom=bom.hex(), crlf=crlf, lf=lf, cr=cr,
                       endings='mixed' if kinds > 1 else 'CRLF' if crlf else 'LF' if lf else 'CR' if cr else 'none')
            canonical = text.replace('\r\n', '\n').replace('\r', '\n')
            row['content_sha256'] = sha(bom+canonical.encode(codec))
            target = canonical.replace('\n', '\r\n') if path.suffix.lower() in {'.bat', '.cmd'} else canonical
            target_bytes = bom+target.encode(codec)
            row['planned_sha256'] = sha(target_bytes)
            row['needs_normalization'] = target_bytes != data
            row['exception'] = path.suffix.lower() in {'.bat', '.cmd'}
        row['after_sha256'] = sha(data)
        row['changed'] = row['before_sha256'] != row['after_sha256']
        rows.append(row)
    return {'repository': str(root), 'normalized': False,
            'counts': dict(Counter(r.get('endings', r['kind']) for r in rows)),
            'changed_files': sum(r.get('changed', False) for r in rows), 'files': rows}


def apply_plan(plan_path, backup):
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    root = Path(plan['repository']).resolve()
    changes = []
    for row in plan['files']:
        if not row.get('needs_normalization'):
            continue
        path = (root/row['path']).resolve()
        if not path.is_relative_to(root) or path.is_symlink():
            raise ValueError('Plan path leaves repository')
        data = path.read_bytes()
        if sha(data) != row['before_sha256']:
            raise ValueError(f"File changed since audit: {row['path']}")
        bom = bytes.fromhex(row['bom'])
        text = data[len(bom):].decode(row['encoding'])
        canonical = text.replace('\r\n', '\n').replace('\r', '\n')
        if sha(bom+canonical.encode(row['encoding'])) != row['content_sha256']:
            raise ValueError('Content fingerprint mismatch')
        normalized = canonical.replace('\n', '\r\n') if row['exception'] else canonical
        result = bom+normalized.encode(row['encoding'])
        if sha(result) != row['planned_sha256']:
            raise ValueError('Planned bytes mismatch')
        changes.append((path, data, result, row['path']))
    # Back up every original before making the first write.
    if backup.exists():
        raise ValueError('Backup destination must be new')
    for _, data, _, relative in changes:
        destination = backup/relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    for path, _, result, _ in changes:
        path.write_bytes(result)
    print(json.dumps({'applied_files': len(changes), 'backup': str(backup)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repo', type=Path, nargs='?')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--apply-plan', type=Path)
    parser.add_argument('--backup', type=Path)
    args = parser.parse_args()
    if args.apply_plan:
        if not args.backup:
            parser.error('--backup is required with --apply-plan')
        apply_plan(args.apply_plan, args.backup)
        raise SystemExit(0)
    if not args.repo or not args.report:
        parser.error('repo and --report are required for an audit')
    report = audit(args.repo.resolve())
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps({k:v for k,v in report.items() if k != 'files'}))
