package com.paymentgateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentgateway.dto.CardData;
import com.paymentgateway.dto.PaymentRequest;
import com.paymentgateway.exception.ErrorCode;
import com.paymentgateway.exception.InvalidPaymentRequestException;
import com.paymentgateway.model.SimulationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CardRulesTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
    private final CardRules rules = new CardRules(clock);

    private PaymentRequest request(String amount, String brand, String number, String expiry, String cvv) {
        return new PaymentRequest("res-1", new BigDecimal(amount), new CardData(brand, number, expiry, cvv));
    }

    private PaymentRequest valid() {
        return request("10.00", "VISA", "4111111111114242", "12/30", "123");
    }

    @Test
    void validRequestPasses() {
        assertThatCode(() -> rules.validate(valid())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00"})
    void nonPositiveAmountIsInvalid(String amount) {
        assertInvalid(request(amount, "VISA", "4111111111114242", "12/30", "123"), ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"09/26", "01/20", "13/30", "1230", "12/2030", "ab/cd"})
    void expiredOrMalformedExpiryIsInvalid(String expiry) {
        assertInvalid(request("10", "VISA", "4111111111114242", expiry, "123"), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void currentMonthIsNotExpired() {
        assertThatCode(() -> rules.validate(request("10", "VISA", "4111111111114242", "10/26", "123")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"411111111111abcd", "411111111111", "41111111111111111111", ""})
    void badNumberIsInvalid(String number) {
        assertInvalid(request("10", "VISA", number, "12/30", "123"), ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "1234", "abc"})
    void badCvvIsInvalid(String cvv) {
        assertInvalid(request("10", "VISA", "4111111111114242", "12/30", cvv), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void numberWithoutLuhnIsAccepted() {
        assertThatCode(() -> rules.validate(request("10", "VISA", "1234567890123456", "12/30", "123")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DINERS", "visa", ""})
    void brandOutsideTheListIsRejected(String brand) {
        assertInvalid(request("10", brand, "4111111111114242", "12/30", "123"), ErrorCode.BRAND_NOT_ALLOWED);
    }

    @Test
    void missingBrandIsRejected() {
        assertInvalid(request("10", null, "4111111111114242", "12/30", "123"), ErrorCode.BRAND_NOT_ALLOWED);
    }

    @Test
    void brandNotMatchingTheNumberIsAccepted() {
        assertThatCode(() -> rules.validate(request("10", "AMEX", "4111111111114242", "12/30", "123")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"4111111111110000,REJECTED", "4111111111119999,ERROR", "4111111111114242,APPROVED", "4111111111110001,APPROVED"})
    void simulateDependsOnCardSuffix(String number, SimulationResult expected) {
        assertThat(rules.simulate(number)).isEqualTo(expected);
    }

    private void assertInvalid(PaymentRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> rules.validate(request))
                .isInstanceOfSatisfying(InvalidPaymentRequestException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}
