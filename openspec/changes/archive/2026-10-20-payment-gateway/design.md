## Context

Microservicio nuevo e independiente, con repositorio propio, que consume `ticket-api` mediante HTTP. Motivación y alcance en `proposal.md`. Stack acordado: Java 21, Spring Boot 3.5.x, Gradle (Kotlin DSL) con wrapper, PostgreSQL 16, Docker y docker-compose. El contrato de origen es la sección 8 de `docs/business-rules.md` del repo `ticket_backend_api`.

## Goals / Non-Goals

**Goals:**
- El resultado del pago se decide sin leer la BD (excepto idempotencia y conteo de intentos).
- Trazabilidad completa de intentos sin almacenar datos sensibles de la tarjeta.
- Contrato claro, publicado como OpenAPI.
- Arranque con un solo `docker compose up`.

**Non-Goals:**
- Reembolsos, token con expiración y emisión de token, bloqueo global por tarjeta, Luhn, validación de prefijo por franquicia, alta disponibilidad.

## Decisions

**Idioma.** Código, nombres, mensajes de respuesta de la API, logs y tests en inglés. Los documentos (OpenSpec, README, `docs/`) y los comentarios necesarios en el código, en español.

**Paquetes por capa (MVC de API, sin vista)**
```
com.<org>.paymentgateway
  controller/   PaymentController
  dto/          PaymentRequest, CardData, PaymentResponse   (records)
  service/      PaymentService, CardRules, CardFingerprintService
  repository/   PaymentAttemptRepository                    (Spring Data JPA)
  model/        PaymentAttempt (entidad), CardBrand, PaymentStatus, SimulationResult
  mapper/       PaymentMapper                               (MapStruct)
  security/     ApiKeyFilter, SecurityConfig
  exception/    ErrorCode, PaymentExceptionHandler, excepciones propias
  config/       ClockConfig, propiedades
```
Dirección de dependencias: `controller -> service -> repository`. El controller solo ve DTOs; el servicio ve DTOs y entidades; el repository solo entidades. El servicio no conoce HTTP (`ResponseEntity`, servlet); el mapeo a códigos HTTP vive solo en el controller y en `PaymentExceptionHandler`. Todo se conecta por inyección por constructor.

`CardRules` es un `@Component` sin acceso a BD, con `Clock` inyectado. Expone `validate(request)` (lanza excepción propia si el formato, la franquicia o el vencimiento son inválidos) y `simulate(number)` (devuelve `SimulationResult`: `APPROVED`, `REJECTED` o `ERROR` según el sufijo). `CARD_FLAGGED` lo decide `PaymentService`, porque depende del historial en BD.

**Constantes.**
- Estados: enums en `model` (`PaymentStatus` para lo que se guarda y devuelve; `SimulationResult` aparte para el resultado de `simulate`, que no incluye `CARD_FLAGGED`).
- Códigos y mensajes de error: enum `exception/ErrorCode` (código y mensaje en inglés), usado por servicio, handler y filtro de seguridad.
- Sufijos `0000`/`9999` y largo del número: constantes privadas de `CardRules`.
- Máximo de intentos con error (3): `payment.max-error-attempts` en `application.yml`, leído con `@ConfigurationProperties`.
- No hay cadenas ni números literales de negocio en `service` ni en `controller`. Mensajes sin internacionalizar en el MVP.

Mensajes de `ErrorCode`: `INVALID_REQUEST` "Invalid request", `BRAND_NOT_ALLOWED` "Card brand not allowed. Use VISA, MASTERCARD or AMEX", `INSUFFICIENT_FUNDS` "Insufficient funds", `GATEWAY_ERROR` "Error processing the payment, please try again", `CARD_PROBLEM` "The card has problems, use another payment method", `INVALID_API_KEY` "Invalid or missing API key".

**Flujo de `PaymentService`**
```
ApiKeyFilter -> Controller (@Valid) -> PaymentService:
  1. cardRules.validate(request)                    (400 si falla, no se guarda)
  2. si existsApproved(reservationId) -> devolver el guardado
  3. fingerprint = fingerprintService.of(number)
  4. attempt = repository.count(reservationId, fingerprint) + 1
  5. outcome = cardRules.simulate(number)           (en memoria)
  6. si outcome == ERROR y attempt >= maxErrorAttempts -> CARD_FLAGGED
  7. repository.save(intento) -> responder
```
Alternativa descartada: decidir leyendo estado de BD, porque contradice el requisito de decidir en memoria y acopla el resultado a la persistencia. Alternativa descartada: puertos y adaptadores; se prefiere la estructura por capas por simplicidad y mantenibilidad. Aplicación de SOLID acotada: el servicio orquesta y las reglas y el HMAC están en clases propias (S); los repositories exponen pocas consultas (I); inyección por constructor (D); añadir un sufijo nuevo exige editar `CardRules` (O, aceptado en el MVP).

**ArchUnit** (`archunit-junit5`, clase `ArchitectureTest`) con `allowEmptyShould(true)` para activarlo desde el inicio. Reglas: (1) arquitectura por capas: nadie usa `controller`, solo `controller` usa `service`, solo `service` usa `repository`; (2) `controller` no depende de `repository` ni `model`; (3) `service` no depende de `controller` ni de clases HTTP; (4) `model` no depende de `service`, `controller`, `repository` ni `dto`; (5) `dto` no depende de `model` ni `repository`; (6) sin ciclos entre paquetes; (7) sin `@Autowired` en campos; (8) convención de nombres `*Controller`, `*Service` (y `CardRules`), `*Repository`.

**Fingerprint con HMAC-SHA256** y clave en `CARD_FINGERPRINT_SECRET`. Alternativa: SHA-256 simple, descartada porque el espacio de números de tarjeta permite fuerza bruta. Se usa `javax.crypto`, sin librería adicional.

**Unicidad en datos.** Un índice único parcial sobre `reservation_id` donde `status = 'APPROVED'` garantiza un solo pago aprobado por reserva. Si dos solicitudes concurrentes chocan, la segunda captura la violación, relee el aprobado y lo devuelve. Un índice único sobre `(reservation_id, card_fingerprint, attempt_number)` evita números de intento duplicados.

**Migraciones con Flyway** y Hibernate en `ddl-auto=validate`. Alternativa: `ddl-auto=update`, descartada por no ser reproducible.

**Seguridad.** `ApiKeyFilter` (`OncePerRequestFilter`) se ejecuta antes de la validación del cuerpo. Compara con `MessageDigest.isEqual`. `/actuator/health` queda fuera del filtro. Respuestas `401` con el cuerpo del contrato y `ErrorCode.INVALID_API_KEY`.

**Mapeo con MapStruct** (`componentModel = "spring"`) entre DTO, entidad y respuesta. Sin Lombok: DTOs como `record` y entidades escritas a mano.

**Errores uniformes.** Un `@RestControllerAdvice` (`PaymentExceptionHandler`) convierte las excepciones en `{ status, code, message }`. Mapeo HTTP del contrato: `APPROVED` 200, `REJECTED` 402, `ERROR` 502 (reintentable), `CARD_FLAGGED` 422 (no reintentable con esa tarjeta), formato o franquicia 400 y API key 401.

**Contrato.** `springdoc-openapi` publica el OpenAPI; es el artefacto que consume `ticket-api` para su mock.

**Pruebas.** Unitarias con JUnit 5, AssertJ y Mockito (`CardRules`, `CardFingerprintService`, `PaymentService`); web con `@WebMvcTest`, MockMvc y `spring-security-test`; datos e integración con `@SpringBootTest` y Testcontainers PostgreSQL (`@ServiceConnection`), no H2, para ejercitar los índices parciales; concurrencia con `ExecutorService` y `CountDownLatch`; arquitectura con ArchUnit; cobertura opcional con JaCoCo.

**Docker.** Dockerfile multi-stage (`gradle:8-jdk21` y `eclipse-temurin:21-jre`, usuario no root). `docker-compose.yml` con `postgres:16` (healthcheck `pg_isready`, volumen) y el servicio con `depends_on: condition: service_healthy`. Variables en `.env`, con `.env.example` versionado.

**Montos.** `amount` se recibe como número positivo y se guarda como `NUMERIC(12,2)` / `BigDecimal`.

## Risks / Trade-offs

- [Carrera entre el conteo de intentos y el guardado] → el índice único de `attempt_number` detecta el choque; el servicio reintenta una vez.
- [API key estática, sin rotación ni expiración] → aceptado para el MVP; se documenta la etapa futura (token con expiración, rotación, restricción por IP).
- [El `card_fingerprint` es estable por tarjeta] → es dato derivado; se protege con la clave secreta y no se expone en las respuestas.
- [`ERROR` responde `502`, no `200`] → el cliente debe tratar el `502` como reintentable; queda reflejado en el contrato.
- [`CARD_FLAGGED` no bloquea la tarjeta en otras reservas] → decisión de MVP.

## Migration Plan

Proyecto nuevo, sin migración de datos. Despliegue: `docker compose up`; Flyway crea el esquema al iniciar. Reversión: bajar el contenedor y eliminar el volumen en entornos de desarrollo.
