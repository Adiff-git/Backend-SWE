# Seed v1 distribution review and delivery plan

The existing archive remains immutable at `tools/catalog-import/output/catalog-dev-v1.tar.gz`. It contains only `catalog-dev-v1/catalog.json`, `manifest.json` and `SHA256SUMS`, all regular files. Extracted data validates as 1,000 Products, 1,000 variants, 31 categories, five PUBLISHED and 995 DRAFT. There are no Users, Addresses, authentication tables, Media records, images, credentials or S3 keys. Field allowlists and checksum validation supplement a scan for private keys, embedded DSN credentials and AWS access keys. No crawler or SQLite is required by the loader.

Archive SHA-256:

```text
055416889480fe8b88ad46806c47c24d72c89fdbdccffca80f1c6639087a3d32
```

## Content rights assessment

Technical packaging is ready. Permission to redistribute retailer-derived names and descriptions has not been established. This dataset must not be assumed covered by the repository's source-code license or by the owner's authorization to import it locally.

The [CellphoneS usage policy](https://cellphones.com.vn/chinh-sach-su-dung) restricts copying, distribution, republication, storage and transmission of website content beyond its stated access-related exception. Its [terms and privacy page](https://cellphones.com.vn/dieu-khoan-su-dung-chinh-sach-bao-mat) also describes limits on third-party/commercial use. The [TGDD terms](https://www.thegioididong.com/tos) were consulted, but no affirmative license to redistribute this seed was established. These sources were checked for this review; they are not a legal determination of every field's copyright status.

Do not place this retailer-derived archive in a public repository or public release without documented rights clearance. Private access reduces exposure but does not resolve permission. Before sharing, the project owner should establish an applicable permission or other reviewed basis, identify the permitted audience and purpose, and record the decision. If that cannot be established, prepare a separately versioned original/synthetic dataset instead; do not silently replace v1.

## Proposed private delivery after clearance

1. Record the content permission/basis, owner approval, recipient group, allowed use and retention policy in the team's private record. Keep the archive outside Git.
2. Choose either an authenticated release on a repository verified to be private, or an approved private object-storage bucket with public access blocked and narrowly scoped access. A draft release on a public repository must not be treated as private storage.
3. Use immutable object/version naming such as `catalog-seeds/catalog-dev-v1/catalog-dev-v1.tar.gz`. Deliver the archive hash through an authenticated team channel independently of the download. Do not place credentials or signed download URLs in committed documentation.
4. The owner uploads only after explicit authorization. No upload, bucket creation, release creation, or public distribution was performed in this task.
5. Record the actual approved location and recipient instructions privately. There is currently no shared download URL.

## Teammate obtain and verify

After permission and delivery are approved, obtain the archive from the owner-provided authenticated location. Verify the archive hash against the value supplied through the approved channel:

```bash
sha256sum catalog-dev-v1.tar.gz
tar -tzf catalog-dev-v1.tar.gz
mkdir -p tools/catalog-import/output
tar -xzf catalog-dev-v1.tar.gz -C tools/catalog-import/output
(cd tools/catalog-import/output/catalog-dev-v1 && sha256sum -c SHA256SUMS)
```

The archive listing must contain exactly the three regular files listed above, without absolute paths, parent-directory traversal or links. The fixed archive hash is recorded above. Checksums detect changes; they do not grant permission or establish authenticity unless the expected hash comes from a trusted channel.

Continue with [FRONTEND_SEED.md](FRONTEND_SEED.md). Load only into a positively identified fresh local PostgreSQL 17 database with V1–V9. Keep private environment configuration outside Git. A later authorized move to 50 PUBLISHED products requires a new seed version and checksums; never overwrite this v1 archive or reuse its version for changed data.
