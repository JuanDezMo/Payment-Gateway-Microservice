CREATE TABLE payment_attempts (
    id               BIGSERIAL     PRIMARY KEY,
    transaction_id   UUID          NOT NULL UNIQUE,
    reservation_id   VARCHAR(64)   NOT NULL,
    attempt_number   INTEGER       NOT NULL CHECK (attempt_number >= 1),
    card_brand       VARCHAR(16)   NOT NULL,
    card_last4       VARCHAR(4)      NOT NULL,
    card_fingerprint VARCHAR(64)   NOT NULL,
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    status           VARCHAR(16)   NOT NULL,
    error_code       VARCHAR(32),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Un solo pago aprobado por reserva, incluso con solicitudes concurrentes
CREATE UNIQUE INDEX uq_payment_attempts_approved_per_reservation
    ON payment_attempts (reservation_id)
    WHERE status = 'APPROVED';

-- Evita numeros de intento duplicados para la misma reserva y tarjeta
CREATE UNIQUE INDEX uq_payment_attempts_reservation_card_attempt
    ON payment_attempts (reservation_id, card_fingerprint, attempt_number);
