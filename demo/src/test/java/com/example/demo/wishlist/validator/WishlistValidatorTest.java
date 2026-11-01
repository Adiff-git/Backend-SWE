package com.example.demo.wishlist.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.wishlist.exception.InvalidWishlistRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WishlistValidatorTest {
    private final WishlistValidator validator = new WishlistValidator();

    @Test
    void validateOwner_shouldRejectMissingIdentity_whenOwnerIsNull() {
        assertThatThrownBy(() -> validator.validateOwner(null)).isInstanceOf(IdentityUnavailableException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,20", "1,100", "2147483647,1"})
    void validatePage_shouldAcceptBounds_whenInputIsValid(int page, int size) {
        assertThatCode(() -> validator.validatePage(page, size)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101", "0,-1"})
    void validatePage_shouldRejectInvalidBounds_whenInputIsInvalid(int page, int size) {
        assertThatThrownBy(() -> validator.validatePage(page, size))
                .isInstanceOf(InvalidWishlistRequestException.class);
    }
}
