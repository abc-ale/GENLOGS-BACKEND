package com.genlogs.app.repository;

import com.genlogs.app.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTercero_IdTercero(Long idTercero);

    List<Cliente> findBySituacionAndStatus(String situacion, String status);

    /**
     * Listado para la pantalla de Clientes en UNA sola consulta (antes se hacian cientos de
     * consultas lazy y la respuesta tardaba tanto que el front hacia timeout). No exige
     * situacion = 'ACTIVO' exacta: basta con que el registro no este dado de baja.
     */
    @Query("""
           SELECT c FROM Cliente c
           JOIN FETCH c.tercero t
           JOIN FETCH t.tipoDocumento
           JOIN FETCH t.distrito
           LEFT JOIN FETCH c.sectorEconomico
           WHERE c.status = 'A'
             AND (c.situacion IS NULL OR UPPER(c.situacion) <> 'INACTIVO')
           ORDER BY t.razonSocial
           """)
    List<Cliente> listarParaPantalla();

    // --- Soporte para DashboardController ---
    long countBySituacionAndStatus(String situacion, String status);

    @Query("""
           SELECT c FROM Cliente c
           WHERE c.tercero.numeroDocumento = :numeroDocumento
             AND c.status = 'A'
           """)
    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);
}