package com.paymentgateway.service;

import com.paymentgateway.config.PaymentProperties;
import com.paymentgateway.dto.PaymentRequest;
import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.exception.ErrorCode;
import com.paymentgateway.exception.PaymentDeclinedException;
import com.paymentgateway.mapper.PaymentMapper;
import com.paymentgateway.model.CardBrand;
import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import com.paymentgateway.model.SimulationResult;
import com.paymentgateway.repository.PaymentAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orquesta el pago: valida, aplica idempotencia, cuenta intentos, simula en memoria y guarda.
 * No es transaccional: el intento debe quedar guardado aunque luego se lance PaymentDeclinedException.
 */
@Service
public class PaymentService {

    // Reintentos de guardado ante choque de numero de intento con otra solicitud concurrente
    private static final int MAX_SAVE_RETRIES = 1;

    private final CardRules cardRules;
    private final CardFingerprintService fingerprintService;
    private final PaymentAttemptRepository repository;
    private final PaymentMapper mapper;
    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentService(CardRules cardRules, CardFingerprintService fingerprintService,
            PaymentAttemptRepository repository, PaymentMapper mapper, PaymentProperties properties, Clock clock) {
        this.cardRules = cardRules;
        this.fingerprintService = fingerprintService;
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public PaymentResponse process(PaymentRequest request) {
        cardRules.validate(request);

        var approved = repository.findFirstByReservationIdAndStatus(request.reservationId(), PaymentStatus.APPROVED);
        if (approved.isPresent()) {
            return mapper.toResponse(approved.get());
        }

        String fingerprint = fingerprintService.of(request.card().number());
        SimulationResult outcome = cardRules.simulate(request.card().number());

        for (int retries = 0;; retries++) {
            int attemptNumber = nextAttemptNumber(request.reservationId(), fingerprint);
            PaymentAttempt attempt = buildAttempt(request, fingerprint, attemptNumber, outcome);
            try {
                PaymentAttempt saved = repository.saveAndFlush(attempt);
                return toResult(saved);
            } catch (DataIntegrityViolationException e) {
                // Otra solicitud concurrente gano: si ya hay un APPROVED se devuelve ese
                var concurrentApproved = repository.findFirstByReservationIdAndStatus(request.reservationId(), PaymentStatus.APPROVED);
                if (concurrentApproved.isPresent()) {
                    return mapper.toResponse(concurrentApproved.get());
                }
                if (retries >= MAX_SAVE_RETRIES) {
                    throw e;
                }
            }
        }
    }

    private int nextAttemptNumber(String reservationId, String fingerprint) {
        return (int) repository.countByReservationIdAndCardFingerprint(reservationId, fingerprint) + 1;
    }

    private PaymentAttempt buildAttempt(PaymentRequest request, String fingerprint, int attemptNumber, SimulationResult outcome) {
        PaymentStatus status = switch (outcome) {
            case APPROVED -> PaymentStatus.APPROVED;
            case REJECTED -> PaymentStatus.REJECTED;
            case ERROR -> attemptNumber >= properties.maxErrorAttempts() ? PaymentStatus.CARD_FLAGGED : PaymentStatus.ERROR;
        };
        ErrorCode errorCode = switch (status) {
            case APPROVED -> null;
            case REJECTED -> ErrorCode.INSUFFICIENT_FUNDS;
            case ERROR -> ErrorCode.GATEWAY_ERROR;
            case CARD_FLAGGED -> ErrorCode.CARD_PROBLEM;
        };
        return new PaymentAttempt(UUID.randomUUID(), request.reservationId(), attemptNumber,
                CardBrand.from(request.card().brand()).orElseThrow(), cardRules.lastDigits(request.card().number()),
                fingerprint, request.amount(), status, errorCode == null ? null : errorCode.name(), Instant.now(clock));
    }

    private PaymentResponse toResult(PaymentAttempt saved) {
        if (saved.getStatus() == PaymentStatus.APPROVED) {
            return mapper.toResponse(saved);
        }
        throw new PaymentDeclinedException(ErrorCode.valueOf(saved.getErrorCode()));
    }
}
