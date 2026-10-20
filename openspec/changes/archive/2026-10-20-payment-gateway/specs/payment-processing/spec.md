## Purpose

Define cómo la pasarela recibe una solicitud de pago, la valida y simula su resultado según el final de la tarjeta, incluyendo las reglas de reintento y las respuestas del contrato.

## ADDED Requirements

### Requirement: Solicitud de pago
El sistema SHALL exponer `POST /payments` que recibe `reservationId`, `amount` y `card` con `brand`, `number`, `expiry` y `cvv`.

#### Scenario: Solicitud con todos los campos
- **WHEN** un cliente autenticado envía una solicitud con todos los campos válidos
- **THEN** el sistema procesa el pago y responde con un `status` y un `transactionId` cuando corresponda

### Requirement: Validación de formato
El sistema MUST validar la solicitud antes de simular el pago y MUST responder `400` con `status` `INVALID` cuando incumpla alguna regla: `amount` mayor a 0, número de tarjeta de solo dígitos y de 13 a 19 de largo, `expiry` con formato `MM/YY` y no vencido, y `cvv` de 3 dígitos. El sistema MUST NOT aplicar validación Luhn.

#### Scenario: Monto inválido
- **WHEN** el `amount` es 0 o negativo
- **THEN** el sistema responde `400` con `code` `INVALID_REQUEST`

#### Scenario: Tarjeta vencida
- **WHEN** `expiry` corresponde a un mes ya vencido
- **THEN** el sistema responde `400` con `code` `INVALID_REQUEST`

#### Scenario: Número con caracteres no numéricos
- **WHEN** `number` contiene letras o su largo está fuera de 13 a 19
- **THEN** el sistema responde `400` con `code` `INVALID_REQUEST`

#### Scenario: Número que no cumple Luhn
- **WHEN** `number` tiene 16 dígitos pero no cumple Luhn y termina en un dígito común
- **THEN** el sistema no lo rechaza por formato y continúa con la simulación

### Requirement: Franquicia permitida
El sistema SHALL aceptar únicamente las franquicias `VISA`, `MASTERCARD` y `AMEX`. Cualquier otra, o su ausencia, MUST responder `400` con `code` `BRAND_NOT_ALLOWED` y el mensaje "Card brand not allowed. Use VISA, MASTERCARD or AMEX". El sistema MUST NOT verificar que el número corresponda a la franquicia.

#### Scenario: Franquicia fuera de la lista
- **WHEN** `brand` es `DINERS`
- **THEN** el sistema responde `400` con `code` `BRAND_NOT_ALLOWED`

#### Scenario: Franquicia ausente
- **WHEN** la solicitud no incluye `brand`
- **THEN** el sistema responde `400` con `code` `BRAND_NOT_ALLOWED`

#### Scenario: Franquicia no coincide con el número
- **WHEN** `brand` es `AMEX` y el número empieza por 4
- **THEN** el sistema no lo rechaza por franquicia

### Requirement: Resultado según el final de la tarjeta
El sistema SHALL decidir el resultado en memoria, sin consultar la base de datos: si el número termina en `0000` el pago es `REJECTED`, si termina en `9999` se simula un error y en cualquier otro caso es `APPROVED`.

#### Scenario: Tarjeta aprobada
- **WHEN** el número termina en `4242` y la solicitud es válida
- **THEN** el sistema responde `200` con `status` `APPROVED` y un `transactionId`

#### Scenario: Tarjeta rechazada
- **WHEN** el número termina en `0000`
- **THEN** el sistema responde `402` con `status` `REJECTED` y `code` `INSUFFICIENT_FUNDS`

### Requirement: Error simulado y límite de intentos
Cuando el número termina en `9999`, el sistema SHALL responder `502` con `status` `ERROR` y `code` `GATEWAY_ERROR` (mensaje "Error processing the payment, please try again") en los intentos 1 y 2 de la misma reserva y tarjeta. En el intento 3 SHALL responder `422` con `status` `CARD_FLAGGED`, `code` `CARD_PROBLEM` y el mensaje "The card has problems, use another payment method". El número de intento es el conteo de intentos previos guardados para la reserva y la tarjeta, más uno. Una tarjeta distinta MUST iniciar de nuevo en el intento 1.

#### Scenario: Primer intento con error
- **WHEN** es el primer intento de una reserva con una tarjeta que termina en `9999`
- **THEN** el sistema responde `502` con `status` `ERROR` y permite reintentar

#### Scenario: Segundo intento con error
- **WHEN** es el segundo intento con la misma tarjeta y reserva
- **THEN** el sistema responde `502` con `status` `ERROR`

#### Scenario: Tercer intento con la misma tarjeta
- **WHEN** es el tercer intento con la misma tarjeta y reserva
- **THEN** el sistema responde `422` con `status` `CARD_FLAGGED` y `code` `CARD_PROBLEM`

#### Scenario: Cambio de tarjeta tras errores
- **WHEN** tras dos errores el cliente envía otra tarjeta que no termina en `9999`
- **THEN** el sistema trata el envío como intento 1 de esa tarjeta y responde `APPROVED`

### Requirement: Idempotencia del pago aprobado
El sistema SHALL devolver el resultado ya guardado, sin crear un nuevo intento ni cobrar de nuevo, cuando ya existe un intento `APPROVED` para el `reservationId`.

#### Scenario: Reintento tras un pago aprobado
- **WHEN** llega otra solicitud con el mismo `reservationId` que ya tiene un pago `APPROVED`
- **THEN** el sistema responde `200` con `status` `APPROVED` y el mismo `transactionId`, y no crea un intento nuevo

#### Scenario: Reintento tras un rechazo
- **WHEN** llega otra solicitud con el mismo `reservationId` cuyo único intento fue `REJECTED`
- **THEN** el sistema procesa la solicitud como un intento nuevo

### Requirement: Mensajes en inglés
El sistema SHALL devolver todos los mensajes de respuesta de la API en inglés, definidos en un único catálogo de códigos de error.

#### Scenario: Mensaje de rechazo
- **WHEN** el pago termina en `REJECTED`
- **THEN** el cuerpo incluye `code` `INSUFFICIENT_FUNDS` y `message` "Insufficient funds"

#### Scenario: Mensaje de formato inválido
- **WHEN** la solicitud tiene formato inválido
- **THEN** el cuerpo incluye `code` `INVALID_REQUEST` y un `message` en inglés
