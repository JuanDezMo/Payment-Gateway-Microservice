package com.paymentgateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentgateway.config.PaymentProperties;
import com.paymentgateway.dto.CardData;
import com.paymentgateway.dto.PaymentRequest;
import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.exception.ErrorCode;
import com.paymentgateway.exception.PaymentDeclinedException;
import com.paymentgateway.mapper.PaymentMapper;
import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import com.paymentgateway.repository.PaymentAttemptRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentAttemptRepository repository;

    PaymentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
        var properties = new PaymentProperties(3, "key", "secret");
        service = new PaymentService(new CardRules(clock), new CardFingerprintService(properties), repository,
                Mappers.getMapper(PaymentMapper.class), properties, clock);
        lenient().when(repository.saveAndFlush(any(PaymentAttempt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PaymentRequest request(String number) {
        return new PaymentRequest("res-1", new BigDecimal("25.50"), new CardData("VISA", number, "12/30", "123"));
    }

    private PaymentAttempt savedAttempt() {
        ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void approvedCardIsSavedAsApproved() {
        PaymentResponse response = service.process(request("4111111111114242"));

        PaymentAttempt saved = savedAttempt();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(saved.getAttemptNumber()).isEqualTo(1);
        assertThat(saved.getCardLast4()).isEqualTo("4242");
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.transactionId()).isEqualTo(saved.getTransactionId().toString());
        assertThat(response.code()).isNull();
    }

    @Test
    void rejectedCardIsSavedAsRejected() {
        assertThatThrownBy(() -> service.process(request("4111111111110000")))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        assertThat(savedAttempt().getStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void firstErrorIsSavedAsErrorWithAttemptNumberOne() {
        assertThatThrownBy(() -> service.process(request("4111111111119999")))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GATEWAY_ERROR));

        PaymentAttempt saved = savedAttempt();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(saved.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void thirdErrorWithSameCardIsFlagged() {
        when(repository.countByReservationIdAndCardFingerprint(anyString(), anyString())).thenReturn(2L);

        assertThatThrownBy(() -> service.process(request("4111111111119999")))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CARD_PROBLEM));

        PaymentAttempt saved = savedAttempt();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CARD_FLAGGED);
        assertThat(saved.getAttemptNumber()).isEqualTo(3);
    }

    @Test
    void existingApprovedAttemptIsReturnedWithoutSavingAnother() {
        PaymentAttempt approved = new PaymentAttempt(java.util.UUID.randomUUID(), "res-1", 1,
                com.paymentgateway.model.CardBrand.VISA, "4242", "fp", new BigDecimal("25.50"), PaymentStatus.APPROVED,
                null, Instant.now());
        when(repository.findFirstByReservationIdAndStatus("res-1", PaymentStatus.APPROVED)).thenReturn(Optional.of(approved));

        PaymentResponse response = service.process(request("4111111111119999"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.transactionId()).isEqualTo(approved.getTransactionId().toString());
        verify(repository, never()).saveAndFlush(any());
    }
}
