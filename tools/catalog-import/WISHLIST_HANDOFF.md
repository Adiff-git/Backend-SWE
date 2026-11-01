# Private 1,000-product wishlist handoff

`catalog-wishlist-v1` is a new local Catalog seed derived from the immutable
`catalog-dev-v1`. It retains all 1,000 Product UUIDs, Variant UUIDs, categories,
SKUs, descriptions and historical demo prices. Its only product-field change is
`status=PUBLISHED` for the original 995 DRAFT products. It contains no images,
users, credentials or storage objects. The original v1 seed stays unchanged.

## Prepare and review on the owner's machine

```bash
python3 tools/catalog-import/prepare_wishlist_seed.py \
  --source tools/catalog-import/output/catalog-dev-v1 \
  --output tools/catalog-import/output/catalog-wishlist-v1
(cd tools/catalog-import/output/catalog-wishlist-v1 && sha256sum -c SHA256SUMS)
docker build -f tools/catalog-import/Dockerfile.wishlist-seed \
  -t catalog-wishlist-seed:local .
```

The output directory is Git ignored. The build copies retailer-derived text
into the image. Do not push, share or publish that image until the content
permission/basis, intended recipients and destination have been reviewed and
approved. A private registry restricts access but does not provide content
permission. Retailer images need separate authorization and are omitted.

## After approved private delivery

The owner tags and pushes the reviewed image to the approved private registry.
The recipient pulls it, extracts the seed into their own checkout, and verifies
checksums:

```bash
docker pull <approved-private-image>:catalog-wishlist-v1
mkdir -p tools/catalog-import/output/catalog-wishlist-v1
docker run --rm \
  -v "$PWD/tools/catalog-import/output/catalog-wishlist-v1:/seed-out" \
  <approved-private-image>:catalog-wishlist-v1
```

For the one-command local flow, from a checkout with Java 25, Maven, Docker
Compose and Python 3 installed, set `WISHLIST_SEED_IMAGE` to the approved
private image and run:

```bash
bash tools/catalog-import/start_wishlist.sh
```

The default image is `hza206/catalog:wishlist-v1`. The teammate must have
read access to that private Docker Hub repository and complete `docker login`
once on their machine. The script pulls the image, verifies and extracts the seed, starts a new isolated
PostgreSQL Compose project, starts Spring Boot with Flyway, previews and loads
the seed, then checks `totalItems=1000` through the public API. Credentials stay
in ignored `.env.wishlist.local`; the database stays in its own Docker volume.
It uses local ports 55434 and 18082, which must be available. The image does
not contain image bytes or a prepopulated database volume.
