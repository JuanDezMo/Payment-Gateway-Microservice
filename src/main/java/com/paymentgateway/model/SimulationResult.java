package com.paymentgateway.model;

/** Resultado de la simulacion por final de tarjeta; no incluye CARD_FLAGGED, que depende del historial. */
public enum SimulationResult {
    APPROVED,
    REJECTED,
    ERROR
}
