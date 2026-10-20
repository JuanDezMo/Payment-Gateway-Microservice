package com.paymentgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.model.PaymentAttempt;
import com.paymentgateway.model.PaymentStatus;
import com.paymentgateway.repository.PaymentAttemptRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class PaymentApiIntegrationTest extends PostgresIntegrationTest {

    private static final String AUTH = "Bearer test-api-key";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentAttemptRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private String body(String reservation, String brand, String number) {
        String brandField = brand == null ? "" : "\"brand\":\"" + brand + "\",";
        return """
                {"reservationId":"%s","amount":50.00,"card":{%s"number":"%s","expiry":"12/30","cvv":"123"}}
                """.formatted(reservation, brandField, number);
    }

    private ResultActions pay(String reservation, String brand, String number) throws Exception {
        return pay(AUTH, body(reservation, brand, number));
    }

    private ResultActions pay(String authorization, String json) throws Exception {
        var request = post("/payments").contentType(MediaType.APPLICATION_JSON).content(json);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request);
    }

    @Test
    void approvedPayment() throws Exception {
        pay("r-ok", "VISA", "4111111111114242")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.code").doesNotExist());

        PaymentAttempt saved = repository.findAll().get(0);
        assertThat(saved.getCardLast4()).isEqualTo("4242");
        assertThat(saved.getCardFingerprint()).doesNotContain("4111111111114242");
    }

    @Test
    void rejectedPayment() throws Exception {
        pay("r-rej", "MASTERCARD", "4111111111110000")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        assertThat(repository.findAll()).extracting(PaymentAttempt::getStatus).containsExactly(PaymentStatus.REJECTED);
    }

    @Test
    void rejectedReservationCanBeRetriedAsNewAttempt() throws Exception {
        pay("r-retry", "VISA", "4111111111110000").andExpect(status().isPaymentRequired());
        pay("r-retry", "VISA", "4111111111114242").andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void threeErrorsFlagTheCardAndChangingCardRestartsTheCount() throws Exception {
        pay("r-err", "VISA", "4111111111119999").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("ERROR")).andExpect(jsonPath("$.code").value("GATEWAY_ERROR"))
                .andExpect(jsonPath("$.message").value("Error processing the payment, please try again"));
        pay("r-err", "VISA", "4111111111119999").andExpect(status().isBadGateway());
        pay("r-err", "VISA", "4111111111119999").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("CARD_FLAGGED")).andExpect(jsonPath("$.code").value("CARD_PROBLEM"))
                .andExpect(jsonPath("$.message").value("The card has problems, use another payment method"));

        pay("r-err", "VISA", "4111111111114242").andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findAll()).extracting(PaymentAttempt::getAttemptNumber).containsExactlyInAnyOrder(1, 2, 3, 1);
    }

    @Test
    void secondDifferentCardStartsAtAttemptOne() throws Exception {
        pay("r-two", "VISA", "4111111111119999").andExpect(status().isBadGateway());
        pay("r-two", "VISA", "4222222222229999").andExpect(status().isBadGateway());

        assertThat(repository.findAll()).extracting(PaymentAttempt::getAttemptNumber).containsExactly(1, 1);
    }

    @Test
    void invalidBrandOrMissingBrandIsRejectedAndNotSaved() throws Exception {
        pay("r-b", "DINERS", "4111111111114242").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Card brand not allowed. Use VISA, MASTERCARD or AMEX"));
        pay("r-b", null, "4111111111114242").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_ALLOWED"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidFormatsReturnInvalidRequest() throws Exception {
        pay("r-f", "VISA", "41111abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID")).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        pay(AUTH, "{\"reservationId\":\"r\",\"amount\":0,\"card\":{\"brand\":\"VISA\",\"number\":\"4111111111114242\",\"expiry\":\"12/30\",\"cvv\":\"123\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        pay(AUTH, "not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        pay(AUTH, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void numberWithoutLuhnAndBrandMismatchAreAccepted() throws Exception {
        pay("r-luhn", "AMEX", "1234567890123456").andExpect(status().isOk());
    }

    @Test
    void approvedPaymentIsIdempotent() throws Exception {
        String first = pay("r-idem", "VISA", "4111111111114242").andReturn().getResponse().getContentAsString();
        String second = pay("r-idem", "VISA", "4111111111114242").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("transactionId")).isEqualTo(objectMapper.readTree(first).get("transactionId"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentApprovedRequestsLeaveSingleApprovedAttempt() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return pay("r-conc", "VISA", "4111111111114242").andReturn();
            }));
        }
        ready.await();
        go.countDown();

        List<String> transactionIds = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            transactionIds.add(json.get("transactionId").asText());
        }
        executor.shutdown();

        assertThat(transactionIds).doesNotContainNull().containsOnly(transactionIds.get(0));
        assertThat(repository.findAll()).filteredOn(a -> a.getStatus() == PaymentStatus.APPROVED).hasSize(1);
    }

    @Test
    void concurrentErrorRequestsWithSameCardGetConsecutiveAttemptNumbers() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return pay("r-conc-err", "VISA", "4111111111119999").andReturn();
            }));
        }
        ready.await();
        go.countDown();

        for (Future<MvcResult> future : futures) {
            assertThat(future.get().getResponse().getStatus()).isEqualTo(502);
        }
        executor.shutdown();

        assertThat(repository.findAll()).extracting(PaymentAttempt::getAttemptNumber).containsExactlyInAnyOrder(1, 2);
        assertThat(repository.findAll()).extracting(PaymentAttempt::getStatus)
                .containsOnly(PaymentStatus.ERROR);
    }

    @Test
    void missingOrWrongApiKeyReturns401EvenWithInvalidBody() throws Exception {
        pay(null, body("r-a", "VISA", "4111111111114242")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED")).andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
        pay("Bearer wrong", body("r-a", "VISA", "4111111111114242")).andExpect(status().isUnauthorized());
        pay("Bearer wrong", "{\"amount\":-1}").andExpect(status().isUnauthorized());
        pay("test-api-key", body("r-a", "VISA", "4111111111114242")).andExpect(status().isUnauthorized());

        assertThat(repository.count()).isZero();
    }

    @Test
    void healthEndpointDoesNotRequireApiKey() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocumentListsPaymentsEndpointWithAllResponses() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['402']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/payments'].post.responses['502']").exists());
    }
}
