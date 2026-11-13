"""Restore project image checkpoints. Historical filename retained for existing mounts."""
import base64, hashlib, json, os, re, tempfile, argparse
from pathlib import Path

MAX_BYTES = 10_485_760
MAX_MANIFEST_BYTES = 4 * ((MAX_BYTES + 2) // 3) + 4096

def unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate checkpoint field")
        result[key] = value
    return result

def read_checkpoint(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_MANIFEST_BYTES:
        raise ValueError("Invalid checkpoint file")
    row = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_fields)
    if not isinstance(row, dict) or set(row) != {"key", "content_type", "sha256", "bytes_base64"}:
        raise ValueError("Invalid checkpoint fields")
    if not all(isinstance(v, str) for v in row.values()):
        raise ValueError("Invalid checkpoint types")
    key = row["key"]
    if len(key.encode()) > 1024 or not re.fullmatch(r"images/[A-Za-z0-9_-][A-Za-z0-9_.-]*(?:/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*", key):
        raise ValueError("Invalid image namespace")
    if path.name != hashlib.sha256(key.encode()).hexdigest() + ".json":
        raise ValueError("Checkpoint key identity mismatch")
    data = base64.b64decode(row["bytes_base64"], validate=True)
    if not 0 < len(data) <= MAX_BYTES or row["content_type"] not in {"image/jpeg", "image/png", "image/webp"}:
        raise ValueError("Invalid image checkpoint")
    if hashlib.sha256(data).hexdigest() != row["sha256"]:
        raise ValueError("Checkpoint checksum mismatch")
    return row, data

def restore(directory, client, bucket):
    root = Path(directory)
    if not root.is_dir():
        raise ValueError("Checkpoint volume is not mounted")
    paths = sorted(root.glob("*.json"))
    # Reject corrupt batches before writing any object. Keep only one image in memory.
    for path in paths:
        read_checkpoint(path)
    for path in paths:
        row, data = read_checkpoint(path)
        client.put_object(Bucket=bucket, Key=row["key"], Body=data, ContentType=row["content_type"], ACL="private", CacheControl="no-store")
        response = client.get_object(Bucket=bucket, Key=row["key"])
        with response["Body"] as stream:
            stored = stream.read(MAX_BYTES + 1)
        if stored != data or response.get("ContentType") != row["content_type"]:
            raise ValueError("Restored object verification failed")
    return len(paths)

def capture(directory, client, bucket):
    """Backfill final image bytes that predate automatic checkpointing; no deletions."""
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    count = 0
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix="images/"):
        for item in page.get("Contents", []):
            key = item["Key"]
            if len(key.encode()) > 1024 or not re.fullmatch(r"images/[A-Za-z0-9_-][A-Za-z0-9_.-]*(?:/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*", key):
                raise ValueError("Invalid stored image key")
            response = client.get_object(Bucket=bucket, Key=key)
            with response["Body"] as stream:
                data = stream.read(MAX_BYTES + 1)
            if not 0 < len(data) <= MAX_BYTES or response.get("ContentType") not in {"image/png", "image/jpeg", "image/webp"}:
                raise ValueError("Invalid stored image")
            row = {"key": key, "content_type": response["ContentType"], "sha256": hashlib.sha256(data).hexdigest(), "bytes_base64": base64.b64encode(data).decode()}
            destination = root / (hashlib.sha256(key.encode()).hexdigest() + ".json")
            temporary = None
            try:
                with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=root, suffix=".tmp", delete=False) as output:
                    temporary = Path(output.name)
                    json.dump(row, output)
                    output.flush()
                    os.fsync(output.fileno())
                os.replace(temporary, destination)
                temporary = None
                if os.name == "posix":
                    fd = os.open(root, os.O_RDONLY)
                    try: os.fsync(fd)
                    finally: os.close(fd)
            finally:
                if temporary is not None: temporary.unlink(missing_ok=True)
            count += 1
    return count

if __name__ == "__main__":
    import boto3
    parser = argparse.ArgumentParser(description="Durability for final project images in local development")
    parser.add_argument("--capture-existing", action="store_true")
    parser.add_argument("--endpoint", default="http://localhost:4566")
    args = parser.parse_args()
    client = boto3.client("s3", endpoint_url=args.endpoint, region_name="us-east-1", aws_access_key_id="test", aws_secret_access_key="test")
    directory = os.environ.get("IMAGE_CHECKPOINT_DIRECTORY", "/catalog-product-checkpoints")
    operation = capture if args.capture_existing else restore
    print("Verified project image checkpoints " + ("captured:" if args.capture_existing else "restored:"), operation(directory, client, os.environ.get("LOCAL_STORAGE_BUCKET", "backend-images")))
