package com.paymentgateway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentgateway.PostgresIntegrationTest;
import com.paymentgateway.model.CardBrand;
import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentAttemptRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    PaymentAttemptRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private PaymentAttempt attempt(String reservationId, String fingerprint, int number, PaymentStatus status) {
        return new PaymentAttempt(UUID.randomUUID(), reservationId, number, CardBrand.VISA, "4242", fingerprint,
                new BigDecimal("10.00"), status, null, Instant.now());
    }

    @Test
    void migrationIsAppliedAndAttemptCanBePersisted() {
        PaymentAttempt saved = repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.REJECTED));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    void secondApprovedAttemptForSameReservationFails() {
        repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.APPROVED));

        assertThatThrownBy(() -> repository.saveAndFlush(attempt("r-1", "fp-b", 1, PaymentStatus.APPROVED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvedAttemptsOnDifferentReservationsAreAllowed() {
        repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.APPROVED));
        repository.saveAndFlush(attempt("r-2", "fp-a", 1, PaymentStatus.APPROVED));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void duplicatedAttemptNumberForSameReservationAndCardFails() {
        repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.ERROR));

        assertThatThrownBy(() -> repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.ERROR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameAttemptNumberWithAnotherCardIsAllowed() {
        repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.ERROR));
        repository.saveAndFlush(attempt("r-1", "fp-b", 1, PaymentStatus.ERROR));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void existsApprovedAndCountQueriesWork() {
        repository.saveAndFlush(attempt("r-1", "fp-a", 1, PaymentStatus.ERROR));
        repository.saveAndFlush(attempt("r-1", "fp-a", 2, PaymentStatus.ERROR));
        repository.saveAndFlush(attempt("r-1", "fp-b", 1, PaymentStatus.REJECTED));

        assertThat(repository.existsByReservationIdAndStatus("r-1", PaymentStatus.APPROVED)).isFalse();
        assertThat(repository.countByReservationIdAndCardFingerprint("r-1", "fp-a")).isEqualTo(2);
        assertThat(repository.countByReservationIdAndCardFingerprint("r-1", "fp-b")).isEqualTo(1);

        repository.saveAndFlush(attempt("r-1", "fp-c", 1, PaymentStatus.APPROVED));
        assertThat(repository.existsByReservationIdAndStatus("r-1", PaymentStatus.APPROVED)).isTrue();
        assertThat(repository.findFirstByReservationIdAndStatus("r-1", PaymentStatus.APPROVED)).isPresent();
    }
}
