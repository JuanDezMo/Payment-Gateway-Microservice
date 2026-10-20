# Payment Gateway

Microservicio que simula una pasarela de pagos para `ticket-api`. Valida la solicitud, decide el resultado según el final de la tarjeta, guarda cada intento en PostgreSQL y se consume de forma segura con una API key.

Stack: Java 21, Spring Boot 3.5, Gradle, PostgreSQL 16, Flyway, Docker.

## Cómo levantar el servicio

Requisitos: Docker con Docker Compose.

```bash
cp .env.example .env
docker compose up -d --build
```

Cuando `docker compose ps` muestre ambos servicios como `healthy`, el servicio escucha en `http://localhost:8080`. Flyway crea el esquema al iniciar.

Para bajarlo: `docker compose down` (agrega `-v` para borrar también el volumen de la base de datos).

> `.env` no se versiona. Los valores de `.env.example` son solo para desarrollo: cámbialos en cualquier otro entorno.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `DB_NAME` | Nombre de la base de datos (solo la usa docker-compose) |
| `DB_USER` | Usuario de PostgreSQL |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `PAYMENT_GATEWAY_API_KEY` | API key que deben enviar los clientes |
| `CARD_FINGERPRINT_SECRET` | Clave secreta del HMAC-SHA256 del `card_fingerprint` |

Para ejecutar sin Docker, el servicio también necesita `DB_URL` (por ejemplo `jdbc:postgresql://localhost:5432/payment_gateway`). Si falta alguna variable obligatoria, la aplicación no arranca.

El máximo de errores por reserva y tarjeta se configura en `payment.max-error-attempts` (por defecto 3).

## Contrato

### `POST /payments`

Header obligatorio: `Authorization: Bearer <PAYMENT_GATEWAY_API_KEY>`

```json
{
  "reservationId": "res-123",
  "amount": 50.00,
  "card": {
    "brand": "VISA",
    "number": "4111111111114242",
    "expiry": "12/30",
    "cvv": "123"
  }
}
```

Reglas de validación:
- `brand`: `VISA`, `MASTERCARD` o `AMEX`. No se comprueba que coincida con el número.
- `number`: solo dígitos, de 13 a 19. No se valida Luhn.
- `expiry`: formato `MM/YY` y no vencido (vale hasta el fin del mes indicado).
- `cvv`: 3 dígitos.
- `amount`: mayor que 0.

### Respuestas

| HTTP | `status` | `code` | Cuándo |
|---|---|---|---|
| 200 | `APPROVED` | — | Pago aprobado (incluye `transactionId`) |
| 400 | `INVALID` | `INVALID_REQUEST` | Formato inválido |
| 400 | `INVALID` | `BRAND_NOT_ALLOWED` | Franquicia fuera de la lista o ausente |
| 401 | `UNAUTHORIZED` | `INVALID_API_KEY` | API key ausente o incorrecta |
| 402 | `REJECTED` | `INSUFFICIENT_FUNDS` | Tarjeta que termina en `0000` |
| 422 | `CARD_FLAGGED` | `CARD_PROBLEM` | Tercer error con la misma tarjeta y reserva |
| 502 | `ERROR` | `GATEWAY_ERROR` | Tarjeta que termina en `9999` (reintentable) |

Las respuestas que no son 200 tienen el formato `{ "status", "code", "message" }`, con el mensaje en inglés.

Comportamientos a tener en cuenta:
- **Idempotencia:** si la reserva ya tiene un pago `APPROVED`, se devuelve ese mismo resultado y `transactionId` sin crear un intento nuevo.
- **Reintentos:** un `502` se puede reintentar. En el tercer error con la misma tarjeta y reserva se responde `422`. Con otra tarjeta, el conteo empieza de nuevo.
- **Datos sensibles:** nunca se guarda el número completo, el CVV ni el vencimiento; solo `card_last4` y un `card_fingerprint` (HMAC).

### OpenAPI

Con el servicio levantado: `http://localhost:8080/v3/api-docs` (JSON) y `http://localhost:8080/swagger-ui.html`. Este es el contrato que consume `ticket-api` para su mock.

### Salud

`GET /actuator/health` no requiere API key.

## Tarjetas de prueba

El resultado se decide por el final del número:

| Termina en | Resultado |
|---|---|
| `0000` | `REJECTED` (402) |
| `9999` | `ERROR` (502) en los intentos 1 y 2; `CARD_FLAGGED` (422) en el 3 |
| cualquier otro | `APPROVED` (200) |

Ejemplo con `curl` (usa la API key de tu `.env`):

```bash
curl -i -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer change-me-api-key" \
  -d '{"reservationId":"res-123","amount":50.00,"card":{"brand":"VISA","number":"4111111111114242","expiry":"12/30","cvv":"123"}}'
```

Respuesta esperada: `200` con `{"status":"APPROVED","transactionId":"..."}`.

## Pruebas

```bash
./gradlew test
```

Requiere Docker: las pruebas de integración usan PostgreSQL real con Testcontainers. Incluye `ArchitectureTest` (ArchUnit), que verifica las reglas de capas del proyecto.

## Estructura

```
com.paymentgateway
  controller/  endpoints REST
  dto/         records de solicitud y respuesta
  service/     PaymentService, CardRules, CardFingerprintService
  repository/  Spring Data JPA
  model/       entidad y enums
  mapper/      MapStruct
  security/    ApiKeyFilter, SecurityConfig
  exception/   ErrorCode y manejo de errores
  config/      propiedades, Clock, OpenAPI
```

Dependencias: `controller -> service -> repository`.
