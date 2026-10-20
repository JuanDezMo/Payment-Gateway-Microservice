package com.paymentgateway.exception;

/** Catalogo unico de codigos de error: status del contrato y mensaje en ingles. */
public enum ErrorCode {
    INVALID_REQUEST("INVALID", "Invalid request"),
    BRAND_NOT_ALLOWED("INVALID", "Card brand not allowed. Use VISA, MASTERCARD or AMEX"),
    INSUFFICIENT_FUNDS("REJECTED", "Insufficient funds"),
    GATEWAY_ERROR("ERROR", "Error processing the payment, please try again"),
    CARD_PROBLEM("CARD_FLAGGED", "The card has problems, use another payment method"),
    INVALID_API_KEY("UNAUTHORIZED", "Invalid or missing API key");

    private final String status;
    private final String message;

    ErrorCode(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String status() {
        return status;
    }

    public String message() {
        return message;
    }
}
