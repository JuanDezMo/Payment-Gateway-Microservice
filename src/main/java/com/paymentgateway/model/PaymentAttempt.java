package com.paymentgateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private String reservationId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand", nullable = false, updatable = false)
    private CardBrand cardBrand;

    @Column(name = "card_last4", nullable = false, updatable = false, length = 4)
    private String cardLast4;

    @Column(name = "card_fingerprint", nullable = false, updatable = false)
    private String cardFingerprint;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentStatus status;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID transactionId, String reservationId, int attemptNumber, CardBrand cardBrand,
            String cardLast4, String cardFingerprint, BigDecimal amount, PaymentStatus status, String errorCode,
            Instant createdAt) {
        this.transactionId = transactionId;
        this.reservationId = reservationId;
        this.attemptNumber = attemptNumber;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.cardFingerprint = cardFingerprint;
        this.amount = amount;
        this.status = status;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public String getReservationId() { return reservationId; }
    public int getAttemptNumber() { return attemptNumber; }
    public CardBrand getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getCardFingerprint() { return cardFingerprint; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
}
