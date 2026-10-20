## Why

La plataforma de tickets (`ticket-api`) necesita cobrar la compra de tickets a través de una pasarela de pagos externa. Para el MVP se requiere un microservicio mínimo que simule el cobro, aplique validaciones básicas, guarde la trazabilidad de cada intento y sea consumible de forma segura.

## What Changes

- Nuevo microservicio `payment-gateway` (Java 21, Spring Boot, Gradle, PostgreSQL, Docker y docker-compose).
- Endpoint `POST /payments` que recibe `reservationId`, `amount` y los datos de la tarjeta (`brand`, `number`, `expiry`, `cvv`).
- Validaciones de formato: franquicia permitida (`VISA`, `MASTERCARD`, `AMEX`), número de 13 a 19 dígitos (sin Luhn), vencimiento `MM/YY` no vencido, CVV de 3 dígitos y `amount > 0`.
- Simulación del resultado según el final del número de tarjeta: `0000` rechazo, `9999` error simulado y cualquier otro aprobado. La decisión se toma en memoria, sin depender de la BD.
- Conteo de intentos por reserva y tarjeta. Los intentos 1 y 2 con error permiten reintentar y el tercero marca la tarjeta como `CARD_FLAGGED`.
- Persistencia de cada intento válido en `payment_attempts`, con `card_fingerprint` (HMAC) y sin guardar número completo, CVV ni vencimiento.
- Idempotencia: si la reserva ya tiene un intento `APPROVED`, se devuelve ese resultado sin cobrar de nuevo.
- Autenticación servidor a servidor con API key estática (`Authorization: Bearer <api-key>`).
- Publicación del contrato como OpenAPI.

## Capabilities

### New Capabilities
- `payment-processing`: recepción y validación del pago, simulación del resultado por final de tarjeta, reglas de reintento (`ERROR` y `CARD_FLAGGED`), idempotencia y respuestas del contrato.
- `payment-attempt-history`: persistencia de cada intento válido en `payment_attempts`, cálculo del número de intento y protección de datos sensibles de la tarjeta.
- `service-authentication`: validación de la API key en cada request a `/payments`.

### Modified Capabilities

Ninguna. Es un proyecto nuevo.

## Impact

- Código nuevo: todo el proyecto `payment-gateway-microservice-project`.
- Sistemas: base de datos PostgreSQL propia y contenedores Docker (servicio y BD).
- Contrato: lo consume `ticket-api` (cambio `ticket-purchase`), que probará contra un mock del mismo contrato.
- Variables de entorno: `PAYMENT_GATEWAY_API_KEY`, `CARD_FINGERPRINT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- Fuera de alcance: reembolsos, token con expiración y endpoint de emisión de token (iteración futura), bloqueo global por tarjeta, validación Luhn y validación de prefijo por franquicia.
