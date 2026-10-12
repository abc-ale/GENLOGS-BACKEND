package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Respuesta de POST /api/cotizaciones/{id}/enviar: { success, mensaje }. */
@Getter
@AllArgsConstructor
public class EnvioCotizacionResponse {
    private boolean success;
    private String mensaje;
}