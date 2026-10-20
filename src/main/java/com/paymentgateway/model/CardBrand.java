package com.paymentgateway.model;

import java.util.Optional;

public enum CardBrand {
    VISA,
    MASTERCARD,
    AMEX;

    public static Optional<CardBrand> from(String value) {
        for (CardBrand brand : values()) {
            if (brand.name().equals(value)) {
                return Optional.of(brand);
            }
        }
        return Optional.empty();
    }
}
