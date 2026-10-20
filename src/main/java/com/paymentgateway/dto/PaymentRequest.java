package com.paymentgateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String reservationId,
        @NotNull BigDecimal amount,
        @NotNull @Valid CardData card) {
}
