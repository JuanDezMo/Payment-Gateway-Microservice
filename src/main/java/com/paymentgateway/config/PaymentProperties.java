package com.paymentgateway.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de negocio y secretos del servicio. Todas son obligatorias: si falta una, la app no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @Min(1) int maxErrorAttempts,
        @NotBlank @Pattern(regexp = UNRESOLVED_GUARD) String apiKey,
        @NotBlank @Pattern(regexp = UNRESOLVED_GUARD) String cardFingerprintSecret) {

    // Spring deja "${VAR}" como texto si la variable no existe; este patron lo rechaza
    private static final String UNRESOLVED_GUARD = "^(?!\\$\\{).*";
}
