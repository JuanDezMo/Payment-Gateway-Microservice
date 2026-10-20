package com.paymentgateway.exception;

public class InvalidPaymentRequestException extends RuntimeException {

    private final ErrorCode errorCode;

    public InvalidPaymentRequestException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
