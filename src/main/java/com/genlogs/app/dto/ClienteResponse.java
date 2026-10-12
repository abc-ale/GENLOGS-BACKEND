package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {
    private Long idCliente;
    private Long idTercero;
    private Integer idTipoDocumento;
    private String tipoDocumento;      // codigo_tipo: RUC / DNI / CE / PAS
    private String numeroDocumento;
    private String razonSocial;
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;
    private Integer idSectorEconomico;
    private String sectorEconomico;
    private String situacion;
    private Boolean esTambienProveedor; // true si el mismo tercero también tiene rol de proveedor
}