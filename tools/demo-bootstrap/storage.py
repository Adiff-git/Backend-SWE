"""Signed local S3 plus existing-format checkpoints and exact durable ownership."""
from __future__ import annotations

import base64
import hashlib
import os
import io
from pathlib import Path
import re
from typing import Any

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError, BotoCoreError
from PIL import Image, UnidentifiedImageError

from bootstrap import (BUCKET, DATABASE, PROJECT, BootstrapError, atomic_json,
                       digest_bytes, digest_file, read_json, require,private_fault,member_path)

MAX_IMAGE_BYTES = 10485760
MAX_CHECKPOINT_BYTES = 4 * ((MAX_IMAGE_BYTES + 2) // 3) + 4096
RESOURCE = {"project": PROJECT, "database": DATABASE, "bucket": BUCKET}
KEY_PATTERN = r"images/[A-Za-z0-9_-][A-Za-z0-9_.-]*(?:/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*"
IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}


def checkpoint_name(key: str) -> str:
    require(isinstance(key, str) and len(key.encode()) <= 1024
            and bool(re.fullmatch(KEY_PATTERN, key)), "INVALID_OBJECT_KEY", "Invalid image object key")
    return hashlib.sha256(key.encode()).hexdigest() + ".json"


def read_checkpoint(path: Path) -> tuple[dict[str, str], bytes]:
    row = read_json(path, max_bytes=MAX_CHECKPOINT_BYTES)
    require(isinstance(row, dict) and set(row) == {"key", "content_type", "sha256", "bytes_base64"}
            and all(isinstance(v, str) for v in row.values()), "CORRUPT_CHECKPOINT",
            "Image checkpoint has invalid fields")
    require(path.name == checkpoint_name(row["key"]) and row["content_type"] in IMAGE_TYPES,
            "CORRUPT_CHECKPOINT", "Image checkpoint identity/type is invalid")
    try:
        data = base64.b64decode(row["bytes_base64"], validate=True)
    except ValueError:
        raise BootstrapError("CORRUPT_CHECKPOINT", "Image checkpoint bytes are invalid") from None
    require(0 < len(data) <= MAX_IMAGE_BYTES and digest_bytes(data) == row["sha256"],
            "CORRUPT_CHECKPOINT", "Image checkpoint checksum/size is invalid")
    return row, data


def inspect_checkpoints(directory: Path) -> dict[str, dict[str, Any]]:
    require(directory.is_dir() and not directory.is_symlink(), "CHECKPOINT_DIRECTORY",
            "Verified existing checkpoint volume must be mounted for inspection")
    result: dict[str, dict[str, Any]] = {}
    for path in sorted(directory.glob("*.json")):
        row, data = read_checkpoint(path)
        require(row["key"] not in result, "CORRUPT_CHECKPOINT", "Duplicate checkpoint object identity")
        result[row["key"]] = {"content_type": row["content_type"], "sha256": row["sha256"],
                              "bytes": len(data), "filename": path.name,
                              "checkpoint_sha256": digest_file(path)}
    return result


class OwnershipLedger:
    def __init__(self, directory: Path, *, allow_new_empty: bool = False):
        require(directory.is_dir() and not directory.is_symlink()
                and not (directory / ".bootstrap").is_symlink(), "UNSAFE_PATH",
                "Ownership directory must remain inside the verified checkpoint volume")
        self.directory = directory
        self.path = directory / ".bootstrap" / "ownership.json"
        if self.path.exists():
            self.value = read_json(self.path)
            require(self.value.get("control_version") == 1 and self.value.get("resource") == RESOURCE
                    and isinstance(self.value.get("assets"), dict), "OWNERSHIP_CONFLICT",
                    "Ownership ledger belongs to a different resource set or contract")
        else:
            require(allow_new_empty, "OWNERSHIP_MISSING", "Ownership evidence is missing; existing assets cannot be adopted or cleaned")
            self.value = {"control_version": 1, "resource": RESOURCE, "assets": {}}

    def save(self) -> None:
        atomic_json(self.path, self.value)

    def begin_attempt(self, attempt: str, artifact: str, anchor: str) -> None:
        old = self.value.get("attempt")
        require(old is None or old == {"id": attempt, "artifact": artifact, "anchor": anchor}
                or self.value.get("reset", {}).get("state") == "COMPLETED", "OWNERSHIP_CONFLICT",
                "Cannot change the current attempt without an explicitly completed reset")
        self.value["attempt"] = {"id": attempt, "artifact": artifact, "anchor": anchor}
        self.save()

    def intend(self, key: str, image: dict[str, Any]) -> dict[str, Any]:
        require(key.startswith("images/demo-bootstrap/"), "OWNERSHIP_CONFLICT", "Imported key is outside the approved namespace")
        filename = checkpoint_name(key)
        entry = {"key": key, "sha256": image["sha256"], "content_type": image["content_type"],
                 "bytes": image["bytes"], "product_id": image["product_id"],
                 "source_identity": image["source_identity"], "checkpoint": filename,
                 "upload_state": "INTENDED", "cleanup_state": "RETAIN"}
        previous = self.value["assets"].get(key)
        if previous:
            require(all(previous[k] == entry[k] for k in ("key", "sha256", "content_type", "bytes", "product_id", "source_identity", "checkpoint")),
                    "OWNERSHIP_CONFLICT", "Imported object identity/content conflicts with its durable ownership record")
            require(previous.get("cleanup_state") != "DELETE_INTENDED", "RESET_INCOMPLETE", "Finish saved cleanup before asset import")
            if previous.get("cleanup_state") != "DELETED":
                entry = previous
            else:
                require(not (self.directory / filename).exists(), "CHECKPOINT_CONFLICT", "Deleted checkpoint unexpectedly reappeared")
                self.value["assets"][key] = entry
        else:
            require(not (self.directory / filename).exists(), "OWNERSHIP_CONFLICT", "An untracked checkpoint already occupies the intended object identity")
            self.value["assets"][key] = entry
        self.save()  # Durable ownership intention must precede S3/checkpoint writes.
        return entry

    def verified(self, key: str) -> None:
        entry = self.value["assets"][key]
        path = self.directory / entry["checkpoint"]
        row, data = read_checkpoint(path)
        require(row["key"] == key and row["sha256"] == entry["sha256"]
                and row["content_type"] == entry["content_type"] and len(data) == entry["bytes"],
                "CHECKPOINT_CONFLICT", "Checkpoint readback differs from ownership evidence")
        entry.update(upload_state="VERIFIED", checkpoint_sha256=digest_file(path))
        self.save()


def local_s3():
    return boto3.client("s3", endpoint_url="http://localstack:4567", region_name="us-east-1",
                        aws_access_key_id="test", aws_secret_access_key="test",
                        config=Config(signature_version="s3v4", s3={"addressing_style": "path"},
                                      connect_timeout=60, read_timeout=60,
                                      retries={"total_max_attempts": 1}, proxies={}))


def object_metadata(client, key: str) -> dict[str, Any] | None:
    checkpoint_name(key)
    try:
        response = client.get_object(Bucket=BUCKET, Key=key)
        with response["Body"] as stream:
            data = stream.read(MAX_IMAGE_BYTES + 1)
        require(0 < len(data) <= MAX_IMAGE_BYTES, "OBJECT_CONFLICT", "Existing object size is invalid")
        return {"sha256": digest_bytes(data), "content_type": response.get("ContentType"), "bytes": len(data)}
    except ClientError as error:
        if error.response.get("Error", {}).get("Code") in {"NoSuchKey", "NotFound", "404"}:
            return None
        raise BootstrapError("STORAGE_UNAVAILABLE", "Signed local object access failed", 5) from None
    except BotoCoreError:
        raise BootstrapError("STORAGE_UNAVAILABLE", "Signed local object access failed", 5) from None


def compare_owned_objects(client, ledger: OwnershipLedger, *, required: set[str] | None = None) -> dict[str, int]:
    counts = {"verified": 0, "missing": 0}
    for key, entry in ledger.value["assets"].items():
        if entry["cleanup_state"] == "DELETED" or (required is not None and key not in required):
            continue
        actual = object_metadata(client, key)
        if actual is None:
            counts["missing"] += 1
        else:
            require(all(actual[k] == entry[k] for k in ("sha256", "content_type", "bytes")),
                    "OBJECT_CONFLICT", "Existing owned object conflicts with trusted content; restore/overwrite is forbidden")
            counts["verified"] += 1
    return counts


def preflight_checkpoints(client, ledger: OwnershipLedger) -> None:
    """Existing restore rewrites every root checkpoint, including non-owned ones."""
    for key,entry in inspect_checkpoints(ledger.directory).items():
        actual=object_metadata(client,key)
        require(actual is None or all(actual[k]==entry[k] for k in ('sha256','content_type','bytes')),
                'CHECKPOINT_LIVE_CONFLICT','Live object differs from a restore checkpoint; service restoration is forbidden')
    compare_owned_objects(client,ledger)


def reconcile_restore_sources(ledger: OwnershipLedger, required: set[str]) -> None:
    """Archive unreferenced owned restore inputs without deleting their bytes.

    LocalStack's existing loader restores every root JSON file. Moving an owned
    unused input outside that glob prevents a restart from repairing deleted DB
    references. Unrelated files are never moved. Durable intent makes this resumable.
    """
    for key,entry in ledger.value['assets'].items():
        if entry['cleanup_state']!='RETAIN':continue
        root=ledger.directory/entry['checkpoint']
        archive_name='.bootstrap/retained/'+entry['checkpoint']
        archived=member_path(ledger.directory,archive_name)
        for path in (root,archived):
            if path.exists():
                row,data=read_checkpoint(path)
                require(row['key']==key and row['sha256']==entry['sha256'] and row['content_type']==entry['content_type']
                        and len(data)==entry['bytes'],'CHECKPOINT_CONFLICT','Restore source changed; no move is permitted')
        source,target=(archived,root) if key in required else (root,archived)
        if source.exists():
            entry['archived_checkpoint']=archive_name
            entry['checkpoint_restore']='ROOT_INTENDED' if key in required else 'ARCHIVE_INTENDED'
            ledger.save()
            target.parent.mkdir(parents=True,exist_ok=True)
            os.replace(source,target)
            if os.name=='posix':
                for directory in {source.parent,target.parent}:
                    fd=os.open(directory,os.O_RDONLY|os.O_DIRECTORY)
                    try:os.fsync(fd)
                    finally:os.close(fd)
        entry['checkpoint_restore']='ROOT' if key in required else 'ARCHIVED'
        ledger.save()


def save_checkpoint(directory: Path, key: str, content_type: str, data: bytes) -> None:
    require(content_type in IMAGE_TYPES and 0 < len(data) <= MAX_IMAGE_BYTES,
            "INVALID_IMAGE", "Checkpoint input is not a permitted image")
    path = directory / checkpoint_name(key)
    atomic_json(path, {"key": key, "content_type": content_type, "sha256": digest_bytes(data),
                       "bytes_base64": base64.b64encode(data).decode("ascii")})


def validate_image(data: bytes, image: dict[str, Any]) -> None:
    require(0 < len(data) <= MAX_IMAGE_BYTES and len(data) == image.get("bytes")
            and digest_bytes(data) == image.get("sha256"),
            "INVALID_IMAGE", "Image content differs from the reviewed asset")
    try:
        with Image.open(io.BytesIO(data)) as decoded:
            content_type = Image.MIME.get(decoded.format)
            require(content_type in IMAGE_TYPES and content_type == image.get("content_type")
                    and decoded.width == image.get("width") and decoded.height == image.get("height"),
                    "INVALID_IMAGE", "Image type/dimensions differ from the reviewed asset")
            decoded.verify()
    except (OSError, ValueError, UnidentifiedImageError, Image.DecompressionBombError):
        raise BootstrapError("INVALID_IMAGE", "Reviewed image cannot be safely decoded") from None


def ensure_asset(client, ledger: OwnershipLedger, key: str, image: dict[str, Any], data: bytes,
                 *, repair: bool = False) -> dict[str, str]:
    """Create only absent objects; never replace conflicting or untracked bytes.

    Repair callers must pass only surviving DB references, not the original
    catalog inventory. Conditional PUT protects against a writer racing GET.
    """
    validate_image(data, image)
    previous = ledger.value["assets"].get(key)
    require(not repair or (previous is not None and previous.get("cleanup_state") == "RETAIN"),
            "OWNERSHIP_MISSING", "Repair requires existing retained ownership evidence")
    actual = object_metadata(client, key)
    expected = {k: image[k] for k in ("sha256", "content_type", "bytes")}
    require(actual is None or (previous is not None and actual == expected),
            "OBJECT_CONFLICT", "Existing object is unowned or differs from reviewed bytes")
    checkpoint = ledger.directory / checkpoint_name(key)
    if checkpoint.exists():
        row, checkpoint_data = read_checkpoint(checkpoint)
        require(previous is not None and row["key"] == key and checkpoint_data == data
                and row["content_type"] == image["content_type"],
                "CHECKPOINT_CONFLICT", "Existing checkpoint conflicts with reviewed ownership/content")
    ledger.intend(key, image)
    private_fault('AFTER_ASSET_INTENTION')
    action = "skipped"
    if actual is None:
        try:
            client.put_object(Bucket=BUCKET, Key=key, Body=data,
                              ContentType=image["content_type"], IfNoneMatch="*")
        except (ClientError, BotoCoreError) as error:
            if isinstance(error,ClientError) and error.response.get('Error',{}).get('Code') in {'PreconditionFailed','ConditionalRequestConflict','412','409'}:
                raise BootstrapError('OBJECT_CONFLICT','Conditional upload found a competing object; no adoption or overwrite') from None
            # A lost response may have committed; don't overwrite or assume rollback.
            observed = object_metadata(client, key)
            require(observed == expected, "UPLOAD_UNCONFIRMED",
                    "Upload outcome could not be verified; retain ownership and retry normal", 5)
        action = "repaired" if repair else "uploaded"
    require(object_metadata(client, key) == expected, "OBJECT_READBACK",
            "Uploaded object readback differs from the reviewed content", 5)
    private_fault('AFTER_ASSET_WRITE')
    # SHA-256 readback proves the decoded source bytes/type/dimensions persist.
    if not checkpoint.exists():
        save_checkpoint(ledger.directory, key, image["content_type"], data)
    private_fault('AFTER_CHECKPOINT_WRITE')
    ledger.verified(key)
    return {"object": action, "checkpoint": "verified"}


def begin_reset(client, ledger: OwnershipLedger, *, reset_id: str, artifact_digest: str) -> dict:
    """Persist exact cleanup intent before database deletion; never infer prefix ownership."""
    old = ledger.value.get("reset")
    if old and old.get("state") != "COMPLETED":
        require(old.get("artifact") == artifact_digest,"RESET_INCOMPATIBLE","Interrupted reset belongs to another artifact")
        return old
    checkpoints=inspect_checkpoints(ledger.directory)
    compare_owned_objects(client,ledger)
    keys=[]
    for key,entry in ledger.value["assets"].items():
        if entry["cleanup_state"] == "DELETED":
            continue
        if key in checkpoints:
            require(all(checkpoints[key][k]==entry[k] for k in ("sha256","content_type","bytes")),
                    "CHECKPOINT_CONFLICT","Owned checkpoint differs before reset")
        if entry.get('archived_checkpoint'):
            require(entry['archived_checkpoint']=='.bootstrap/retained/'+entry['checkpoint'],'OWNERSHIP_CONFLICT','Archived allowlist identity differs')
            archived=member_path(ledger.directory,entry['archived_checkpoint'])
            if archived.exists():
                row,data=read_checkpoint(archived)
                require(row['key']==key and row['sha256']==entry['sha256'] and row['content_type']==entry['content_type']
                        and len(data)==entry['bytes'],'CHECKPOINT_CONFLICT','Archived checkpoint differs before reset')
        keys.append(key)
    reset={"id":reset_id,"artifact":artifact_digest,"state":"INTENDED","keys":sorted(keys),
           "database":"backend_demo","database_state":"NOT_RESET"}
    ledger.value["reset"]=reset
    ledger.save()
    return reset


def cleanup_reset_assets(client, ledger: OwnershipLedger) -> dict:
    reset=ledger.value.get("reset",{})
    require(reset.get("state") in {"INTENDED","CLEANING"} and reset.get("database_state")=="RECREATED",
            "RESET_NOT_PREPARED","Database recreation must be recorded before asset cleanup")
    reset["state"]="CLEANING"
    ledger.save()
    deleted=0
    for key in reset["keys"]:
        entry=ledger.value["assets"].get(key)
        require(entry is not None and entry.get("checkpoint")==checkpoint_name(key),
                "OWNERSHIP_CONFLICT","Saved reset allowlist lost its exact ownership evidence")
        actual=object_metadata(client,key)
        require(actual is None or all(actual[k]==entry[k] for k in ("sha256","content_type","bytes")),
                "OBJECT_CONFLICT","Owned object changed during reset; deletion refused")
        paths=[ledger.directory/entry["checkpoint"]]
        if entry.get('archived_checkpoint'):
            require(entry['archived_checkpoint']=='.bootstrap/retained/'+entry['checkpoint'],'OWNERSHIP_CONFLICT','Archived allowlist identity differs')
            paths.append(member_path(ledger.directory,entry['archived_checkpoint']))
        for path in paths:
            if path.exists():
                row,data=read_checkpoint(path)
                require(row["key"]==key and row["sha256"]==entry["sha256"] and row["content_type"]==entry["content_type"]
                        and len(data)==entry["bytes"],"CHECKPOINT_CONFLICT","Checkpoint changed during reset")
        entry["cleanup_state"]="DELETE_INTENDED"
        ledger.save()
        if actual is not None:
            try:
                client.delete_object(Bucket=BUCKET,Key=key)
            except (ClientError,BotoCoreError):
                raise BootstrapError("CLEANUP_UNCONFIRMED","Owned delete outcome is uncertain; retain saved intention",5) from None
        require(object_metadata(client,key) is None,"CLEANUP_UNCONFIRMED","Owned object is still present after deletion",5)
        for path in paths:
            if path.exists():
                path.unlink()
                if os.name=="posix":
                    fd=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY)
                    try: os.fsync(fd)
                    finally: os.close(fd)
        entry["cleanup_state"]="DELETED"
        ledger.save()
        deleted+=1
    # Reset completion and retiring its old attempt must be one durable write.
    # Otherwise prepare_attempt would resume the previous anchor after the DB
    # was recreated. Incomplete cleanup retains that attempt until retry finishes.
    ledger.value.pop("attempt",None)
    reset["state"]="COMPLETED"
    ledger.save()
    return {"deleted_owned_assets":deleted}
