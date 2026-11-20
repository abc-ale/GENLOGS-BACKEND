package com.genlogs.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrdenCompraRequest {

    @NotNull(message = "Debe indicar la cotización")
    private Long idCotizacion;

    private Integer idCondicionPago;

    /** Numeración NNNN-NN del documento (por ejemplo 0001-26): 4 dígitos, guion y 2 dígitos. */
    @NotBlank(message = "Debe indicar el número de orden de compra")
    @Pattern(regexp = OrdenCompraRequest.FORMATO_NUMERO,
            message = "El número de orden de compra debe tener el formato NNNN-NN (por ejemplo, 0001-26)")
    private String numeroOrdenCompra;

    public static final String FORMATO_NUMERO = "^\\d{4}-\\d{2}$";

    private LocalDate fechaEmisionCliente;

    private String observaciones;
}
