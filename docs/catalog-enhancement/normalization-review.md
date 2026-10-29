# Reviewed pilot normalization expansion — 2026-10-29

Scope: only the exact 1,000 Product/Variant identities in the immutable pilot seed, read against the original crawler SQLite snapshot. The populated PostgreSQL target was not contacted. The [machine-readable Brand evidence](brand-review.json) and [technical coverage](technical-review.json) record exact measured counts.

## Brands

Before this review, 151/1,000 pilot records resolved to approved canonical Brands and 849 did not. The unresolved records used 145 distinct raw labels. This review added 63 direct label-to-identical-canonical-identity rules covering 673 pilot records. In 668 of those records, the separate crawled `Hãng sản xuất` field repeated the same label; in five the second field was absent, and none contradicted the selected label. This is corroboration within the same source page, not external manufacturer verification. No suffix stripping, fuzzy matching or cross-brand alias merge was approved. After the review, 824/1,000 resolve and 176 remain unresolved.

`Apple Chính hãng` (17 records) and `Samsung Chính hãng` (20) remain unresolved because their distribution claim has not been approved as an alias of the plain manufacturer name. Generic `Magic` (13), `Hãng khác` (4), and rare/unverified labels remain unresolved. In total, the 176 unresolved records contain 82 distinct raw Brand labels. Unknowns continue to map to null. Existing Brand UUIDs/codes are unchanged; newly approved direct labels have deterministic UUIDv5 `brand|code` identities in the versioned registry.

## Typed specifications

Before this review, 102/1,000 Variants had at least one approved typed fact. The main recurring unmapped fields were the source manufacturer field (885), feature prose (312/261), compatibility text (286), material (224), device power (187), dimensions (186), and category-specific display/storage/battery fields. Arbitrary prose, compatibility lists, and manufacturer duplication are still raw-only; they are not universally filterable technical facts.

The new rules parse exact display dimensions into canonical `widthxheight` text (99 accepted), a leading installed `GB` storage capacity where the remaining text does not imply a second installed capacity (71 accepted), a single laptop `Wh` battery capacity (91 accepted), and a single exact accessory `mAh` battery capacity (36 accepted). CPU model (101), GPU model (101), physical RAM (101), display diagonal (99), and installed OS (23) retain their previous coverage. The new battery mAh rule increased Variants with any approved specification from 102 to 138. Counts of fields overlap within Variants.

The parser keeps `Wh` and `mAh` separate; it does not infer battery voltage or convert between them. It does not convert `TB` to `GB` without an approved convention. It rejects compound/range capacities and resolution labels without pixel dimensions. After adding the rules, 44 pilot records contain 49 invalid candidate values across `Ổ cứng` (30), `Dung lượng pin`/`Dung lượng Pin` (12), and `Pin` (7); raw values remain available in source provenance output. The broad source category path is checked before each rule, and no missing specification is guessed.

The remaining unsupported attributes on 998 records represent 226 distinct raw keys and are intentionally retained in raw source material. Further mappings require a separate review of semantics and category scope. This review did not create a new Product or Variant, alter prices or options, or approve category remapping.
