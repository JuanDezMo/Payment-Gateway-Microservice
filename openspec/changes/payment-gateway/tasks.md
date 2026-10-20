## 1. Proyecto base

- [x] 1.1 Crear el proyecto Gradle (Kotlin DSL, wrapper, Java 21, Spring Boot 3.5.x) con los paquetes `controller`, `dto`, `service`, `repository`, `model`, `mapper`, `security`, `exception` y `config`; verificar con `./gradlew build` sin errores
- [x] 1.2 Agregar dependencias (web, validation, data-jpa, postgresql, flyway, security, actuator, springdoc, MapStruct y su procesador, y las de prueba con Testcontainers); verificar que `./gradlew dependencies` resuelve sin conflictos
- [x] 1.3 Configurar `application.yml` con variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `PAYMENT_GATEWAY_API_KEY`, `CARD_FINGERPRINT_SECRET`), la propiedad `payment.max-error-attempts` (3) con `@ConfigurationProperties` y `ddl-auto=validate`; verificar que la app falla al arrancar si falta alguna variable obligatoria
- [x] 1.4 Agregar `archunit-junit5` y `ArchitectureTest` con las 8 reglas del diseño (capas, dependencias prohibidas, sin ciclos, sin `@Autowired` en campos, nombres) y `allowEmptyShould(true)`; verificar que `./gradlew test` pasa en el proyecto vacío y que falla al introducir un import prohibido de prueba

## 2. Docker

- [x] 2.1 Crear el Dockerfile multi-stage con usuario no root; verificar con `docker build` que la imagen se construye
- [x] 2.2 Crear `docker-compose.yml` con `postgres:16` (healthcheck y volumen) y el servicio con `depends_on: service_healthy`, más `.env.example`; verificar que `docker compose up` deja ambos servicios sanos
- [x] 2.3 Verificar que `/actuator/health` responde `UP` desde el contenedor sin API key

## 3. Base de datos

- [x] 3.1 Crear la migración Flyway de `payment_attempts` con los campos del diseño, incluida `card_brand`; verificar que la migración se aplica en un PostgreSQL de Testcontainers
- [x] 3.2 Agregar el índice único parcial de un solo `APPROVED` por `reservation_id` y el índice único `(reservation_id, card_fingerprint, attempt_number)`; verificar con tests de integración que un segundo `APPROVED` y un número de intento duplicado fallan
- [x] 3.3 Crear la entidad `model/PaymentAttempt` y `repository/PaymentAttemptRepository` (existe `APPROVED` por reserva, conteo por reserva y fingerprint); verificar con tests de repositorio

## 4. Modelo y reglas

- [x] 4.1 Crear los enums `CardBrand`, `PaymentStatus` y `SimulationResult` en `model`, y `ErrorCode` en `exception` con código y mensaje en inglés (`INVALID_REQUEST`, `BRAND_NOT_ALLOWED`, `INSUFFICIENT_FUNDS`, `GATEWAY_ERROR`, `CARD_PROBLEM`, `INVALID_API_KEY`); verificar con test unitario que cada código tiene su mensaje y que una marca fuera de la lista no se reconoce
- [x] 4.2 Implementar `service/CardRules.validate` con formato del número (solo dígitos, 13 a 19, sin Luhn), vencimiento `MM/YY` con `Clock` inyectado y CVV de 3 dígitos; verificar con tests unitarios de los escenarios de la spec, incluido un número sin Luhn que se acepta
- [x] 4.3 Implementar `CardRules.simulate` por sufijo (`0000` rechazo, `9999` error, otro aprobado) con constantes privadas; verificar con tests unitarios parametrizados por cada sufijo
- [x] 4.4 Implementar `service/CardFingerprintService` con HMAC-SHA256; verificar que el mismo número da el mismo valor, que números distintos dan valores distintos y que el valor no contiene el número

## 5. Servicio y API

- [x] 5.1 Crear los DTOs (`record`) de solicitud y respuesta y el `PaymentMapper` con MapStruct; verificar que el mapeo no expone número completo, CVV ni vencimiento
- [x] 5.2 Implementar `POST /payments` en `PaymentController` con validación de bean y de franquicia; verificar que `brand` fuera de la lista o ausente devuelve `400` `BRAND_NOT_ALLOWED` con el mensaje en inglés del `ErrorCode` y que no se guarda ningún intento
- [x] 5.3 Implementar `PaymentService` con el flujo (validar, idempotencia, conteo, simular, guardar); verificar con tests de servicio con Mockito que `APPROVED` y `REJECTED` se guardan con su estado
- [x] 5.4 Implementar la regla de `9999` en el servicio: intentos 1 y 2 devuelven `ERROR` (502) y el tercero convierte el resultado en `CARD_FLAGGED` (422) usando `payment.max-error-attempts`; verificar con test de integración los tres intentos y el cambio de tarjeta que reinicia el conteo
- [x] 5.5 Implementar la idempotencia de `APPROVED`, incluido el choque concurrente que relee el aprobado; verificar con test de integración con dos solicitudes simultáneas que queda un solo `APPROVED` y ambas devuelven el mismo `transactionId`
- [x] 5.6 Implementar `PaymentExceptionHandler` con el formato `{ status, code, message }` y el mapeo único a códigos HTTP (200, 400, 402, 422, 502); verificar que cada error del contrato devuelve su código HTTP y cuerpo, con todos los mensajes en inglés y sin literales de negocio en `service` ni `controller`

## 6. Seguridad

- [x] 6.1 Implementar `ApiKeyFilter` con comparación en tiempo constante, antes de la validación del cuerpo y con `/actuator/health` excluido; verificar con tests que sin header o con key incorrecta responde `401` `INVALID_API_KEY`, incluso con cuerpo inválido
- [x] 6.2 Verificar que la API key no aparece en logs ni en respuestas en un rechazo `401`, revisando la salida del log en un test

## 7. Contrato y verificación final

- [x] 7.1 Publicar el OpenAPI con springdoc con ejemplos de cada respuesta; verificar que `/v3/api-docs` lista `POST /payments` con las respuestas 200, 400, 401, 402, 422 y 502
- [x] 7.2 Escribir un test de integración de extremo a extremo con Testcontainers que cubra aprobado, rechazado, error con tres intentos, franquicia inválida, 401 e idempotencia; verificar que `./gradlew test` pasa completo, incluido `ArchitectureTest`
- [x] 7.3 Escribir el `README` en español con cómo levantar el servicio, las variables de entorno, el contrato y las tarjetas de prueba (`0000`, `9999`, otras); verificar siguiendo el README desde cero hasta una llamada `curl` exitosa
