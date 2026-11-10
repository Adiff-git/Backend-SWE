# Implemented API validation

GET /api/products remains public,PUBLISHED-only.9 parameters:q,categoryId,repeated brandId,minPrice,maxPrice,specFilters,sort,page,size. Default0/20,max100;no-q UUID ASC,q relevance;explicit price/name/relevance modes. Only listing gains displayPrice and representativeVariantId,paired null for the documented orphan exception. Detail/Wishlist/image contracts retained.

Strict parser rejects duplicate singleton parameters,duplicate JSON members even equal,invalid null/types/limits/units/operators/unsupported keys/categories with400 ProblemDetail. Valid no-match/unknown well-formed IDs yield200 empty. Nonblank successful q has fuzzy-enabled/exact-only mode header;required capability loss503,never silent fallback. Both original T020 header assertions remain enabled and passed.

Approved isolated openapi-spec-validator0.9.0/openapi-schema-validator0.9.0/OAS31Validator with URI checker:both documents valid,41 positive/negative probes,60 refs. Actual HTTP DTO/error/publication/auth regressions pass;static schema validation is not claimed to detect duplicate JSON members or other prose-only rules. No new wire fields for caches/rank internals.
