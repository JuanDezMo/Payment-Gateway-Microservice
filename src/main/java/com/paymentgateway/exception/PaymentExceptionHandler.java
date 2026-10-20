package com.paymentgateway.exception;

import com.paymentgateway.dto.PaymentResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Unico lugar donde un ErrorCode se traduce a codigo HTTP. */
@RestControllerAdvice
public class PaymentExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> HTTP_STATUS = Map.of(
            ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
            ErrorCode.BRAND_NOT_ALLOWED, HttpStatus.BAD_REQUEST,
            ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.PAYMENT_REQUIRED,
            ErrorCode.GATEWAY_ERROR, HttpStatus.BAD_GATEWAY,
            ErrorCode.CARD_PROBLEM, HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCode.INVALID_API_KEY, HttpStatus.UNAUTHORIZED);

    @ExceptionHandler(InvalidPaymentRequestException.class)
    ResponseEntity<PaymentResponse> handleInvalid(InvalidPaymentRequestException e) {
        return body(e.errorCode());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    ResponseEntity<PaymentResponse> handleDeclined(PaymentDeclinedException e) {
        return body(e.errorCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<PaymentResponse> handleMalformed(Exception e) {
        return body(ErrorCode.INVALID_REQUEST);
    }

    public static ResponseEntity<PaymentResponse> body(ErrorCode code) {
        return ResponseEntity.status(HTTP_STATUS.get(code))
                .body(new PaymentResponse(code.status(), null, code.name(), code.message()));
    }
}
