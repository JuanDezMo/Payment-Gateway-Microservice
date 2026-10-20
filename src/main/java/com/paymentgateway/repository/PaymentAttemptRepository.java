package com.paymentgateway.repository;

import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findFirstByReservationIdAndStatus(String reservationId, PaymentStatus status);

    boolean existsByReservationIdAndStatus(String reservationId, PaymentStatus status);

    long countByReservationIdAndCardFingerprint(String reservationId, String cardFingerprint);
}
