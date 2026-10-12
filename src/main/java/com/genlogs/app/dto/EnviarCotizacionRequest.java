package com.genlogs.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Cuerpo de POST /api/cotizaciones/{id}/enviar (coincide con EnviarCotizacionRequest del frontend). */
@Getter
@Setter
public class EnviarCotizacionRequest {

    @NotBlank(message = "Debe indicar el correo del destinatario")
    @Email(message = "Correo inválido")
    @Size(max = 150)
    private String correoDestinatario;

    @NotBlank(message = "Debe indicar el asunto")
    @Size(max = 200)
    private String asunto;

    @NotBlank(message = "Debe indicar el mensaje")
    @Size(max = 2000)
    private String mensaje;

    /** Si es true, el cuerpo del correo incluye el detalle de líneas y totales (el PDF siempre se adjunta). */
    private Boolean incluirDetalles = Boolean.TRUE;
}