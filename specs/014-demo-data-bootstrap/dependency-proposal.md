# Dependency and Tool Image Proposal — Demo Bootstrap

**Status**: APPROVED by the user on 2026-11-27: "I approve" in response to the exact image/package/hash proposal. Runtime build/verification and import/reset outcomes are recorded separately.
**Prepared**: 2026-11-27
**Scope**: Standalone local Python helper only; no Java/Maven dependency or plugin changes. Private non-commercial course use under F-39; no public/commercial distribution.

## Proposed base image

Use `python:3.12-slim-bookworm@sha256:44cf1793efa7c7a476bb0f2cff81443f4a072c90c1cb127e2924933daa3e8c64` for both build/runtime stages. Verified official registry metadata identifies **Python 3.12.16** on Linux amd64 and arm64; both variants share the pinned multi-platform index.

| Platform | Manifest digest |
|---|---|
| linux/amd64 | `sha256:1475c4f5b6bcb7d35e57661d6f5d7429ec4041275633c15f2b49350ac898276d` |
| linux/arm64 | `sha256:209c477b2d5268b578d813ae5a4668682b55252a5b32ad1148dd49e937119101` |

Use a multi-stage build and non-root UID 10001 runtime, narrow `tools/demo-bootstrap` build context and dedicated .dockerignore. Resolve checkpoint permissions without a root-runtime fallback. No image pull/build was performed to obtain this metadata. Existing PostgreSQL/LocalStack/Mailpit/backend image/dependency choices stay unchanged.

## Exact proposed packages

Use binary wheels only, exact pins and `--require-hashes`; no source compilation, optional test/dev/docs/CRT extras, pip upgrade or dynamic startup installation. Choose CPython 3.12 manylinux wheels for the two Linux architectures; the pinned Debian bookworm image supports the chosen glibc baselines. Runtime compatibility/login/image decoding is verified after approval, not claimed here.

| Package | Version | Purpose |
|---|---|---|
| psycopg | `3.3.9` | Direct PostgreSQL transactions and parameterized SQL |
| psycopg-binary | `3.3.9` | Matching binary PostgreSQL adapter/libpq distribution |
| boto3 | `1.44.12` | Signed local S3 API calls |
| Pillow | `13.0.0` | Decode/verify real image content and dimensions |
| bcrypt | `5.0.0` | BCrypt cost-10 hashes compatible with existing login |
| botocore | `1.44.12` | boto3 runtime dependency |
| s3transfer | `0.19.3` | boto3 runtime dependency |
| jmespath | `1.1.0` | boto3/botocore runtime dependency |
| six | `1.17.0` | python-dateutil runtime dependency |
| urllib3 | `2.8.0` | botocore HTTPS dependency |
| typing-extensions | `4.16.0` | psycopg on Python 3.12 |
| python-dateutil | `2.9.0.post0` | botocore-compatible date parsing dependency (must remain <3) |

`python-dateutil` is intentionally **2.9.0.post0**, not current 4.x: botocore 1.44.12 requires `<3.0.0`. psycopg and psycopg-binary versions match; s3transfer 0.19.3 meets boto3 >=0.19.0,<0.20.0; botocore 1.44.12 meets boto3 >=1.44.12,<1.45.0. All selected package Python requirements admit Python 3.12.

## Exact wheel allowlist and SHA-256 hashes

The following hashes are read from official PyPI release metadata. Package bytes have not been downloaded/executed; build-time hash checking and actual import/codec/authentication smoke checks are T004/future operational evidence. These lines are a **proposal**, not an installed lockfile.

### psycopg==3.3.9

[Official PyPI release metadata](https://pypi.org/pypi/psycopg/3.3.9/json)

| Wheel | SHA-256 |
|---|---|
| `psycopg-3.3.9-py3-none-any.whl` | `d2d7d04a1ce6dab4f5b27316bea3b52585cafa2f2d631e5b09dd69823f5c26f6` |

### psycopg-binary==3.3.9

[Official PyPI release metadata](https://pypi.org/pypi/psycopg-binary/3.3.9/json)

| Wheel | SHA-256 |
|---|---|
| `psycopg_binary-3.3.9-cp312-cp312-manylinux2014_x86_64.manylinux_2_17_x86_64.whl` | `a99f0b9a19ed7a56bf5d6776456b8f15f5364db8853b0f5d184db394a45c391d` |
| `psycopg_binary-3.3.9-cp312-cp312-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl` | `b081348d8992af26e9b252f04876c286428418a6b7845d8d4caae2044b259668` |

### boto3==1.44.12

[Official PyPI release metadata](https://pypi.org/pypi/boto3/1.44.12/json)

| Wheel | SHA-256 |
|---|---|
| `boto3-1.44.12-py3-none-any.whl` | `9f1cf241303edb523af912ebdc001e0563301d9ba8eef076d25fe9a8bfb6efaf` |

### Pillow==13.0.0

[Official PyPI release metadata](https://pypi.org/pypi/Pillow/13.0.0/json)

| Wheel | SHA-256 |
|---|---|
| `pillow-13.0.0-cp312-cp312-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl` | `8bf295fe459343bc6093c8e5e0df60bfa189e0fcb775a22e2c2488fdeccc6c0d` |
| `pillow-13.0.0-cp312-cp312-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl` | `42d63be3af4f9b951b199b0a9d23365d37ab14687e6d77b74940432c8515c4ca` |

### bcrypt==5.0.0

[Official PyPI release metadata](https://pypi.org/pypi/bcrypt/5.0.0/json)

| Wheel | SHA-256 |
|---|---|
| `bcrypt-5.0.0-cp39-abi3-manylinux_2_34_aarch64.whl` | `edfcdcedd0d0f05850c52ba3127b1fce70b9f89e0fe5ff16517df7e81fa3cbb8` |
| `bcrypt-5.0.0-cp39-abi3-manylinux_2_34_x86_64.whl` | `611f0a17aa4a25a69362dcc299fda5c8a3d4f160e2abb3831041feb77393a14a` |

### botocore==1.44.12

[Official PyPI release metadata](https://pypi.org/pypi/botocore/1.44.12/json)

| Wheel | SHA-256 |
|---|---|
| `botocore-1.44.12-py3-none-any.whl` | `d5df4cf868f2c4050999a0e6bc94e0a039017e719f4e27b2a8e87053856787b9` |

### s3transfer==0.19.3

[Official PyPI release metadata](https://pypi.org/pypi/s3transfer/0.19.3/json)

| Wheel | SHA-256 |
|---|---|
| `s3transfer-0.19.3-py3-none-any.whl` | `24eb7535804306355e31c952a90022bf5b2f9d760f9a11062c7493a217c08553` |

### jmespath==1.1.0

[Official PyPI release metadata](https://pypi.org/pypi/jmespath/1.1.0/json)

| Wheel | SHA-256 |
|---|---|
| `jmespath-1.1.0-py3-none-any.whl` | `a5663118de4908c91729bea0acadca56526eb2698e83de10cd116ae0f4e97c64` |

### six==1.17.0

[Official PyPI release metadata](https://pypi.org/pypi/six/1.17.0/json)

| Wheel | SHA-256 |
|---|---|
| `six-1.17.0-py2.py3-none-any.whl` | `4721f391ed90541fddacab5acf947aa0d3dc7d27b2e1e8eda2be8970586c3274` |

### urllib3==2.8.0

[Official PyPI release metadata](https://pypi.org/pypi/urllib3/2.8.0/json)

| Wheel | SHA-256 |
|---|---|
| `urllib3-2.8.0-py3-none-any.whl` | `0cf3cae568d36aa9576b28dfb35f11328f1cb974ca7647d9475ebb86c75ac6e3` |

### typing-extensions==4.16.0

[Official PyPI release metadata](https://pypi.org/pypi/typing-extensions/4.16.0/json)

| Wheel | SHA-256 |
|---|---|
| `typing_extensions-4.16.0-py3-none-any.whl` | `481caa481374e813c1b176ada14e97f1f67a4539ce9cfeb3f350d78d6370c2e8` |

### python-dateutil==2.9.0.post0

[Official PyPI release metadata](https://pypi.org/pypi/python-dateutil/2.9.0.post0/json)

| Wheel | SHA-256 |
|---|---|
| `python_dateutil-2.9.0.post0-py2.py3-none-any.whl` | `a8b2bc7bffae282281c8140a97d3aa9c14da0b136dfe83f850eea9a5f7470427` |

## Verification and limitations

- Public PyPI JSON and Docker official library registry metadata were queried without downloading/installing packages or pulling images. Windows certificate validation was used; TLS verification was not disabled. The initial MSYS Python HTTPS attempt failed because its CA trust was incomplete; no packages/settings were changed to work around it.
- Metadata confirms declared version-range/Python compatibility and published wheel availability/hashes. This is not a vulnerability audit, executable compatibility test or performance result. Runtime image build/imports, database/S3 communication, Pillow WebP decoding and Java login compatibility remain unexecuted.
- Private raw metadata snapshots are `.private/demo-bootstrap/reports/setup/dependency-metadata.json`, `dependency-proposal-metadata.json` and `python-image-metadata.json`; source/working-state baseline is `baseline.json`. No credentials, tokens or package binaries are included in this proposal.
- [Official Python image definition](https://github.com/docker-library/python), [official image registry manifest](https://registry-1.docker.io/v2/library/python/manifests/3.12-slim-bookworm). The approved digest, not the mutable tag alone, is the reproducibility anchor.

## Approval requested

Approve exactly the image index/platform identities, 12 package versions and wheel hashes above for the isolated helper image. Approval permits T004 to create the lockfile/build image and verify it; it does not approve new Java dependencies, test suites, cloud targets, broad cleanup, public redistribution or commercial use. Any changed pin/digest/package set requires a new concrete review unless already authorized.

**Developer decision**: APPROVED — explicit user response "I approve" to this concrete proposal. Exact image digests, package versions and wheel hashes above are authorized; changed dependencies require review.
