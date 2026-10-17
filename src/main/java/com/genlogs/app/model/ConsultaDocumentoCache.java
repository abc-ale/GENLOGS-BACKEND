package com.genlogs.app.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resultado guardado de una consulta DNI/RUC a Decolecta (cache local). */
@Entity
@Table(name = "consulta_documento_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultaDocumentoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "numero_documento", length = 11, nullable = false, unique = true)
    private String numeroDocumento;

    @Column(name = "tipo_documento", length = 3, nullable = false)
    private String tipoDocumento;

    @Column(name = "razon_social", length = 300)
    private String razonSocial;

    @Column(name = "direccion", length = 400)
    private String direccion;

    @Column(name = "nombres", length = 150)
    private String nombres;

    @Column(name = "apellido_paterno", length = 100)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 100)
    private String apellidoMaterno;

    @Column(name = "fecha_consulta", nullable = false)
    private LocalDateTime fechaConsulta;
}