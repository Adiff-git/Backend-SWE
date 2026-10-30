package com.example.demo.common.identity;
/** Controlled identity for tests only. Not a Spring bean or production adapter. */
public final class TestCurrentUserIdProvider implements CurrentUserIdProvider {
    private final Long userId;
    public TestCurrentUserIdProvider(Long userId) {
        this.userId = userId;
    }
    @Override
    public Long getCurrentUserId() {
        if (userId == null) {
            throw new IdentityUnavailableException();
        }
        return userId;
    }
}
