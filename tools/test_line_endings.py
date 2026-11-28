"""Integrity checks for the encoding-preserving newline audit/apply workflow."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import line_endings


class LineEndingTests(unittest.TestCase):
    def make_plan(self, root, files, lfs=()):
        for name, data in files.items():
            (root/name).write_bytes(data)

        def git(_root, *args, **kwargs):
            if args[0] == 'ls-files':
                return b''.join(name.encode()+b'\0' for name in files)
            return b''.join(name.encode()+b'\0filter\0'+(b'lfs' if name in lfs else b'unspecified')+b'\0' for name in files)

        with patch.object(line_endings, 'git', git):
            plan = line_endings.audit(root)
        destination = root/'plan.json'
        destination.write_text(json.dumps(plan), encoding='utf-8')
        return destination

    def test_encoding_bom_binary_lfs_and_batch_preserved(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            files = {'mixed.sql': b'a\r\nb\nc\r', 'utf8.txt': b'\xef\xbb\xbfa\r\n',
                     'utf16.txt': b'\xff\xfe'+'a\r\nb\n'.encode('utf-16-le'),
                     'latin.txt': b'caf\xe9\r\n', 'image.bin': b'\x00\r\n\xff',
                     'large.txt': b'lfs managed\r\n', 'build.cmd': b'a\nb\r\n'}
            plan = self.make_plan(root, files, lfs={'large.txt'})
            line_endings.apply_plan(plan, root/'backup')
            expected = {'mixed.sql': b'a\nb\nc\n', 'utf8.txt': b'\xef\xbb\xbfa\n',
                        'utf16.txt': b'\xff\xfe'+'a\nb\n'.encode('utf-16-le'),
                        'latin.txt': b'caf\xe9\n', 'build.cmd': b'a\r\nb\r\n'}
            for filename, data in files.items():
                self.assertEqual((root/filename).read_bytes(), expected.get(filename, data))
            for filename in expected:
                self.assertEqual((root/'backup'/filename).read_bytes(), files[filename])
            self.assertFalse((root/'backup/image.bin').exists())
            self.assertFalse((root/'backup/large.txt').exists())

    def test_stale_plan_refused_before_any_write(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            plan = self.make_plan(root, {'a.txt': b'a\r\n', 'b.txt': b'b\r\n'})
            (root/'b.txt').write_bytes(b'edited\r\n')
            with self.assertRaisesRegex(ValueError, 'changed since audit'):
                line_endings.apply_plan(plan, root/'backup')
            self.assertEqual((root/'a.txt').read_bytes(), b'a\r\n')
            self.assertFalse((root/'backup').exists())


if __name__ == '__main__':
    unittest.main()
