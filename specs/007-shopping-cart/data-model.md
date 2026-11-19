# Cart data model

2026-11-17. Cart and item IDs/user IDs remain Long; catalog IDs remain UUID. Entities use scalar references and safe Lombok accessors. No entity graphs or stored monetary amounts/URLs are exposed.

carts: identity id, NOT NULL user_id; uk_carts_user_id and fk_carts_user_id ON DELETE CASCADE.

cart_items: identity id, cart_id, product_id, product_variant_id, quantity, selected, added_at, public_product_name, public_sku and JSONB public_options. Ownership FK cascades only from cart; product and variant references use RESTRICT. uk_cart_items_cart_id_product_variant_id enforces one row per variant. Public snapshots are nonblank/object constrained. Ordering/index stays cart_id, added_at DESC, id ASC.

Current quantity invariant is 1..999. V21 is historical and remains unchanged; additive V22 replaces ck_cart_items_quantity. Legacy rows above 999 prevent migration without changing data; resolve them explicitly before retry. Separate FKs prove reference existence, not variant-parent pairing; CartValidator enforces the pairing on eligible mutations.

## Transitions

- Successful first add creates a cart and selected row; repeat add increments checked quantity while preserving row identity/time/selection.
- Quantity replacement updates only quantity, with current eligibility required.
- Change to absent eligible same-product variant updates source reference/snapshots and quantity=1; identity/time/selection persist.
- Change to existing distinct target deletes source only; target fields all remain unchanged.
- Change to current variant leaves the row unchanged.
- Select/deselect changes selection only under common coordination; GET never writes.
- Last removal leaves cart empty. Account lifecycle cascade and referenced catalog RESTRICT stay unchanged.

## DTOs and transient values

CartReadSeed, PreparedCart and AddCartResult are immutable Lombok @Value classes in cart/dto/response. Copy maps/lists defensively. Seeds capture owned rows/snapshots; PreparedCart carries final catalog states, eligible line subtotals, checked selected counts/quantities and exact BigInteger total. Signed URLs live transiently in CartVariantState, never in durable entities.

CartResponse has items, totalItemCount (all rows), selectedItemCount (eligible selected rows), selectedTotalQuantity (eligible selected units), selectedSubtotal and finalTotal. All Money values reuse SellingPrice {amount,currency} in whole VND. Missing/ineligible line subtotal is null; empty aggregates are zero. totalItemCount derives from the complete captured item collection, including unavailable/unselected rows.
