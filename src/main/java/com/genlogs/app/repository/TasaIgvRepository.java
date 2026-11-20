package com.genlogs.app.repository;

import com.genlogs.app.model.TasaIgv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TasaIgvRepository extends JpaRepository<TasaIgv, Integer> {

    /** Tasa que rige en la fecha: la de "vigente_desde" más reciente que no sea posterior. */
    Optional<TasaIgv> findFirstByVigenteDesdeLessThanEqualAndStatusOrderByVigenteDesdeDesc(
            LocalDate fecha, String status);

    List<TasaIgv> findByStatusOrderByVigenteDesdeDesc(String status);

    boolean existsByVigenteDesdeAndStatus(LocalDate vigenteDesde, String status);
}
