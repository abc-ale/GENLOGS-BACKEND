package com.genlogs.app.service;

import com.genlogs.app.dto.TasaIgvRequest;
import com.genlogs.app.dto.TasaIgvResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.TasaIgv;
import com.genlogs.app.repository.TasaIgvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tasa de tributos vigente y configurable (RF-37): IGV + IPM. La tasa no se edita ni se
 * borra: un cambio se registra como una nueva tasa con su fecha de inicio, para conservar
 * el historial con que se emitieron los documentos.
 */
@Service
@RequiredArgsConstructor
public class TasaIgvService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final TasaIgvRepository tasaIgvRepository;

    @Transactional(readOnly = true)
    public List<TasaIgvResponse> listar() {
        Integer idVigente = tasaIgvRepository
                .findFirstByVigenteDesdeLessThanEqualAndStatusOrderByVigenteDesdeDesc(LocalDate.now(), "A")
                .map(TasaIgv::getIdTasaIgv)
                .orElse(null);
        return tasaIgvRepository.findByStatusOrderByVigenteDesdeDesc("A").stream()
                .map(t -> toResponse(t, t.getIdTasaIgv().equals(idVigente)))
                .toList();
    }

    /** Tasa que rige en la fecha indicada (hoy si es null). */
    @Transactional(readOnly = true)
    public TasaIgv vigenteEn(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return tasaIgvRepository
                .findFirstByVigenteDesdeLessThanEqualAndStatusOrderByVigenteDesdeDesc(dia, "A")
                .orElseThrow(() -> new BusinessException(
                        "No hay una tasa de IGV configurada que rija el " + dia + ". Regístrala en catálogos maestros."));
    }

    @Transactional(readOnly = true)
    public TasaIgvResponse vigenteResponse(LocalDate fecha) {
        TasaIgv tasa = vigenteEn(fecha);
        return toResponse(tasa, true);
    }

    @Transactional
    public TasaIgvResponse registrar(TasaIgvRequest request) {
        if (tasaIgvRepository.existsByVigenteDesdeAndStatus(request.getVigenteDesde(), "A")) {
            throw new BusinessException("Ya existe una tasa que rige desde el " + request.getVigenteDesde());
        }
        if (request.getPorcentajeIgv().add(request.getPorcentajeIpm()).compareTo(CIEN) > 0) {
            throw new BusinessException("La suma de IGV e IPM no puede superar 100 %");
        }

        TasaIgv tasa = TasaIgv.builder()
                .vigenteDesde(request.getVigenteDesde())
                .porcentajeIgv(request.getPorcentajeIgv())
                .porcentajeIpm(request.getPorcentajeIpm())
                .descripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null)
                .build();
        tasa.setStatus("A");
        tasa.setUserCreate(SecurityContextHolder.getContext().getAuthentication().getName());
        tasa.setProcessCreate("ALTA_TASA_IGV");
        tasa.setDateCreate(LocalDateTime.now());

        TasaIgv guardada = tasaIgvRepository.save(tasa);
        boolean vigente = !guardada.getVigenteDesde().isAfter(LocalDate.now())
                && vigenteEn(LocalDate.now()).getIdTasaIgv().equals(guardada.getIdTasaIgv());
        return toResponse(guardada, vigente);
    }

    private TasaIgvResponse toResponse(TasaIgv t, boolean vigente) {
        return TasaIgvResponse.builder()
                .idTasaIgv(t.getIdTasaIgv())
                .vigenteDesde(t.getVigenteDesde())
                .porcentajeIgv(t.getPorcentajeIgv())
                .porcentajeIpm(t.getPorcentajeIpm())
                .tasaTotal(t.getPorcentajeIgv().add(t.getPorcentajeIpm()))
                .descripcion(t.getDescripcion())
                .vigente(vigente)
                .build();
    }
}
