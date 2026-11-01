"""Offline safety checks; never starts Docker or writes a catalog database."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/catalog-import'))
import amazon_import
pg_spec=importlib.util.spec_from_file_location('real_import_postgres',ROOT/'tools/catalog-import/import_postgres.py')
import_postgres=importlib.util.module_from_spec(pg_spec)
pg_spec.loader.exec_module(import_postgres)

spec=importlib.util.spec_from_file_location('run_demo',ROOT/'scripts/run_demo.py')
run_demo=importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_demo)


class BootstrapSafetyTests(unittest.TestCase):
    def test_fresh_and_partial_stage_selection(self):
        self.assertEqual(run_demo.stages_needed(0),(100,1000,1500))
        self.assertEqual(run_demo.stages_needed(100),(1000,1500))
        self.assertEqual(run_demo.stages_needed(1000),(1500,))

    def test_complete_rerun_has_no_import_stage(self):
        self.assertEqual(run_demo.stages_needed(1500),())

    def test_incompatible_count_and_volume_are_rejected(self):
        with self.assertRaises(RuntimeError):run_demo.stages_needed(1501)
        owned=json.dumps([{'Labels':{'com.docker.compose.project':'demo-other',
                                    'com.docker.compose.volume':'postgres-data'}}]).encode()
        self.assertFalse(run_demo.volume_owned(owned,'demo-expected'))
        self.assertTrue(run_demo.volume_owned(owned,'demo-other'))

    def test_unexpected_existing_product_is_rejected_before_import(self):
        import uuid
        class Result:
            def __init__(self,rows):self.rows=rows
            def __iter__(self):return iter(self.rows)
        class Connection:
            def execute(self,sql):
                if sql=='SELECT id FROM public.products':return Result([(uuid.uuid4(),)])
                return Result([])
        with self.assertRaisesRegex(amazon_import.AmazonImportError,'Unexpected existing'):
            amazon_import.validate_existing_scope(Connection(),[],{})

    def test_target_and_stage_authorization_are_exact(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);backup=root/'backup.dump';backup.write_bytes(b'PGDMPtest')
            import hashlib
            record={'decision':'APPROVED','target':'backend@127.0.0.1:55433',
                    'manifest_sha256':'a'*64,'backup_sha256':hashlib.sha256(backup.read_bytes()).hexdigest(),
                    'restore_tested':True,'approved_by':'operator','evidence_reference':'review-1','stage':100}
            path=root/'approval.json';path.write_text(json.dumps(record))
            amazon_import.authorization(path,record['target'],'a'*64,backup,100)
            with self.assertRaises(amazon_import.AmazonImportError):
                amazon_import.authorization(path,'backend@127.0.0.1:55432','a'*64,backup,100)
            with self.assertRaises(amazon_import.AmazonImportError):
                amazon_import.authorization(path,record['target'],'b'*64,backup,100)
            with self.assertRaises(amazon_import.AmazonImportError):
                amazon_import.authorization(path,record['target'],'a'*64,backup,1000)
            backup.write_bytes(b'PGDMPchanged')
            with self.assertRaises(amazon_import.AmazonImportError):
                amazon_import.authorization(path,record['target'],'a'*64,backup,100)

    def test_incorrect_database_target_fails_before_connection(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        args=SimpleNamespace(target_kind='local-development',expect_database='backend',
            expect_host='127.0.0.1',expect_port=55433,
            confirm_target='backend@127.0.0.1:55433',dsn_env='CATALOG_IMPORT_DSN')
        with patch.dict('os.environ',{'CATALOG_IMPORT_DSN':'postgresql://user:pass@127.0.0.1:55432/backend'}):
            with self.assertRaisesRegex(import_postgres.ImportErrorDetail,'does not match'):
                import_postgres.target_connection(args,read_only=True)

    def test_backup_format_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);backup=root/'backup.dump';backup.write_bytes(b'not-a-dump')
            path=root/'approval.json';path.write_text(json.dumps({'decision':'APPROVED','target':'target',
                'manifest_sha256':'a'*64,'restore_tested':True,'approved_by':'operator',
                'evidence_reference':'review-1','stage':100}))
            with self.assertRaisesRegex(amazon_import.AmazonImportError,'custom-format'):
                amazon_import.authorization(path,'target','a'*64,backup,100)


if __name__=='__main__':unittest.main()
