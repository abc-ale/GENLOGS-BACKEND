package com.genlogs.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nueva tasa de tributos (IGV + IPM) a partir de una fecha (RF-37). */
@Getter
@Setter
public class TasaIgvRequest {

    @NotNull(message = "Debe indicar desde qué fecha rige la tasa")
    private LocalDate vigenteDesde;

    @NotNull(message = "Debe indicar el porcentaje de IGV")
    @DecimalMin(value = "0.00", message = "El IGV no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El IGV no puede superar 100 %")
    @Digits(integer = 3, fraction = 2, message = "El IGV admite como máximo 2 decimales")
    private BigDecimal porcentajeIgv;

    @NotNull(message = "Debe indicar el porcentaje de IPM")
    @DecimalMin(value = "0.00", message = "El IPM no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El IPM no puede superar 100 %")
    @Digits(integer = 3, fraction = 2, message = "El IPM admite como máximo 2 decimales")
    private BigDecimal porcentajeIpm;

    @Size(max = 255, message = "La descripción admite como máximo 255 caracteres")
    private String descripcion;
}
