CREATE TABLE user_behavior_events (
 id UUID PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 event_type VARCHAR(24) NOT NULL CHECK (event_type IN ('PRODUCT_VIEW','SEARCH','PRODUCT_CLICK','WISHLIST_ADD','WISHLIST_REMOVE')),
 product_id UUID REFERENCES products(id) ON DELETE CASCADE,
 search_query VARCHAR(256),
 normalized_query TEXT,
 client_event_id UUID,
 source_context VARCHAR(24) CHECK (source_context IN ('SEARCH_RESULTS','CATEGORY_LIST','PRODUCT_LIST','OTHER')),
 occurred_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_behavior_customer_client UNIQUE(customer_id, client_event_id),
 CONSTRAINT ck_behavior_fields CHECK (
  (event_type = 'SEARCH' AND product_id IS NULL AND search_query IS NOT NULL AND length(trim(search_query)) > 0
    AND normalized_query IS NOT NULL AND length(trim(normalized_query)) > 0 AND source_context IS NULL)
  OR (event_type IN ('PRODUCT_VIEW','PRODUCT_CLICK','WISHLIST_ADD','WISHLIST_REMOVE') AND product_id IS NOT NULL
    AND search_query IS NULL AND normalized_query IS NULL AND (event_type = 'PRODUCT_CLICK' OR source_context IS NULL))),
 CONSTRAINT ck_behavior_origin CHECK (
  (event_type IN ('PRODUCT_VIEW','SEARCH','PRODUCT_CLICK') AND client_event_id IS NOT NULL)
  OR (event_type IN ('WISHLIST_ADD','WISHLIST_REMOVE') AND client_event_id IS NULL))
);
CREATE INDEX idx_behavior_customer_history ON user_behavior_events(customer_id, occurred_at DESC, id DESC);
CREATE INDEX idx_behavior_retention ON user_behavior_events(occurred_at);
-- Analytics scan only the requested, bounded time window using the retention index.
