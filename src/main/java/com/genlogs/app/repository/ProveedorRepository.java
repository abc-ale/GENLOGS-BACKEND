package com.genlogs.app.repository;

import com.genlogs.app.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByTercero_IdTercero(Long idTercero);

    List<Proveedor> findBySituacionAndStatus(String situacion, String status);

    /** Listado para la pantalla de Proveedores en UNA sola consulta (sin N+1). */
    @Query("""
           SELECT p FROM Proveedor p
           JOIN FETCH p.tercero t
           JOIN FETCH t.tipoDocumento
           JOIN FETCH t.distrito
           WHERE p.status = 'A'
             AND (p.situacion IS NULL OR UPPER(p.situacion) <> 'INACTIVO')
           ORDER BY t.razonSocial
           """)
    List<Proveedor> listarParaPantalla();

    /** Ids de terceros que tambien son proveedores (para marcar esTambienProveedor sin una consulta por cliente). */
    @Query("SELECT p.tercero.idTercero FROM Proveedor p WHERE p.status = 'A'")
    java.util.Set<Long> idsTerceroProveedores();
}