package com.paymentgateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentgateway.config.PaymentProperties;
import org.junit.jupiter.api.Test;

class CardFingerprintServiceTest {

    private final CardFingerprintService service = new CardFingerprintService(new PaymentProperties(3, "key", "secret"));

    @Test
    void sameNumberGivesSameFingerprint() {
        assertThat(service.of("4111111111114242")).isEqualTo(service.of("4111111111114242"));
    }

    @Test
    void differentNumbersGiveDifferentFingerprints() {
        assertThat(service.of("4111111111114242")).isNotEqualTo(service.of("4111111111114243"));
    }

    @Test
    void differentSecretsGiveDifferentFingerprints() {
        var other = new CardFingerprintService(new PaymentProperties(3, "key", "another-secret"));
        assertThat(other.of("4111111111114242")).isNotEqualTo(service.of("4111111111114242"));
    }

    @Test
    void fingerprintDoesNotContainTheNumber() {
        assertThat(service.of("4111111111114242")).doesNotContain("4111111111114242").hasSize(64);
    }
}
