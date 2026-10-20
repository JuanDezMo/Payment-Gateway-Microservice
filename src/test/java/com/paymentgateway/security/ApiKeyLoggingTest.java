package com.paymentgateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.paymentgateway.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiKeyLoggingTest extends PostgresIntegrationTest {

    private static final String SENT_KEY = "super-secret-sent-key";

    @Autowired
    MockMvc mvc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void attach() {
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void rejectionLogsDoNotContainSentOrConfiguredKey() throws Exception {
        String responseBody = mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SENT_KEY))
                .andReturn().getResponse().getContentAsString();

        assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("invalid or missing API key"));
        assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().contains(SENT_KEY)
                || e.getFormattedMessage().contains("test-api-key"));
        assertThat(responseBody).doesNotContain(SENT_KEY).doesNotContain("test-api-key");
    }
}
