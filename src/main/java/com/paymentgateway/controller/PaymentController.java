package com.paymentgateway.controller;

import com.paymentgateway.dto.PaymentRequest;
import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String JSON = "application/json";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Process a payment (simulated)", security = @SecurityRequirement(name = "apiKey"))
    @ApiResponse(responseCode = "200", description = "Payment approved (or already approved for the reservation)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"APPROVED\",\"transactionId\":\"3f2b8c0e-6a1d-4e55-9a57-0d1c2b3a4f5e\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid format or card brand not allowed",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = {
                            @ExampleObject(name = "INVALID_REQUEST", value = "{\"status\":\"INVALID\",\"code\":\"INVALID_REQUEST\",\"message\":\"Invalid request\"}"),
                            @ExampleObject(name = "BRAND_NOT_ALLOWED", value = "{\"status\":\"INVALID\",\"code\":\"BRAND_NOT_ALLOWED\",\"message\":\"Card brand not allowed. Use VISA, MASTERCARD or AMEX\"}")}))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"UNAUTHORIZED\",\"code\":\"INVALID_API_KEY\",\"message\":\"Invalid or missing API key\"}")))
    @ApiResponse(responseCode = "402", description = "Payment rejected (card ending in 0000)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"REJECTED\",\"code\":\"INSUFFICIENT_FUNDS\",\"message\":\"Insufficient funds\"}")))
    @ApiResponse(responseCode = "422", description = "Third error with the same card and reservation; use another payment method",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"CARD_FLAGGED\",\"code\":\"CARD_PROBLEM\",\"message\":\"The card has problems, use another payment method\"}")))
    @ApiResponse(responseCode = "502", description = "Simulated gateway error (card ending in 9999); retryable",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"ERROR\",\"code\":\"GATEWAY_ERROR\",\"message\":\"Error processing the payment, please try again\"}")))
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.process(request);
    }
}
