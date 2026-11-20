package com.genlogs.app.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.genlogs.app.model.Facturacion;

public interface FacturacionRepository extends JpaRepository<Facturacion, Long> {
    Optional<Facturacion> findBySerieComprobanteAndNumeroComprobante(String serie, String numero);

    @Query("SELECT f FROM Facturacion f WHERE f.ordenCompra.cotizacion.cliente.idCliente = :idCliente AND f.status = 'A' ORDER BY f.fechaEmision DESC")
    List<Facturacion> findByCliente(@Param("idCliente") Long idCliente);

    // Listado paginado con filtros opcionales ('' y 0 significan "sin filtro").
    @Query(value = "SELECT f FROM Facturacion f WHERE f.status = 'A' "
            + "AND (:codigoEstado = '' OR f.estadoFacturacion.codigoEstado = :codigoEstado) "
            + "AND (:codigoTipo = '' OR f.tipoComprobante.codigoTipo = :codigoTipo) "
            + "AND (:idOrdenCompra = 0L OR f.ordenCompra.idOrdenCompra = :idOrdenCompra) "
            + "ORDER BY f.fechaEmision DESC, f.idFacturacion DESC",
            countQuery = "SELECT COUNT(f) FROM Facturacion f WHERE f.status = 'A' "
            + "AND (:codigoEstado = '' OR f.estadoFacturacion.codigoEstado = :codigoEstado) "
            + "AND (:codigoTipo = '' OR f.tipoComprobante.codigoTipo = :codigoTipo) "
            + "AND (:idOrdenCompra = 0L OR f.ordenCompra.idOrdenCompra = :idOrdenCompra)")
    Page<Facturacion> buscar(@Param("codigoEstado") String codigoEstado,
                             @Param("codigoTipo") String codigoTipo,
                             @Param("idOrdenCompra") long idOrdenCompra,
                             Pageable pageable);

    // --- Soporte para DashboardController ---
    // La tabla "facturacion" no guarda el total. Antes se leía de la vista
    // vw_facturacion_totales, que aplica un 18 % fijo; ahora cada comprobante usa la
    // tasa (IGV + IPM) guardada al emitirlo (RF-37), con la misma fórmula que
    // FacturacionService/DesgloseTributos: suma de líneas redondeadas × (1 + tasa / 100).
    // No se cuentan los comprobantes ANULADOS.
    String TOTALES_FACTURACION = "WITH t AS (" +
            "SELECT f.fecha_emision, " +
            "ROUND(COALESCE(SUM(ROUND(d.cantidad * (d.precio_unitario - d.descuento_unitario), 2)), 0) " +
            "* (1 + (f.porcentaje_igv + f.porcentaje_ipm) / 100.0), 2) AS total " +
            "FROM facturacion f " +
            "JOIN estado_facturacion e ON e.id_estado_facturacion = f.id_estado_facturacion " +
            "LEFT JOIN detalle_facturacion d ON d.id_facturacion = f.id_facturacion AND d.status = 'A' " +
            "WHERE f.status = 'A' AND e.codigo_estado <> 'ANULADA' " +
            "GROUP BY f.id_facturacion, f.fecha_emision, f.porcentaje_igv, f.porcentaje_ipm) ";

    @Query(value = TOTALES_FACTURACION + "SELECT COALESCE(SUM(total), 0) FROM t " +
            "WHERE (CAST(:desde AS date) IS NULL OR fecha_emision >= :desde) " +
            "AND (CAST(:hasta AS date) IS NULL OR fecha_emision <= :hasta)", nativeQuery = true)
    BigDecimal sumTotalFacturado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query(value = TOTALES_FACTURACION + "SELECT COALESCE(SUM(total), 0) FROM t " +
            "WHERE date_trunc('month', fecha_emision) = date_trunc('month', CAST(:fecha AS date))",
            nativeQuery = true)
    BigDecimal sumTotalFacturadoDelMes(@Param("fecha") LocalDate fecha);

    public interface PeriodoMontoProjection {
        String getPeriodo();
        BigDecimal getMonto();
    }

    @Query(value = TOTALES_FACTURACION + "SELECT to_char(date_trunc('month', fecha_emision), 'YYYY-MM') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM t WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('month', fecha_emision) " +
            "ORDER BY date_trunc('month', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumMensualDesde(@Param("desde") LocalDate desde);

    @Query(value = TOTALES_FACTURACION + "SELECT to_char(date_trunc('quarter', fecha_emision), 'YYYY') || '-Q' || " +
            "to_char(date_trunc('quarter', fecha_emision), 'Q') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM t WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('quarter', fecha_emision) " +
            "ORDER BY date_trunc('quarter', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumTrimestralDesde(@Param("desde") LocalDate desde);

    @Query(value = TOTALES_FACTURACION + "SELECT to_char(date_trunc('year', fecha_emision), 'YYYY') AS periodo, " +
            "COALESCE(SUM(total), 0) AS monto " +
            "FROM t WHERE fecha_emision >= :desde " +
            "GROUP BY date_trunc('year', fecha_emision) " +
            "ORDER BY date_trunc('year', fecha_emision)", nativeQuery = true)
    List<PeriodoMontoProjection> sumAnualDesde(@Param("desde") LocalDate desde);
}
