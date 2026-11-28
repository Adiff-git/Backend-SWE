"""Private implementation stages for the two-mode Bash bootstrap entry point.

Errors are deliberately curated: connection strings, environment values and raw SDK
exceptions must never reach console/report output.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import sys
import tempfile
from urllib.parse import urlsplit
from typing import Any

# Private modules must share the same exception type when this file is the entry point.
if __name__ == "__main__":
    sys.modules.setdefault("bootstrap", sys.modules[__name__])

CONTRACT_VERSION = "demo-bootstrap-startup-v1"
ARTIFACT_VERSION = "demo-bootstrap-artifact-v1"
IMPORTER_VERSION = "demo-bootstrap-importer-v1"
PROJECT = "backend-swe-demo"
DATABASE = "backend_demo"
BUCKET = "backend-demo-images"
SOURCE_REVISION = "89e3b6c5bc95b533386a349c65305613617d8709"
SOURCE_MANIFEST_SHA256 = "b07f38b6b13e9d2a517643da4298728b300d739ecad8b8ecce401aa525886b4a"
MAX_JSON_BYTES = 32 * 1024 * 1024


class BootstrapError(Exception):
    def __init__(self, code: str, message: str, exit_code: int = 4):
        super().__init__(message)
        self.code, self.public_message, self.exit_code = code, message, exit_code


def require(condition: bool, code: str, message: str, exit_code: int = 4) -> None:
    if not condition:
        raise BootstrapError(code, message, exit_code)


def private_fault(stage: str) -> None:
    """Internal operational verification only; no additional public CLI mode."""
    if os.environ.get('BOOTSTRAP_FAULT') == stage:
        raise BootstrapError('INJECTED_FAULT','Controlled private verification interruption',5)


def unique_fields(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_FIELD", "Duplicate JSON field")
        result[key] = value
    return result


def invalid_constant(_: str) -> None:
    raise BootstrapError("INVALID_JSON_NUMBER", "Non-finite JSON numbers are forbidden")


def read_json(path: Path, max_bytes: int = MAX_JSON_BYTES) -> Any:
    require(path.is_file() and not path.is_symlink(), "MISSING_INPUT", "Required regular input file is missing")
    require(path.stat().st_size <= max_bytes, "INPUT_TOO_LARGE", "Input exceeds its allowed size")
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_fields,
                          parse_constant=invalid_constant)
    except (UnicodeError, json.JSONDecodeError):
        raise BootstrapError("INVALID_JSON", "Input is not valid UTF-8 JSON") from None


def canonical_bytes(value: Any) -> bytes:
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
                          allow_nan=False).encode("utf-8")
    except (TypeError, ValueError):
        raise BootstrapError("INVALID_CANONICAL_VALUE", "Value cannot be encoded as canonical JSON") from None


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def digest_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def member_path(root: Path, name: str) -> Path:
    require(isinstance(name, str) and bool(name) and "\\" not in name and ":" not in name,
            "UNSAFE_PATH", "Unsafe input path")
    relative = PurePosixPath(name)
    require(not relative.is_absolute() and not PureWindowsPath(name).is_absolute()
            and all(part not in ("", ".", "..") for part in name.split("/"))
            and "\x00" not in name, "UNSAFE_PATH", "Unsafe input path")
    base = root.resolve(strict=True)
    current = base
    for part in relative.parts:
        current = current / part
        require(not current.is_symlink(), "UNSAFE_PATH", "Symlink input paths are forbidden")
    require(current.resolve().is_relative_to(base), "UNSAFE_PATH", "Input escapes its approved root")
    return current


def atomic_json(path: Path, value: Any) -> None:
    require(not path.is_symlink(), "UNSAFE_PATH", "Refusing a symlink output")
    require(not any(p.is_symlink() for p in [path.parent, *path.parent.parents]),
            "UNSAFE_PATH", "Refusing a symlink output directory")
    path.parent.mkdir(parents=True, exist_ok=True)
    require(not any(p.is_symlink() for p in [path.parent, *path.parent.parents]),
            "UNSAFE_PATH", "Refusing a symlink output directory")
    payload = canonical_bytes(value) + b"\n"
    temporary: str | None = None
    try:
        fd, temporary = tempfile.mkstemp(prefix=".bootstrap-", dir=path.parent)
        with os.fdopen(fd, "wb") as stream:
            if os.name == "posix":
                os.fchmod(stream.fileno(), 0o640)
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        temporary = None
        if os.name == "posix":
            fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)
    finally:
        if temporary is not None:
            Path(temporary).unlink(missing_ok=True)


def operational_report(path: Path, payload: dict[str, Any]) -> None:
    allowed = {"result", "mode", "stage", "attempt_id", "started_at", "finished_at", "elapsed_seconds",
               "dataset", "source_revision", "artifact_digest", "importer", "rules", "runtime",
               "anchor", "resource_identity", "counts", "assets", "checks", "initialization",
               "backend_ready", "demo_accounts", "connection_info", "frontend_journeys",
               "error_code", "message", "recovery", "unexecuted_checks"}
    require(isinstance(payload, dict) and set(payload).issubset(allowed), "UNSAFE_REPORT",
            "Report contains fields outside the nonsecret evidence contract", 5)

    def inspect(value: Any) -> None:
        if isinstance(value, dict):
            for key, item in value.items():
                if not isinstance(item, (int, float, bool, type(None))):
                    require(not re.search(r"password|secret|credential|authorization|(^|_)env($|_)|dsn|(^|_)token($|_)", str(key), re.I),
                            "UNSAFE_REPORT", "Report would expose a forbidden configuration/authentication field", 5)
                inspect(item)
        elif isinstance(value, list):
            for item in value:
                inspect(item)
        elif isinstance(value, str):
            require(not re.search(r"postgres(?:ql)?://|\bBearer\s|[?&]X-Amz-|password=", value, re.I),
                    "UNSAFE_REPORT", "Report would expose a credential-bearing value", 5)
    inspect(payload)
    atomic_json(path, {"contract_version": "demo-bootstrap-verification-v1", **payload})


def sql_identifier(value: str) -> str:
    require(bool(re.fullmatch(r"[a-z_][a-z0-9_]{0,62}", value)), "INVALID_IDENTIFIER",
            "Invalid database identifier", 2)
    return value


def load_artifact(root: Path) -> dict[str, Any]:
    manifest = read_json(member_path(root, "manifest.json"))
    require(isinstance(manifest, dict) and manifest.get("contract_version") == ARTIFACT_VERSION,
            "ARTIFACT_VERSION", "Unsupported artifact contract")
    for name in ("dataset_id", "dataset_version"):
        require(isinstance(manifest.get(name), str) and bool(manifest[name].strip()),
                "ARTIFACT_IDENTITY", "Artifact identity is missing")
    expected = manifest.get("artifact_digest")
    payload = {k: v for k, v in manifest.items() if k != "artifact_digest"}
    require(expected == digest_bytes(canonical_bytes(payload)), "ARTIFACT_CHECKSUM",
            "Artifact manifest digest is invalid")
    require(manifest.get("source", {}).get("revision") == SOURCE_REVISION,
            "SOURCE_REVISION_MISMATCH", "Artifact source revision is incompatible")
    files = manifest.get("files")
    require(isinstance(files, dict) and bool(files), "ARTIFACT_FILES", "Artifact file inventory is missing")
    required_files={"catalog/products.jsonl","catalog/variants.jsonl","catalog/categories.json","catalog/brands.json",
                    "assets/images.json","provenance/fields.jsonl","provenance/source-links.jsonl",
                    "scenarios/recipe.json","scenarios/orders.jsonl","scenarios/promotions.json","scenarios/demo-accounts.json",
                    "rules/options.json","rules/pricing.json","rules/distributions.json","review/decisions.jsonl","review/summary.json","usage-scope.json"}
    require(required_files.issubset(files),"ARTIFACT_FILES","Artifact omits required consumed payloads")
    for name, metadata in files.items():
        require(name != "manifest.json" and isinstance(metadata, dict), "ARTIFACT_FILES",
                "Artifact file inventory is invalid")
        path = member_path(root, name)
        require(path.is_file() and path.stat().st_size == metadata.get("bytes")
                and digest_file(path) == metadata.get("sha256"), "ARTIFACT_CHECKSUM",
                "Artifact payload bytes differ from the approved manifest")
    usage = manifest.get("usage_scope", {})
    require(usage.get("usage_scope") == "PRIVATE_NONCOMMERCIAL_UNIVERSITY_COURSE"
            and usage.get("access_audience") == ["TEAM_MEMBERS", "INSTRUCTOR"]
            and usage.get("public_redistribution_approved") is False
            and usage.get("commercial_use_allowed") is False
            and usage.get("scope_decision_status") == "COMPLETE"
            and usage.get("public_redistribution_verification") == "NOT_APPLICABLE_CURRENT_SCOPE"
            and usage.get("copyright_clearance_status") == "UNVERIFIED"
            and usage.get("permission_evidence_status") == "UNVERIFIED"
            and usage.get("future_permission_verification_required") is True,
            "USAGE_SCOPE", "Artifact does not preserve the approved restricted educational scope")
    review = manifest.get("review", {})
    require(review.get("unresolved_error_count") == 0 and review.get("approved") is True,
            "ARTIFACT_REVIEW", "Artifact is not approved or has unresolved review errors")
    release=read_json(member_path(root,'release-approval.json'))
    require(release.get('artifact_digest')==expected and release.get('decision')=='APPROVE'
            and release.get('reviewer_kind')=='HUMAN' and isinstance(release.get('reviewer'),str)
            and bool(release['reviewer'].strip()) and isinstance(release.get('reference'),str) and bool(release['reference'].strip()),
            'ARTIFACT_REVIEW','Final artifact needs genuine detached full-digest approval')
    return manifest


def timeout_seconds(value: str, default: int) -> int:
    try:
        result = int(value) if value else default
    except ValueError:
        raise BootstrapError("INVALID_TIMEOUT", "Timeout must be a positive bounded integer", 2) from None
    require(0 < result <= 3600, "INVALID_TIMEOUT", "Timeout must be between 1 and 3600 seconds", 2)
    return result


def validate_environment(env: dict[str, str], *, need_password: bool = False) -> dict[str, Any]:
    require(env.get("COMPOSE_PROJECT_NAME") == PROJECT and env.get("LOCAL_DB_NAME") == DATABASE
            and env.get("LOCAL_STORAGE_BUCKET") == BUCKET,
            "TARGET_MISMATCH", "Only the dedicated local demo project/database/bucket is permitted", 2)
    require(env.get("LOCAL_DB_HOST", "postgres") == "postgres", "EXTERNAL_DATABASE",
            "Database host must be the owned Compose postgres service", 2)
    require(env.get("LOCAL_STORAGE_ENDPOINT", "http://localstack:4567") == "http://localstack:4567",
            "EXTERNAL_STORAGE", "Storage endpoint must be the owned signed local gateway", 2)
    try:
        signing=urlsplit(env.get('LOCAL_STORAGE_SIGNING_ENDPOINT','http://127.0.0.1:14567'))
        valid_signing=(signing.scheme=='http' and signing.hostname in {'127.0.0.1','localhost'}
                       and signing.username is None and signing.password is None and not signing.query and not signing.fragment
                       and signing.path in {'','/'} and signing.port is not None and 1<=signing.port<=65535)
    except ValueError:
        valid_signing=False
    require(valid_signing,'EXTERNAL_STORAGE','Storage signing URLs must use the configured loopback gateway',2)
    require(not any(env.get(k) for k in ("AWS_PROFILE", "AWS_DEFAULT_PROFILE", "AWS_ENDPOINT_URL",
                                       "AWS_ENDPOINT_URL_S3", "DB_URL", "DATABASE_URL")),
            "UNSAFE_OVERRIDE", "Ambient cloud/database overrides are not permitted", 2)
    user = sql_identifier(env.get("LOCAL_DB_USER", ""))
    require(bool(env.get("LOCAL_DB_PASSWORD")) and bool(env.get("JWT_SECRET")),
            "MISSING_SECRET", "Required private database/JWT configuration is absent", 2)
    require(len(env["JWT_SECRET"].encode()) >= 32, "INVALID_SECRET", "JWT configuration is too short", 2)
    if need_password:
        password = env.get("DEMO_BOOTSTRAP_PASSWORD", "").encode()
        require(0 < len(password) <= 72 and not password.startswith(b"GENERATE_"),
                "INVALID_DEMO_PASSWORD", "A private demo password of 1–72 UTF-8 bytes is required", 2)
    return {"project": PROJECT, "database": DATABASE, "bucket": BUCKET, "database_user": user,
            "timeouts": {"infrastructure": timeout_seconds(env.get("BOOTSTRAP_INFRA_TIMEOUT", ""), 180),
                         "backend": timeout_seconds(env.get("BOOTSTRAP_BACKEND_TIMEOUT", ""), 600),
                         "storage": timeout_seconds(env.get("BOOTSTRAP_STORAGE_TIMEOUT", ""), 60)}}


def validate_compose(config: dict[str, Any]) -> dict[str, Any]:
    require(isinstance(config, dict) and config.get("name") == PROJECT,
            "TARGET_MISMATCH", "Compose project must be the dedicated demo project", 2)
    services = config.get("services", {})
    require({"postgres", "localstack", "image-storage", "mailpit", "backend"}.issubset(services),
            "COMPOSE_INCOMPATIBLE", "Required existing Compose services are missing", 2)
    env = services["backend"].get("environment", {})
    require(isinstance(env, dict), "COMPOSE_INCOMPATIBLE", "Expected resolved Compose environment", 2)
    env = {k: str(v) if v is not None else "" for k, v in env.items()}
    if env.get("COMPOSE_PROJECT_NAME"):
        require(env["COMPOSE_PROJECT_NAME"] == PROJECT, "TARGET_MISMATCH",
                "Environment project does not match the dedicated project", 2)
    env["COMPOSE_PROJECT_NAME"] = PROJECT
    result = validate_environment(env)
    for service in services.values():
        for port in service.get("ports", []):
            require(port.get("host_ip") == "127.0.0.1", "EXTERNAL_BIND",
                    "Published service ports must remain loopback-only", 2)
    for volume in ("postgres-data", "product-checkpoints"):
        info = config.get("volumes", {}).get(volume, {})
        require(info.get("name") == PROJECT + "_" + volume and not info.get("external"),
                "VOLUME_OWNERSHIP", "Stateful volumes must belong to the dedicated Compose project", 2)
    for network in ('default','storage-private'):
        info=config.get('networks',{}).get(network,{})
        require(info.get('name')==PROJECT+'_'+network and not info.get('external')
                and info.get('driver','bridge')=='bridge','NETWORK_OWNERSHIP',
                'Helper networks must be the dedicated local project bridges',2)
    require(services["localstack"].get("environment", {}).get("S3_SKIP_SIGNATURE_VALIDATION") == "0",
            "STORAGE_SIGNATURES", "Local storage signature validation must remain enabled", 2)
    gateway_ports=[p for p in services['localstack'].get('ports',[]) if p.get('target')==4567]
    signing=urlsplit(env.get('LOCAL_STORAGE_SIGNING_ENDPOINT','http://127.0.0.1:14567'))
    require(len(gateway_ports)==1 and str(gateway_ports[0].get('published'))==str(signing.port),
            'STORAGE_SIGNING_MISMATCH','Signing URL port must match the loopback gateway publication',2)
    return result


def dispatch_normal(connection, artifact: dict[str, Any], importer_digest: str, ledger) -> dict[str, Any]:
    """Choose preparation or immutable completed skip; never import implicitly.

    Infrastructure ownership and the exclusive daemon lock must be verified by
    the caller. Import/asset repair/readiness are subsequent explicit stages.
    """
    import database
    require(bool(re.fullmatch(r"[0-9a-f]{64}", importer_digest)), "IMPORTER_IDENTITY",
            "Importer digest must be an exact SHA-256")
    result = database.prepare_attempt(connection, artifact, importer_digest, ledger)
    if result["state"] == "COMPLETED":
        marker = result["marker"]
        return {"action": "skipped", "state": "COMPLETED", "initialized": True,
                "attempt_id": str(marker["attempt_id"]),
                "anchor": marker["anchor_at"].isoformat(timespec="microseconds")}
    return {"action": "prepare", "initialized": False, **result}


def run_private_stage() -> int:
    parser = argparse.ArgumentParser(description="Private bootstrap helper (use scripts/start-dev.sh)")
    parser.add_argument("stage", choices=["validate-config", "validate-compose", "host-artifact", "verify-source", "snapshot-source"])
    parser.add_argument("--source", type=Path)
    parser.add_argument("--canonical", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--format", choices=["json", "timeouts"], default="json")
    args = parser.parse_args()
    if args.stage in {"validate-compose","host-artifact"}:
        try:
            config = json.load(sys.stdin, object_pairs_hook=unique_fields, parse_constant=invalid_constant)
        except (UnicodeError, json.JSONDecodeError):
            raise BootstrapError("INVALID_COMPOSE_JSON", "Effective Compose configuration is invalid", 2) from None
        result = validate_compose(config)
        if args.stage == 'host-artifact':
            mounts=config.get('services',{}).get('bootstrap',{}).get('volumes',[])
            sources=[v.get('source') for v in mounts if v.get('target')=='/artifact' and v.get('read_only') is True and v.get('type')=='bind']
            require(len(sources)==1 and isinstance(sources[0],str) and not any(c in sources[0] for c in '\r\n\t'),
                    'UNSAFE_PATH','Resolved artifact mount is invalid',2)
            print(sources[0]);return 0
    elif args.stage == "validate-config":
        result = validate_environment(dict(os.environ))
    else:
        import catalog
        if args.stage == "snapshot-source":
            require(args.output is not None, "MISSING_OUTPUT", "Snapshot output is required", 2)
            result = catalog.snapshot_archive(sys.stdin.buffer, args.output)
        else:
            require(args.source is not None and args.canonical is not None, "MISSING_INPUT",
                    "Working source and exact Git snapshot roots are required", 2)
            result = catalog.verify_source(args.source, args.canonical)
    if args.format == "timeouts":
        require(args.stage in {"validate-config", "validate-compose"}, "INVALID_FORMAT",
                "Timeout format is only valid for configuration inspection", 2)
        print("\t".join(str(result["timeouts"][k]) for k in ("infrastructure", "backend", "storage")))
    else:
        print(canonical_bytes({"result": "PASS", **result}).decode())
    return 0


if __name__ == "__main__":
    try:
        sys.exit(run_private_stage())
    except BootstrapError as error:
        print(canonical_bytes({"result": "FAIL", "code": error.code,
                               "message": error.public_message}).decode(), file=sys.stderr)
        sys.exit(error.exit_code)
    except Exception:
        print('{"result":"FAIL","code":"INTERNAL_ERROR","message":"Private helper failed; inspect sanitized operational evidence"}', file=sys.stderr)
        sys.exit(5)
