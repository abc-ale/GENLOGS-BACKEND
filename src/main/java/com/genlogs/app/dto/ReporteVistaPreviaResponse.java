package com.genlogs.app.dto;

import java.util.List;

/** Vista previa de un reporte: encabezados + primeras filas, sin generar el archivo. */
public record ReporteVistaPreviaResponse(
        String tipoReporte,
        List<String> encabezados,
        List<List<String>> filas,
        int totalFilas,
        boolean truncado) {
}