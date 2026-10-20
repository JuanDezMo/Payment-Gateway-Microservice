package com.paymentgateway.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentgateway.model.CardBrand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAMessage(ErrorCode code) {
        assertThat(code.message()).isNotBlank();
    }

    @Test
    void messagesMatchTheContract() {
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.message()).isEqualTo("Insufficient funds");
        assertThat(ErrorCode.INVALID_API_KEY.message()).isEqualTo("Invalid or missing API key");
        assertThat(ErrorCode.BRAND_NOT_ALLOWED.message()).isEqualTo("Card brand not allowed. Use VISA, MASTERCARD or AMEX");
    }

    @Test
    void brandOutsideTheListIsNotRecognized() {
        assertThat(CardBrand.from("DINERS")).isEmpty();
        assertThat(CardBrand.from(null)).isEmpty();
        assertThat(CardBrand.from("visa")).isEmpty();
        assertThat(CardBrand.from("VISA")).contains(CardBrand.VISA);
    }
}
