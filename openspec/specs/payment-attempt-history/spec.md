## Purpose

Garantiza la trazabilidad de cada intento de cobro y la protección de los datos sensibles de la tarjeta, sin condicionar el resultado del pago.

## Requirements

### Requirement: Registro de cada intento
El sistema SHALL guardar cada intento con formato válido, sea cual sea su resultado (`APPROVED`, `REJECTED`, `ERROR` o `CARD_FLAGGED`), con `reservationId`, número de intento, franquicia, últimos 4 dígitos, `amount`, estado, código de error y fecha de creación. Las solicitudes con formato inválido o franquicia no permitida MUST NOT guardarse.

#### Scenario: Intento rechazado
- **WHEN** una solicitud válida termina en `REJECTED`
- **THEN** el sistema guarda un intento con estado `REJECTED`

#### Scenario: Intento con error simulado
- **WHEN** una solicitud válida termina en `ERROR`
- **THEN** el sistema guarda el intento con estado `ERROR` y su número de intento

#### Scenario: Solicitud inválida
- **WHEN** una solicitud responde `400`
- **THEN** el sistema no guarda ningún intento

### Requirement: Protección de datos de tarjeta
El sistema MUST NOT guardar el número completo, el CVV ni la fecha de vencimiento, y MUST NOT incluirlos en logs. SHALL guardar los últimos 4 dígitos y un `card_fingerprint` derivado del número con una clave secreta, que no permite recuperar el número.

#### Scenario: Datos persistidos
- **WHEN** se guarda un intento
- **THEN** el registro contiene `card_last4` y `card_fingerprint`, y no contiene número completo, CVV ni vencimiento

#### Scenario: Misma tarjeta, mismo fingerprint
- **WHEN** dos intentos usan el mismo número de tarjeta
- **THEN** ambos registros tienen el mismo `card_fingerprint`

### Requirement: Número de intento por reserva y tarjeta
El sistema SHALL asignar a cada intento el número igual a los intentos previos del mismo `reservationId` y `card_fingerprint` más uno.

#### Scenario: Primer intento de una tarjeta
- **WHEN** no existen intentos previos de esa reserva con esa tarjeta
- **THEN** el intento se guarda con número 1

#### Scenario: Otra tarjeta en la misma reserva
- **WHEN** la reserva ya tiene dos intentos con la tarjeta A y llega la tarjeta B
- **THEN** el intento con la tarjeta B se guarda con número 1

### Requirement: Registro posterior a la decisión
El sistema SHALL calcular el resultado del pago antes de guardar el intento, y el guardado MUST NOT cambiar ese resultado.

#### Scenario: Guardado de un pago aprobado
- **WHEN** el resultado es `APPROVED`
- **THEN** el intento se guarda con el mismo `transactionId` que se devuelve al cliente

### Requirement: Unicidad del pago aprobado
El sistema MUST garantizar a nivel de datos que una reserva tenga como máximo un intento `APPROVED`, incluso con solicitudes concurrentes.

#### Scenario: Solicitudes concurrentes aprobadas
- **WHEN** dos solicitudes simultáneas con el mismo `reservationId` y una tarjeta aprobable llegan a la vez
- **THEN** solo se guarda un intento `APPROVED` y ambas respuestas devuelven el mismo `transactionId`
