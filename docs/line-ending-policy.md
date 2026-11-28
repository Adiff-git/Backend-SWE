# Reproducible text and bootstrap releases

All tracked text uses LF. The only permitted CRLF files are Windows `.bat` and
`.cmd` scripts. Root and nested `.gitattributes` files enforce this independently
of `core.autocrlf`. Binary files and any Git LFS filters/pointers are preserved.
No migration-specific newline exceptions remain. UTF encodings and BOMs are
preserved; normalization never adds or removes a BOM.

Audit a checkout with:

```text
python tools/line_endings.py . --report audit.json
```

The report inventories every tracked file, encoding/BOM, newline counts, raw
SHA-256, and newline-independent content SHA-256. To apply an audited plan, use
`--apply-plan audit.json --backup <new-directory>`; every original hash is checked
before writing, and all originals are backed up before the first write. LFS and
binary files are excluded from normalization. Do not commit private audit inputs
or credentials.

## 2026-11-28 release

The normalized source revision is
`89e3b6c5bc95b533386a349c65305613617d8709` (Catalog-Data main).
Its catalog manifest SHA-256 is
`b07f38b6b13e9d2a517643da4298728b300d739ecad8b8ecce401aa525886b4a`.
The approved-price CSV's byte checksum and its manifest/checksum-list entries
were recalculated; CSV values and all product images remain unchanged.

`demo-v1` through `demo-v5` were re-frozen in separate output directories using
the same approved candidate payload, new exact source/migration bytes, current
importer, dependency lock, and both startup script fingerprints. The existing
freeze and detached approval commands were used. Previous releases remain in Git
history and private originals; `Catalog-Data/bootstrap` records old/new digests in
`demo-bootstrap/line-ending-release.json`. Labels alone do not establish identity:
the full artifact digest and detached approval must match. The new default is
`demo-v6` with digest
`fd81f6e0eee5b0ab8aeda6a03c6d2f7144593f044f44312c2e9abe1028e32be3`.
The reissued `demo-v5` digest is
`dd04df8b1933614b37dab998547ef734707e787052e59588f82c18591c4627bf`.

## Existing databases

All 30 logical Flyway checksums are unchanged. Normalizing line terminators does
not require Flyway `repair`, an SQL edit, or a database reset. No existing Flyway
history is modified. New exact migration byte fingerprints are recorded in each
approved normalized artifact.

Read-only comparison of the existing and freshly migrated databases covered 912
schema records. Twenty stored function definitions differ only in line endings;
constraints, triggers, indexes, and columns match. The previous exact schema hash
is `c6b821c8e8b0b882734feaa65593611a55c149b409c98c0ab0344632d54872d9`;
the normalized fresh-schema hash is
`fa8d23ba01c5fc57b9f9fd1f161160ed7b2bb670a6b7b415418c963c538e7070`.
The runtime schema fingerprint remains exact; comparison normalization was used
only as read-only verification evidence, never as an acceptance fallback.

Bootstrap completion metadata also pins the full artifact/importer/source
identity. An installation imported with an earlier artifact retains that identity;
it is not automatically adopted by a different release, even when payloads match.
Keep that installation's matching checkout/image/artifact for continued operation.
This change does not migrate its operational completion marker. Do not use
`--fresh` merely to resolve a fingerprint mismatch. Existing `.env.local`, original
private artifacts, databases, and persistent resources are preserved.

Publish the normalized Catalog-Data main and bootstrap commits before publishing
the Backend-SWE consumer commit, so its source pin and artifacts are available to
collaborators. A fresh consumer checkout must use the matching normalized release.

## WSL artifact installation

Bash setup validates downloaded data in staging, installs an unmounted copy of
the selected artifact, then validates the installed bytes again. This avoids a
Docker Desktop/DrvFS directory-handle failure when a previously bind-mounted
staging directory is moved. The configured artifact is checked twice; no checksum
or approval gate is skipped. Existing local artifact versions are not overwritten.
