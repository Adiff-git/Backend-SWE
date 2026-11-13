import unittest, tempfile, json, hashlib, base64, io
from pathlib import Path
from restore_product_images import restore, capture

class Store:
    def __init__(self): self.objects = {}; self.writes = 0
    def put_object(self, **kw):
        self.writes += 1; self.objects[kw["Key"]] = (kw["Body"], kw["ContentType"])
        assert kw["ACL"] == "private" and kw["CacheControl"] == "no-store"
    def get_object(self, **kw):
        data, mime = self.objects[kw["Key"]]
        return {"Body": io.BytesIO(data), "ContentType": mime}
    def get_paginator(self, name): return self
    def paginate(self, **kw): return [{"Contents": [{"Key": k} for k in self.objects]}]

class RestoreTests(unittest.TestCase):
    def manifest(self, root, key, **change):
        row = dict(key=key, content_type="image/png", sha256=hashlib.sha256(b"image").hexdigest(), bytes_base64=base64.b64encode(b"image").decode()); row.update(change)
        p=Path(root)/(hashlib.sha256(key.encode()).hexdigest()+".json");p.write_text(json.dumps(row));return p
    def test_general_images_and_legacy_format_are_idempotent(self):
        with tempfile.TemporaryDirectory() as root:
            keys=["images/users/1/abc", "images/reviews/12/photo", "images/products/a/b/hash", "images/banners/home.png"]
            for k in keys: self.manifest(root,k)
            store=Store();self.assertEqual(restore(root,store,"bucket"),4);self.assertEqual(restore(root,store,"bucket"),4);self.assertEqual(set(store.objects),set(keys))
    def test_bad_batch_is_rejected_before_first_write(self):
        with tempfile.TemporaryDirectory() as root:
            self.manifest(root,"images/users/1/good");self.manifest(root,"images/reviews/bad",sha256="0"*64)
            store=Store()
            with self.assertRaises(ValueError):restore(root,store,"bucket")
            self.assertEqual(store.writes,0)
    def test_namespace_traversal_and_staging_are_rejected(self):
        for key in ["staging/users/1/a","images/../private","images//bad","images/a%2fb","images/a\\b"]:
            with self.subTest(key=key),tempfile.TemporaryDirectory() as root:
                self.manifest(root,key)
                with self.assertRaises(ValueError):restore(root,Store(),"bucket")
    def test_missing_volume_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):restore(Path(root)/"missing",Store(),"bucket")
    def test_wrong_identity_or_duplicate_fields_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            p=self.manifest(root,"images/users/1/a");p.rename(Path(root)/("0"*64+".json"))
            with self.assertRaises(ValueError):restore(root,Store(),"bucket")
        with tempfile.TemporaryDirectory() as root:
            p=self.manifest(root,"images/users/1/a");p.write_text(p.read_text().replace('{','{"key":"bad",',1))
            with self.assertRaises(ValueError):restore(root,Store(),"bucket")
    def test_capture_existing_roundtrip_and_temp_ignored(self):
        with tempfile.TemporaryDirectory() as root:
            old=Store();old.objects={"images/users/1/old":(b"avatar","image/png"),"images/reviews/2/new":(b"review","image/webp")}
            self.assertEqual(capture(root,old,"bucket"),2);Path(root,"incomplete.tmp").write_text("partial")
            new=Store();self.assertEqual(restore(root,new,"bucket"),2);self.assertEqual(new.objects,old.objects)
            self.assertEqual(len(list(Path(root).glob("*.json"))),2)

if __name__ == "__main__":unittest.main()
