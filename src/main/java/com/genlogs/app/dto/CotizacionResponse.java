package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CotizacionResponse {
    private Long idCotizacion;
    private String codigoCotizacion;
    private String clienteRazonSocial;
    private LocalDate fechaCotizacion;
    private LocalDate fechaValidez;
    private Integer plazoEntregaEstimadoDias;
    private String estado;          // descripción legible del estado
    private String estadoCodigo;    // código del estado, usado por CotizacionDocumentoService
    // Calculados desde vw_cotizacion_totales (la tabla no guarda el total)
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;
    private String moneda;
    private String condicionPago;
    private String observaciones;
}