package com.paymentgateway.dto;

import jakarta.validation.constraints.NotNull;

/** brand llega como texto para poder responder BRAND_NOT_ALLOWED ante una franquicia desconocida. */
public record CardData(String brand, @NotNull String number, @NotNull String expiry, @NotNull String cvv) {
}
