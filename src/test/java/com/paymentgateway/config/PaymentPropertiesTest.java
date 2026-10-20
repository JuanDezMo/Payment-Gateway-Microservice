package com.paymentgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaymentPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PaymentProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void startsWhenAllPropertiesAreResolved() {
        runner.withPropertyValues("payment.max-error-attempts=3", "payment.api-key=key", "payment.card-fingerprint-secret=secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsWhenApiKeyPlaceholderIsUnresolved() {
        runner.withPropertyValues("payment.max-error-attempts=3", "payment.api-key=${PAYMENT_GATEWAY_API_KEY}",
                        "payment.card-fingerprint-secret=secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenFingerprintSecretPlaceholderIsUnresolved() {
        runner.withPropertyValues("payment.max-error-attempts=3", "payment.api-key=key",
                        "payment.card-fingerprint-secret=${CARD_FINGERPRINT_SECRET}")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenApiKeyIsMissing() {
        runner.withPropertyValues("payment.max-error-attempts=3", "payment.card-fingerprint-secret=secret")
                .run(context -> assertThat(context).hasFailed());
    }
}
