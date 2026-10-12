package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Proveedor con los datos de su tercero en plano (evita serializar entidades JPA con relaciones LAZY). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorResponse {
    private Long idProveedor;
    private Long idTercero;
    private Integer idTipoDocumento;
    private String tipoDocumento;      // codigo_tipo: RUC / DNI / CE / PAS
    private String numeroDocumento;
    private String razonSocial;
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;
    private String situacion;
    private String status;
}