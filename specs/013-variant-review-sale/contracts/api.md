# Public API Contract

All existing route/security/error contracts remain except additive fields and explicitly allowed review parameters. Money is existing `{amount: integer, currency: "VND"}`. No new sale-management or option-resolution route.

## Catalog

`GET /api/products` and `GET /api/products/{productId}` remain anonymous, published-only reads. Existing `sellingPrice`, listing `displayPrice` and cart `unitPrice` always mean current effective price. Existing search price bounds/sorts use effective price before eligibility/pagination; price and specification conditions must match one variant. Existing UUID/tie/null-orphan rules remain.

Add to each priced variant (and listing representative):

- `pricing`: `{originalPrice, effectivePrice, directUnitDiscount, salePercentage, pricedAt}`. Money objects follow existing shape; salePercentage is a decimal number or null. No sale returns original=effective, discount zero, percentage null.
- Keep old effective-price fields synchronized with pricing.effectivePrice. Invalid/unavailable price representations retain existing safe null/ineligibility behavior rather than exposing malformed money.

Add `priceRange`: `{minimum: endpoint|null, maximum: endpoint|null}`, where endpoint is `{variantId, pricing}`. Detail ranges consider catalog-price-eligible variants; listing/search ranges consider the same qualifying variant relation as representative selection. Original/effective pairs never combine different variants; UUID ascending resolves equal extrema. No qualifying price yields null endpoints. Range is catalog price eligibility, not proof of stock availability.

Detail additionally exposes `optionDimensions: [{key,label,values:[{key,label}]}]` ordered by canonical Unicode code points, plus `selectionOptions` canonical key/value map on each existing variant. Preserve `variants[].options` presentation labels and existing UUID/SKU/availability. Choose dimension/value labels from the lowest variant UUID containing that identity. `optionDimensions=[]` and `selectionOptions={}` for a default variant.

Selection rules for clients: missing required keys=incomplete; complete unknown/extra-key selection=nonexistent; complete existing selection resolves to its UUID; availability distinguishes unavailable from purchasable using existing publication/price/inventory rules. Cart accepts the UUID, never authoritative client prices. No auto-generated combinations or automatic cart add from partial selection.

## Cart, voucher wallet and checkout

Keep existing add/change/quantity/selection routes/commands and permissions. Add pricing to eligible cart rows while keeping unitPrice effective and subtotal=effective×quantity; hidden/unavailable rows follow existing redaction/null rules and must not newly reveal base/sale metadata. Cart selection preferences remain independent of eligibility.

Cart response/catalog pricing and voucher preview share one immutable operation-scoped prepared price set and evaluation instant, obtained from coherent inputs once; voucher validation, discount/subtotal calculation and final response do not re-read/reprice those inputs. Prepared values never survive a request; checkout independently obtains fresh authoritative pricing and voucher validation. Voucher minimum spend/percentage base is selected eligible after-sale merchandise subtotal. Existing selected invalid-voucher cleanup/rejection behavior remains; no item voucher allocation.

`POST /api/me/cart/checkout` keeps addressId-only body, required Idempotency-Key, current permission/ownership and 201/Location semantics. New v3 receipts and current order item details add:

- `originalUnitPriceSnapshot`: money (null on legacy order detail).
- `directUnitDiscountSnapshot`: money (null on legacy order detail).
- `directSaleSnapshot`: `{percentage,startsAt,endsAt}` when active at purchase; otherwise null.
- `pricingInstantSnapshot`: UTC timestamp (null on legacy order detail).

Existing unitPriceSnapshot stays effective paid price; lineTotal is unchanged in meaning. Order-level voucher/voucherDiscount remain separate. New v3 evidence is present even on no-sale purchases (known base/effective and zero discount). Old v1/v2 replay returns the original shape/status/body facts, with no inserted new fields or recalculation. Current order detail may add nullable unknown history without retroactively enriching old stored receipts.

All included items and voucher validity use one captured instant after the catalog locks/view. Later processing across a boundary does not move it. Subsequent new checkout sees current state. Replay always returns original evidence and does not call Inventory or redeem again.

## Product reviews

`GET /api/products/{productId}/reviews` remains anonymous for published products and no-store.

| Parameter | Allowed values | Omitted |
| --- | --- | --- |
| page | Canonical nonnegative integer, existing numeric bounds | 0 |
| size | Integer 1–100 | 20 |
| sort | createdAt,desc; createdAt,asc; rating,desc; rating,asc | createdAt,desc |
| rating | Exactly one ASCII digit 1–5 | Any rating |
| hasImages | Lowercase true or false | Any image state |
| hasComment | Lowercase true or false | Any comment state |

All parameters are single-valued. Reject unknown/repeated keys, empty/literal-null/malformed values, rating sets, date/variant filters and arbitrary sort fields with 400 existing review ProblemDetail. All sorts append ID descending. Filter flags combine by AND; false means absence. Comment presence requires a character outside the fixed Unicode White_Space set in data-model.md; null/empty/whitespace-only is absent. Image presence tests attached references, not successful URL signing.

Return existing ReviewPage fields and filtered totalElements/totalPages. Out-of-range page returns empty items with filtered totals. Each review appears once regardless of image count; image order and safe author shape stay unchanged. Add `purchasedOptions` as historical presentation label map; use null only if legacy source is genuinely absent, not live options. Do not expose order/customer/recipient identifiers or addresses through this enrichment.

`GET /api/products/{productId}/rating-summary` accepts no query parameters and remains no-store. Add `ratingDistribution: {"1":count,...,"5":count}` with all keys present. Counts and average remain product-wide, independent of review list filters. Preserve reviewCount and existing averageRating representation: null if no reviews, otherwise existing two-decimal rounding.

## Errors and compatibility

Retain malformed UUID 400, hidden/missing product safe 404, existing private-route 401/403/owned-resource errors and checkout 409/422 mappings. No pricing/option-write HTTP error is introduced because those endpoints are excluded. Persistence validation reports internal fixture/preflight failures without exposing SQL/stack traces to clients. Add response fields without removing existing names; version durable receipts explicitly rather than mutating their shape.
