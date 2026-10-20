package com.paymentgateway.exception;

/** Pago ya guardado que termino en REJECTED, ERROR o CARD_FLAGGED; el handler lo traduce a HTTP. */
public class PaymentDeclinedException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentDeclinedException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
