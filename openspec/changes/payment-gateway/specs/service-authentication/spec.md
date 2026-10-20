## Purpose

Restringe el consumo de la pasarela a servicios autorizados mediante una API key estática, como autenticación servidor a servidor para el MVP.

## ADDED Requirements

### Requirement: API key obligatoria
El sistema SHALL exigir el header `Authorization: Bearer <api-key>` en toda solicitud a `/payments`. Si falta o no coincide con la key configurada, MUST responder `401` con `status` `UNAUTHORIZED` y `code` `INVALID_API_KEY`, antes de validar el cuerpo y sin guardar ningún intento.

#### Scenario: Sin header
- **WHEN** llega una solicitud a `/payments` sin `Authorization`
- **THEN** el sistema responde `401` con `code` `INVALID_API_KEY` y mensaje "Invalid or missing API key"

#### Scenario: Key incorrecta
- **WHEN** el header trae una key distinta a la configurada
- **THEN** el sistema responde `401` y no procesa ni guarda nada

#### Scenario: Key incorrecta con cuerpo inválido
- **WHEN** la key es incorrecta y además el cuerpo tiene formato inválido
- **THEN** el sistema responde `401`, no `400`

#### Scenario: Key correcta
- **WHEN** el header trae la key configurada
- **THEN** el sistema continúa con la validación y el procesamiento del pago

### Requirement: Manejo seguro de la key
El sistema MUST leer la key desde configuración externa, compararla en tiempo constante y no registrarla en logs ni en respuestas.

#### Scenario: Key en logs
- **WHEN** se rechaza una solicitud por key inválida
- **THEN** el log registra el rechazo sin incluir la key recibida ni la configurada

### Requirement: Endpoints de operación
El sistema SHALL permitir sin API key el endpoint de salud para el healthcheck de contenedores.

#### Scenario: Consulta de salud
- **WHEN** se consulta el endpoint de salud sin `Authorization`
- **THEN** el sistema responde sin exigir la key
