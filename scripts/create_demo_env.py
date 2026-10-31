#!/usr/bin/env python3
"""Generate a LOCAL PRIVATE .env.demo.local from Backend-SWE/.env.example.

Does not create approval records, database contents, or admin credentials.
Refuses to overwrite any existing .env.demo.local.
"""

import argparse
import json
import os
from pathlib import Path
import secrets
import sys
from urllib.parse import quote


def create(backend, project='demo-real-images', db_port='55433', server_port='18081', storage_port='14567'):
    backend = Path(backend).resolve()
    template = backend / '.env.example'
    destination = backend / '.env.demo.local'
    if not template.is_file():
        raise ValueError('BACKEND_ENV_TEMPLATE_MISSING')
    if destination.exists() or destination.is_symlink():
        raise ValueError('PRIVATE_ENV_ALREADY_EXISTS: will not overwrite')
    base = {}
    for line in template.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise ValueError('ENV_TEMPLATE_INVALID')
        key, value = line.split('=', 1)
        if not key.isidentifier():
            raise ValueError('ENV_TEMPLATE_KEY_INVALID')
        base[key] = value.strip()

    required = ('LOCAL_DB_HOST', 'LOCAL_DB_PORT', 'LOCAL_DB_NAME',
                'LOCAL_DB_USER', 'LOCAL_DB_PASSWORD', 'JWT_SECRET',
                'SERVER_PORT', 'LOCAL_STORAGE_PORT', 'COMPOSE_PROJECT_NAME',
                'CATALOG_IMPORT_DSN', 'CATALOG_IMAGES_CHECKPOINT_DIRECTORY')
    if any(k not in base for k in required):
        raise ValueError('ENV_TEMPLATE_REQUIRED_VARIABLE_MISSING')

    if not project.startswith('demo-') or not project.replace('-','').isalnum():
        raise ValueError('PROJECT_MUST_BE_ISOLATED_DEMO')
    if any(not str(port).isdigit() or not 1<=int(port)<=65535 for port in (db_port,server_port,storage_port)):
        raise ValueError('EXPLICIT_PORTS_REQUIRED')
    base['COMPOSE_PROJECT_NAME'] = project
    base['LOCAL_DB_PORT'] = str(db_port)
    base['SERVER_PORT'] = str(server_port)
    base['LOCAL_STORAGE_PORT'] = str(storage_port)
    base['LOCAL_DB_HOST'] = '127.0.0.1'
    base['LOCAL_DB_NAME'] = 'backend'
    base['LOCAL_DB_PASSWORD'] = secrets.token_urlsafe(32)
    base['JWT_SECRET'] = secrets.token_urlsafe(48)
    base['CATALOG_IMAGES_CHECKPOINT_DIRECTORY'] = str(
        backend / 'tools/catalog-import/output/demo-product-checkpoints'
    )
    base['CATALOG_IMPORT_DSN'] = (
        'postgresql://'
        + quote(base['LOCAL_DB_USER'], safe='')
        + ':' + quote(base['LOCAL_DB_PASSWORD'], safe='')
        + '@127.0.0.1:' + base['LOCAL_DB_PORT']
        + '/' + quote(base['LOCAL_DB_NAME'], safe='')
    )
    base['DEMO_ADMIN_CREDENTIALS'] = str(backend / '.private/demo-admin-credentials.json')
    # Existing machine may already use the template ports. Don't blindly boot
    # the stack until availability and project isolation have been checked.
    base['LOCAL_STORAGE_ENDPOINT'] = 'http://127.0.0.1:' + base['LOCAL_STORAGE_PORT']
    base['LOCAL_STORAGE_SIGNING_ENDPOINT'] = base['LOCAL_STORAGE_ENDPOINT']
    body = ('# GENERATED PRIVATE LOCAL DEMO ENVIRONMENT. NEVER COMMIT.\n'
            '# Ports below may need changing if already in use.\n'
            + ''.join(f'{key}={value}\n' for key, value in base.items()))
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(destination, flags, 0o600)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            stream.write(body)
    except Exception:
        destination.unlink(missing_ok=True)
        raise
    return {
        'created': str(destination),
        'mode': '0600',
        'compose_project': base['COMPOSE_PROJECT_NAME'],
        'local_db_port': base['LOCAL_DB_PORT'],
        'server_port': base['SERVER_PORT'],
        'local_storage_port': base['LOCAL_STORAGE_PORT'],
        'admin_credentials_ready': False,
        'approvals_created': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', type=Path, required=True)
    parser.add_argument('--project',default='demo-real-images')
    parser.add_argument('--db-port',default='55433')
    parser.add_argument('--server-port',default='18081')
    parser.add_argument('--storage-port',default='14567')
    args = parser.parse_args()
    try:
        print(json.dumps(create(args.backend,args.project,args.db_port,args.server_port,args.storage_port), indent=2))
    except (OSError, ValueError, TypeError) as error:
        print(json.dumps({'error': str(error)}), file=sys.stderr)
        sys.exit(2)


if __name__ == '__main__':
    main()
