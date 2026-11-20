package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class TasaIgvResponse {
    private Integer idTasaIgv;
    private LocalDate vigenteDesde;
    private BigDecimal porcentajeIgv;
    private BigDecimal porcentajeIpm;
    /** IGV + IPM. */
    private BigDecimal tasaTotal;
    private String descripcion;
    /** true si es la tasa que rige hoy. */
    private boolean vigente;
}
