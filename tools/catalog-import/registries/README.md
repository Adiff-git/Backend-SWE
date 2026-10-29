# Catalog enhancement registries, version 1

These files are reviewed source mapping rules, not approval to import or publish records.

- `brands-v1.json` provides stable codes/UUIDs for a conservative initial set of exact brand labels and documented lexical aliases. Unicode NFC, whitespace and case normalization apply to known labels. It does not strip distribution claims or guess manufacturers from names. Unknown labels are reported and map to null.
- `categories-v1.json` intentionally has no newly approved mappings. Add entries `{ "source": "tgdd", "path": ["Laptop"], "category_id": "<existing reviewed UUID>" }` only after reviewing that exact mapping. Empty mappings do not prevent metadata enrichment of independently matched existing records. This feature never creates Products or Variants.
- `attributes-v1.json` defines canonical keys, source-key aliases, value types, applicable exact source category path labels, units, filter metadata and parser version. These path labels select parsers; they do not authorize changing target Category UUIDs. Target enrichment preserves existing category. Existing-product enrichment does not change category assignments.

Quantity rules accept positive amounts no greater than 10^12 with at most six fractional decimal places. Supported conversions are g/kg to g and spelling normalization of inch/inches and Hz spacing. RAM/storage MB/TB conventions, compound capacity claims, ranges and unknown values remain unresolved. Physical RAM is not maximum/virtual RAM. Installed OS is not launch OS.

Raw source JSON, unmapped keys, invalid values and issues are included in every normalized output row. The complete registry digest and normalization version bind the normalized content hash; changing rules requires a newly reviewed import manifest. Registry versions other than 1 fail closed.

Select only existing pilot identities from the raw snapshot from repository root:

```bash
python3 tools/catalog-import/prepare_enrichment.py --seed tools/catalog-import/output/catalog-dev-v1/catalog.json --input-db ../electronics-crawler/data/catalogue.sqlite --output-dir tools/catalog-import/output/enhancement/pilot
python3 -m unittest discover -s tools/catalog-import -p test_enhance_normalize.py -v
```

`pilot-enrichment-candidates.jsonl` contains only exact seed/source matches; `pilot-enrichment-summary.json` contains measured pilot candidate counts. The default output directory is Git-ignored. Enrichment-candidate counts describe useful source metadata, not matches against a target database. Source read-only access and duplicate detection do not imply merchant price freshness, product grouping or publication approval.

Pilot review 2026-10-29 added 63 directly corroborated Brand labels, without merging distribution-qualified labels. It also added strict display resolution, leading installed-GB storage, laptop battery Wh, and accessory battery mAh parsers. `Wh` and `mAh` are never converted; `TB` storage remains unresolved. See `docs/catalog-enhancement/brand-review.json` and `technical-review.json` for measured evidence and rejection counts.
