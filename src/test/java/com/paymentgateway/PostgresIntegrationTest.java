package com.paymentgateway;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Base de pruebas de integracion con PostgreSQL real (no H2) para ejercitar los indices parciales. */
// Sin application.yml de test: se usa el de main para que ddl-auto=validate tambien corra en las pruebas
@SpringBootTest(properties = {"payment.api-key=test-api-key", "payment.card-fingerprint-secret=test-fingerprint-secret"})
@Testcontainers
public abstract class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
}
