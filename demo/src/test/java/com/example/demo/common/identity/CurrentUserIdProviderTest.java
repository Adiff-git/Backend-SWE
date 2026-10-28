package com.example.demo.common.identity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class CurrentUserIdProviderTest {
    @Test
    void getCurrentUserId_shouldReturnControlledImmutableIdentity() {
        CurrentUserIdProvider provider = new TestCurrentUserIdProvider(42L);
        assertThat(provider.getCurrentUserId()).isEqualTo(42L);
    }
    @Test
    void getCurrentUserId_shouldFailWhenIdentityIsUnavailable() {
        CurrentUserIdProvider provider = new TestCurrentUserIdProvider(null);
        assertThatThrownBy(provider::getCurrentUserId)
        .isInstanceOf(IdentityUnavailableException.class);
    }
}
