package com.paymentgateway.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.model.CardBrand;
import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void responseOnlyExposesStatusTransactionCodeAndMessage() {
        assertThat(Arrays.stream(PaymentResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("status", "transactionId", "code", "message");
    }

    @Test
    void approvedAttemptMapsWithoutCodeAndMessage() {
        UUID id = UUID.randomUUID();
        var attempt = new PaymentAttempt(id, "r", 1, CardBrand.VISA, "4242", "fp", BigDecimal.TEN, PaymentStatus.APPROVED, null, Instant.now());

        PaymentResponse response = mapper.toResponse(attempt);

        assertThat(response).isEqualTo(new PaymentResponse("APPROVED", id.toString(), null, null));
    }

    @Test
    void failedAttemptMapsCodeAndEnglishMessage() {
        var attempt = new PaymentAttempt(UUID.randomUUID(), "r", 1, CardBrand.VISA, "0000", "fp", BigDecimal.TEN,
                PaymentStatus.REJECTED, "INSUFFICIENT_FUNDS", Instant.now());

        PaymentResponse response = mapper.toResponse(attempt);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.code()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.message()).isEqualTo("Insufficient funds");
    }
}
