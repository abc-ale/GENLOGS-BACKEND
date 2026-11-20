package com.genlogs.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historial de la tasa de tributos (RF-37): IGV + IPM. Rige la tasa con la fecha
 * "vigente_desde" más reciente que no sea posterior a la fecha del documento.
 * Cada cotización y facturación guarda los porcentajes con que se creó, así un
 * cambio de tasa no altera los documentos anteriores.
 */
@Entity
@Table(name = "tasa_igv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class TasaIgv extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa_igv")
    private Integer idTasaIgv;

    @Column(name = "vigente_desde", nullable = false, unique = true)
    private LocalDate vigenteDesde;

    /** Impuesto General a las Ventas, en porcentaje (por ejemplo 15.50). */
    @Column(name = "porcentaje_igv", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIgv;

    /** Impuesto de Promoción Municipal, en porcentaje (por ejemplo 2.50). */
    @Column(name = "porcentaje_ipm", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIpm;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}
