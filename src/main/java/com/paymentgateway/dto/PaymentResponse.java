package com.paymentgateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Respuesta del contrato: status siempre presente; transactionId, code y message segun el caso. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(String status, String transactionId, String code, String message) {
}
