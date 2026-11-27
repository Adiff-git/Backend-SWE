"""Explicit database capability, identity and initialization-state inspection."""
from __future__ import annotations

import os
import time
import re
from pathlib import Path
from typing import Any
from datetime import datetime, timezone
from uuid import UUID, uuid4

import psycopg
from psycopg import sql
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from bootstrap import (BUCKET, DATABASE, PROJECT, IMPORTER_VERSION, SOURCE_REVISION, BootstrapError,
                       digest_file, require, sql_identifier,private_fault)

SQL_ROOT = Path(__file__).parent / "sql"
METADATA_COLUMNS = {
    "id", "metadata_version", "attempt_id", "dataset_id", "dataset_version", "artifact_sha256",
    "source_revision", "importer_version", "importer_digest", "migration_fingerprint", "anchor_at",
    "state", "started_at", "completed_at", "resource_identity", "verification_summary", "error_code",
}
BASELINE_TABLES = {"roles", "permissions", "role_permissions"}


def connect(*, maintenance: bool = False) -> psycopg.Connection:
    """Explicit kwargs avoid ambient DSN/service defaults and credential-bearing errors."""
    require(not any(k.startswith('PG') and v for k,v in os.environ.items()),
            'UNSAFE_OVERRIDE','Ambient PostgreSQL discovery/session overrides are forbidden',2)
    try:
        return psycopg.connect(host="postgres", port=5432, dbname="postgres" if maintenance else DATABASE,
                               user=sql_identifier(os.environ.get("LOCAL_DB_USER", "")),
                               password=os.environ.get("LOCAL_DB_PASSWORD", ""),
                               connect_timeout=10, autocommit=True, row_factory=dict_row,sslmode='disable',
                               options='-c search_path=public,pg_catalog -c timezone=UTC',application_name='demo-bootstrap')
    except psycopg.Error:
        raise BootstrapError("DATABASE_UNAVAILABLE", "Owned local database connection failed", 5) from None


def capability_probe(connection: psycopg.Connection, *, migrated: bool = False,
                     maintenance: bool = False) -> dict[str, Any]:
    row = connection.execute((SQL_ROOT / "preflight.sql").read_text(encoding="utf-8")).fetchone()
    facts = row["capabilities"]
    protection=connection.execute("SELECT current_setting('session_replication_role')='origin' AS origin, "
        "NOT EXISTS(SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
        "WHERE n.nspname='public' AND t.tgenabled NOT IN ('O','A')) AS enabled").fetchone()
    require(protection['origin'] and protection['enabled'],'DATABASE_PROTECTION','Existing trigger protections are disabled or incompatible')
    require(facts["database"] == ("postgres" if maintenance else DATABASE), "TARGET_MISMATCH",
            "Capability probe reached an unexpected database")
    require(facts["encoding"] == "UTF8" and facts["icu_compatible"], "ICU_INCOMPATIBLE",
            "Current PostgreSQL image does not satisfy existing UTF8/ICU migration prerequisites")
    require(facts["pg_trgm_available"], "PG_TRGM_UNAVAILABLE", "Required pg_trgm 1.6 package is unavailable")
    installed = facts["pg_trgm_installed"]
    if installed is not None:
        require(installed == {"version": "1.6", "schema": "public"}, "PG_TRGM_INCOMPATIBLE",
                "Installed pg_trgm version/schema is incompatible")
    else:
        require(not migrated and facts["can_create"], "PG_TRGM_NOT_INSTALLED",
                "Required extension cannot be installed or is absent after migration")
    return facts


def relation_exists(connection: psycopg.Connection, name: str) -> bool:
    return connection.execute("SELECT TO_REGCLASS(%s) AS relation", ("public." + sql_identifier(name),)).fetchone()["relation"] is not None


def schema_fingerprint(connection) -> str:
    from bootstrap import canonical_bytes,digest_bytes
    queries=[
        ("constraints","SELECT cl.relname||'.'||c.conname identity,pg_get_constraintdef(c.oid,false)||':'||c.convalidated::text definition FROM pg_constraint c JOIN pg_class cl ON cl.oid=c.conrelid JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE n.nspname='public' AND cl.relname<>'bootstrap_import_runs'"),
        ("triggers","SELECT cl.relname||'.'||t.tgname identity,pg_get_triggerdef(t.oid,false)||':'||t.tgenabled::text definition FROM pg_trigger t JOIN pg_class cl ON cl.oid=t.tgrelid JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE n.nspname='public' AND cl.relname<>'bootstrap_import_runs' AND NOT t.tgisinternal"),
        ("functions","SELECT p.proname||'('||pg_get_function_identity_arguments(p.oid)||')' identity,pg_get_functiondef(p.oid) definition FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind='f'"),
        ("indexes","SELECT tablename||'.'||indexname identity,indexdef definition FROM pg_indexes WHERE schemaname='public' AND tablename<>'bootstrap_import_runs'"),
        ("columns","SELECT cl.relname||'.'||a.attname identity,format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull::text||':'||a.attidentity::text||':'||a.attgenerated::text||':'||COALESCE(pg_get_expr(d.adbin,d.adrelid),'') definition FROM pg_attribute a JOIN pg_class cl ON cl.oid=a.attrelid JOIN pg_namespace n ON n.oid=cl.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname='public' AND cl.relkind IN ('r','p') AND cl.relname NOT IN ('bootstrap_import_runs','flyway_schema_history') AND a.attnum>0 AND NOT a.attisdropped")]
    records=[]
    for section,query in queries:
        records.extend({'section':section,**dict(row)} for row in connection.execute(query).fetchall())
    return digest_bytes(canonical_bytes(sorted(records,key=lambda r:(r['section'],r['identity']))))


def migration_history(connection: psycopg.Connection) -> list[dict[str, Any]]:
    if not relation_exists(connection, "flyway_schema_history"):
        return []
    rows = connection.execute(
        "SELECT installed_rank, version, description, type, script, checksum, success "
        "FROM flyway_schema_history ORDER BY installed_rank").fetchall()
    require(all(r["success"] for r in rows), "MIGRATION_FAILED", "Flyway contains failed migration history")
    return [dict(row) for row in rows]


def compare_history(actual: list[dict[str, Any]], expected: list[dict[str, Any]], *, allow_prefix: bool = False) -> None:
    fields = ("version", "type", "script", "checksum", "success")
    normalized = lambda rows: [{k: row[k] for k in fields} for row in rows]
    a, b = normalized(actual), normalized(expected)
    require((b[:len(a)] == a and len(a) <= len(b)) if allow_prefix else a == b,
            "MIGRATION_INCOMPATIBLE", "Migration history does not match the importer compatibility fingerprint")


def metadata(connection: psycopg.Connection) -> dict[str, Any] | None:
    if not relation_exists(connection, "bootstrap_import_runs"):
        return None
    columns = {r["column_name"] for r in connection.execute(
        "SELECT column_name FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = 'bootstrap_import_runs'").fetchall()}
    require(columns == METADATA_COLUMNS, "METADATA_INCOMPATIBLE", "Existing operational metadata layout is incompatible")
    expected_types={name:'text' for name in METADATA_COLUMNS}
    expected_types.update(id='int8',metadata_version='int4',attempt_id='uuid',migration_fingerprint='jsonb',
                          anchor_at='timestamptz',started_at='timestamptz',completed_at='timestamptz',
                          resource_identity='jsonb',verification_summary='jsonb')
    types=connection.execute("SELECT column_name,udt_name,is_nullable FROM information_schema.columns "
                             "WHERE table_schema='public' AND table_name='bootstrap_import_runs'").fetchall()
    require(all(r['udt_name']==expected_types[r['column_name']] and
                r['is_nullable']==('YES' if r['column_name'] in {'completed_at','error_code'} else 'NO') for r in types),
            'METADATA_INCOMPATIBLE','Existing operational metadata types/nullability are incompatible')
    rows = connection.execute("SELECT * FROM bootstrap_import_runs").fetchall()
    require(len(rows) <= 1 and all(r["id"] == 1 and r["metadata_version"] == 1 for r in rows),
            "METADATA_INCOMPATIBLE", "Operational metadata identity/version is incompatible")
    return dict(rows[0]) if rows else None


def business_population(connection: psycopg.Connection) -> dict[str, int]:
    tables = connection.execute("SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename").fetchall()
    counts: dict[str, int] = {}
    for row in tables:
        name = row["tablename"]
        if name in BASELINE_TABLES | {"bootstrap_import_runs", "flyway_schema_history"}:
            continue
        count = connection.execute(sql.SQL("SELECT COUNT(*) AS n FROM public.{}").format(sql.Identifier(name))).fetchone()["n"]
        if count:
            counts[name] = count
    return counts


def validate_baseline(connection: psycopg.Connection, expected: dict[str, list[Any]], *, allow_subset: bool = True) -> None:
    queries = {
        "roles": "SELECT name FROM roles ORDER BY name",
        "permissions": "SELECT name FROM permissions ORDER BY name",
        "role_permissions": "SELECT r.name AS role, p.name AS permission FROM role_permissions rp "
                            "JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id "
                            "ORDER BY r.name, p.name",
    }
    for table, query in queries.items():
        if not relation_exists(connection, table):
            continue
        actual = [dict(row) for row in connection.execute(query).fetchall()]
        permitted = expected.get(table, [])
        require(isinstance(permitted, list) and all(row in permitted for row in actual)
                and (allow_subset or len(actual)==len(permitted)),
                "FOREIGN_BASELINE", "Roles/permissions contain rows outside the compatible migration baseline")


def classify(connection: psycopg.Connection, artifact: dict[str, Any], importer_digest: str) -> dict[str, Any]:
    marker = metadata(connection)
    if marker is not None:
        require(marker["artifact_sha256"] == artifact["artifact_digest"]
                and marker["dataset_id"] == artifact["dataset_id"]
                and marker["dataset_version"] == artifact["dataset_version"]
                and marker["source_revision"] == SOURCE_REVISION
                and marker["importer_version"] == IMPORTER_VERSION
                and marker["importer_digest"] == importer_digest
                and marker["migration_fingerprint"] == artifact.get("importer_compatibility", {}).get("migration_fingerprint")
                and all(marker["resource_identity"].get(k) == v for k, v in
                        {"project": PROJECT, "database": DATABASE, "bucket": BUCKET}.items()),
                "DATASET_INCOMPATIBLE", "Existing dataset/importer identity differs; normal startup never reseeds it")
        require(marker["state"] in {"PREPARING", "IMPORTING", "COMPLETED", "FAILED"},
                "METADATA_INCOMPATIBLE", "Unknown initialization state")
        if os.environ.get('BOOTSTRAP_DAEMON_ID'):
            require(marker['resource_identity'].get('daemon_id')==os.environ['BOOTSTRAP_DAEMON_ID'],
                    'DATASET_INCOMPATIBLE','Completion metadata belongs to a different or unverified daemon')
        if marker["state"] == "COMPLETED":
            require(marker["completed_at"] is not None and bool(marker["verification_summary"]),
                    "METADATA_INCOMPLETE", "Completion marker lacks required verification evidence")
            validate_completion_proof(marker['verification_summary'],artifact)
            return {"state": "COMPLETED", "marker": marker}
    compatibility=artifact.get('importer_compatibility',{})
    expected_history=compatibility.get('migration_history',[])
    full=bool(expected_history) and len(migration_history(connection))==len(expected_history)
    validate_baseline(connection,compatibility.get('baseline_records',{}),allow_subset=not full)
    counts = business_population(connection)
    require(not counts, "FOREIGN_DATA", "Nonempty application data has no trustworthy completed import; no automatic adoption or reset")
    return {"state": "INTERRUPTED" if marker else "UNSEEDED", "marker": marker}


def validate_completion_proof(proof: dict, artifact: dict) -> None:
    """Validate saved initial evidence, never re-enforce live row counts after activity."""
    scope=artifact.get('scope',{})
    counts=proof.get('counts',{})
    verification=proof.get('verification',{})
    mapping={'products':'products','product_variants':'variants','product_reviews':'review_total',
             'orders':'total_orders','order_items':'total_items'}
    require(proof.get('contract_version')=='demo-bootstrap-initial-proof-v1'
            and proof.get('artifact_digest')==artifact['artifact_digest']
            and bool(re.fullmatch(r'[0-9a-f]{64}',str(proof.get('ownership_ledger_sha256',''))))
            and all(key in scope and counts.get(table)==scope[key] for table,key in mapping.items())
            and verification.get('review_count_groups')==[300,300,525,300,75]
            and verification.get('average_rating_groups')==[60,120,300,420,300]
            and verification.get('status_counts')==scope.get('status_counts')
            and verification.get('sale_groups')==[900,300,150,75,75]
            and all(value==0 for key,value in verification.items() if key.endswith('_errors')),
            'METADATA_INCOMPLETE','Completion marker lacks a compatible full initial verification proof')


def ensure_metadata_table(connection: psycopg.Connection) -> None:
    if relation_exists(connection, "bootstrap_import_runs"):
        metadata(connection)
    else:
        connection.execute((SQL_ROOT / "bootstrap_metadata.sql").read_text(encoding="utf-8"))


def prepare_attempt(connection: psycopg.Connection, artifact: dict[str, Any],
                    importer_digest: str, ledger) -> dict[str, Any]:
    """Persist one anchor before external effects; caller must hold the daemon lock.

    The ledger is written first so a lost DB response cannot silently choose a new
    anchor. An interrupted business import must still have no business rows.
    """
    state = classify(connection, artifact, importer_digest)
    history = migration_history(connection)
    compatibility = artifact.get("importer_compatibility", {})
    expected = compatibility.get("migration_history")
    require(isinstance(expected, list) and bool(expected), "MIGRATION_INCOMPATIBLE",
            "Artifact must declare the complete compatible migration history")
    compare_history(history, expected, allow_prefix=state["state"] != "COMPLETED")
    if state["state"] == "COMPLETED":
        return state
    marker = state["marker"]
    old = ledger.value.get("attempt")
    if marker:
        attempt = str(marker["attempt_id"])
        anchor = marker["anchor_at"].astimezone(timezone.utc)
    elif old:
        require(old.get("artifact") == artifact["artifact_digest"], "OWNERSHIP_CONFLICT",
                "Interrupted asset preparation belongs to a different artifact")
        try:
            attempt = str(UUID(old["id"]))
            anchor = datetime.fromisoformat(old["anchor"])
            require(anchor.tzinfo is not None, "OWNERSHIP_CONFLICT", "Saved anchor must include UTC offset")
            anchor = anchor.astimezone(timezone.utc)
        except (ValueError, TypeError, KeyError):
            raise BootstrapError("OWNERSHIP_CONFLICT", "Saved attempt identity/anchor is invalid") from None
    else:
        attempt, anchor = str(uuid4()), datetime.now(timezone.utc)
    anchor_text = anchor.isoformat(timespec="microseconds")
    ledger.begin_attempt(attempt, artifact["artifact_digest"], anchor_text)
    # Flush the intention before creating/updating even the operational marker.
    with connection.transaction():
        ensure_metadata_table(connection)
        current = metadata(connection)
        require((current is None and marker is None) or
                (current is not None and marker is not None and current == marker),
                "ATTEMPT_CHANGED", "Initialization metadata changed during preparation")
        if current is None:
            connection.execute(
                "INSERT INTO bootstrap_import_runs "
                "(id, metadata_version, attempt_id, dataset_id, dataset_version, artifact_sha256, "
                "source_revision, importer_version, importer_digest, migration_fingerprint, "
                "anchor_at, state, started_at, resource_identity) "
                "VALUES (1, 1, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'PREPARING', %s, %s)",
                (attempt, artifact["dataset_id"], artifact["dataset_version"], artifact["artifact_digest"],
                 SOURCE_REVISION, IMPORTER_VERSION, importer_digest,
                 Jsonb(compatibility["migration_fingerprint"]), anchor, datetime.now(timezone.utc),
                 Jsonb({"project": PROJECT, "database": DATABASE, "bucket": BUCKET,
                        "daemon_id":os.environ.get('BOOTSTRAP_DAEMON_ID','')})))
        else:
            connection.execute("UPDATE bootstrap_import_runs SET state='PREPARING', error_code=NULL WHERE id=1")
    return {"state": "PREPARING", "attempt_id": attempt, "anchor": anchor_text,
            "resumed": marker is not None or old is not None}


def atomic_import(connection, root: Path, artifact: dict, attempt: dict, private_password: str, *, ownership_digest: str) -> dict:
    """One constrained business commit; assets are external and already verified."""
    from scenarios import resolved_graph
    from collections import Counter
    capability_probe(connection,migrated=True)
    allowed = {"categories","brands","products","product_variants","product_images","catalog_source_links",
               "users","addresses","vouchers","voucher_customer_usages","voucher_entitlements","orders","order_items",
               "voucher_redemptions","stock","coverage","original_variants","carts","cart_items","product_reviews","wishlist_items","user_behavior_events"}
    require(connection.autocommit, "TRANSACTION_CONFIGURATION", "Import requires an explicit outer transaction")
    marker = metadata(connection)
    require(marker is not None and str(marker["attempt_id"]) == attempt["attempt_id"] and marker["state"] != "COMPLETED",
            "ATTEMPT_CHANGED", "Import attempt no longer matches preparation")
    require(not business_population(connection), "FOREIGN_DATA", "Business data appeared before import")
    connection.execute("UPDATE bootstrap_import_runs SET state='IMPORTING',error_code=NULL WHERE id=1")
    expected = Counter()
    started=time.monotonic()
    timings={}
    try:
        with connection.transaction():
            connection.execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED")
            # Explicit bounded policy; measured full-profile evidence must report actual duration.
            connection.execute("SET LOCAL statement_timeout='1800s'")
            connection.execute("CREATE TEMP TABLE bootstrap_rows (kind text NOT NULL,body jsonb NOT NULL) ON COMMIT DROP")
            with connection.cursor().copy("COPY bootstrap_rows(kind,body) FROM STDIN") as transport:
                for kind, row in resolved_graph(root, datetime.fromisoformat(attempt["anchor"]), private_password):
                    require(kind in allowed,"INVALID_GRAPH","Resolved graph contains an unauthorized entity")
                    transport.write_row((kind,Jsonb(row)))
                    expected[kind]+=1
            connection.execute("CREATE INDEX ON bootstrap_rows(kind)")
            connection.execute("CREATE INDEX ON bootstrap_rows(kind,((body->>'id')::bigint)) WHERE kind IN ('orders','order_items')")
            timings['resolve_and_transport']=round(time.monotonic()-started,6)
            for name in ("catalog.sql","people.sql","vouchers.sql","orders.sql","inventory_vouchers.sql","engagement.sql"):
                phase_started=time.monotonic()
                connection.execute((SQL_ROOT/name).read_text(encoding="utf-8"))
                timings[name]=round(time.monotonic()-phase_started,6)
                private_fault('AFTER_'+name.split('.')[0].upper())
            for table,count in expected.items():
                if table in {"stock","coverage","original_variants"}:
                    continue
                actual=connection.execute(sql.SQL("SELECT COUNT(*) AS n FROM {}").format(sql.Identifier(table))).fetchone()["n"]
                require(actual==count,"IMPORT_COUNT","Persisted entity count differs from the resolved graph",5)
            require(expected["products"]==1500 and expected["users"]==500 and expected["addresses"]==512
                    and expected["product_images"]==1500 and expected["carts"]==300
                    and expected["wishlist_items"]==3500 and expected["user_behavior_events"]==360,
                    "IMPORT_COUNT","Resolved graph violates approved domain counts",5)
            actual_business=business_population(connection)
            allowed_counts={k:v for k,v in expected.items() if k not in {'stock','coverage','original_variants'} and v}
            allowed_counts.update(inventory_balances=expected['product_variants'],inventory_reservations=expected['order_items'],user_roles=500)
            require(actual_business==allowed_counts,'FORBIDDEN_GRAPH','Business rows include unexpected workflow artifacts or derived counts',5)
            verification=connection.execute((SQL_ROOT/"verify.sql").read_text(encoding="utf-8")).fetchone()["verification"]
            recipe=__import__('bootstrap').read_json(root/"scenarios/recipe.json")
            require(verification["review_count_groups"]==[300,300,525,300,75]
                    and verification["average_rating_groups"]==[60,120,300,420,300]
                    and verification["review_total"]==recipe["review_total"]
                    and verification["status_counts"]==recipe["status_counts"]
                    and verification['sale_groups']==[900,300,150,75,75]
                    and verification['stock_groups']==[round(expected['product_variants']*.90),round(expected['product_variants']*.08),
                                                        expected['product_variants']-round(expected['product_variants']*.90)-round(expected['product_variants']*.08)]
                    and all(v==0 for k,v in verification.items() if k.endswith("_errors")),
                    "IMPORT_INVARIANT","Persisted data violates exact approved distributions or business invariants",5)
            # Every explicit-ID sequence and trigger-generated sequence must exceed MAX(id).
            for record in connection.execute("SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='id' AND is_identity='YES'").fetchall():
                table=record["table_name"]
                sequence=connection.execute("SELECT pg_get_serial_sequence(%s,'id') AS name",(table,)).fetchone()["name"]
                maximum=connection.execute(sql.SQL("SELECT MAX(id) AS n FROM {}").format(sql.Identifier(table))).fetchone()["n"]
                if maximum is not None:
                    connection.execute("SELECT setval(%s,%s,true)",(sequence,maximum))
            private_fault('BEFORE_CONSTRAINTS')
            constraint_started=time.monotonic()
            connection.execute("SET CONSTRAINTS ALL IMMEDIATE")
            timings['deferred_constraints']=round(time.monotonic()-constraint_started,6)
            timings['before_commit']=round(time.monotonic()-started,6)
            summary={"contract_version":"demo-bootstrap-initial-proof-v1","counts":dict(expected),"verification":verification,"artifact_digest":artifact["artifact_digest"],
                     'timings_seconds':timings,'statement_timeout_seconds':1800,'ownership_ledger_sha256':ownership_digest}
            validate_completion_proof(summary,artifact)
            connection.execute("UPDATE bootstrap_import_runs SET state='COMPLETED',completed_at=clock_timestamp(),verification_summary=%s,error_code=NULL WHERE id=1 AND attempt_id=%s",
                               (Jsonb(summary),attempt["attempt_id"]))
        private_fault('AFTER_COMMIT')
        return {"initialized":True,"action":"imported","summary":summary,'elapsed_seconds':round(time.monotonic()-started,6)}
    except Exception as error:
        failure_code=error.code if isinstance(error,BootstrapError) else 'IMPORT_FAILED'
        # Close the uncertain session before authoritative reread; no live business
        # transaction can continue after the worker terminates this connection.
        connection.close()
        try:
            with connect() as authoritative:
                saved=metadata(authoritative)
                require(saved is not None and str(saved["attempt_id"])==attempt["attempt_id"],
                        "COMMIT_UNKNOWN","Commit identity cannot be established; do not retry blindly",5)
                if saved["state"]=="COMPLETED":
                    validate_completion_proof(saved['verification_summary'],artifact)
                    return {"initialized":True,"action":"imported","commit_acknowledgement":"RECOVERED",
                            "summary":saved["verification_summary"]}
                require(not business_population(authoritative),"COMMIT_UNKNOWN","Business outcome is uncertain; stop and inspect",5)
                authoritative.execute("UPDATE bootstrap_import_runs SET state='FAILED',error_code=%s WHERE id=1",(failure_code,))
        except BootstrapError:
            raise
        except Exception:
            raise BootstrapError("COMMIT_UNKNOWN","Cannot establish committed outcome; retain lock evidence and inspect before retry",5) from None
        raise BootstrapError("IMPORT_FAILED","Business import rolled back; tracked assets and saved anchor are retained for normal retry",5) from None


def reset_database(connection, *, target_owner: str) -> None:
    """Maintenance connection only; orchestrator has stopped approved clients."""
    owner=sql_identifier(target_owner)
    capability_probe(connection,maintenance=True)
    record=connection.execute("SELECT pg_get_userbyid(datdba) owner FROM pg_database WHERE datname=%s",(DATABASE,)).fetchone()
    require(record is None or record["owner"]==owner,"DATABASE_OWNERSHIP","Reset target owner differs from approved local role")
    sessions=connection.execute("SELECT pid FROM pg_stat_activity WHERE datname=%s",(DATABASE,)).fetchall()
    require(not sessions,"DATABASE_IN_USE","Approved database still has clients; stop backend/migrator before reset")
    if record:
        connection.execute(sql.SQL("DROP DATABASE {}").format(sql.Identifier(DATABASE)))
    connection.execute(sql.SQL("CREATE DATABASE {} OWNER {} ENCODING 'UTF8' TEMPLATE template0").format(sql.Identifier(DATABASE),sql.Identifier(owner)))
