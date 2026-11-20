package com.genlogs.app.controller;

import com.genlogs.app.dto.TasaIgvRequest;
import com.genlogs.app.dto.TasaIgvResponse;
import com.genlogs.app.service.TasaIgvService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Tasa de tributos configurable (RF-37). Está bajo /api/catalogos porque es un catálogo
 * maestro (CU-14): todos la consultan y solo quien mantiene los catálogos la registra.
 */
@RestController
@RequestMapping("/api/catalogos/tasas-igv")
@RequiredArgsConstructor
public class TasaIgvController {

    private final TasaIgvService tasaIgvService;

    /** Historial de tasas, de la más reciente a la más antigua. */
    @GetMapping
    public ResponseEntity<List<TasaIgvResponse>> listar() {
        return ResponseEntity.ok(tasaIgvService.listar());
    }

    /** Tasa que rige en la fecha indicada (hoy si no se envía). */
    @GetMapping("/vigente")
    public ResponseEntity<TasaIgvResponse> vigente(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(tasaIgvService.vigenteResponse(fecha));
    }

    /** Registra una nueva tasa a partir de una fecha; las anteriores se conservan como historial. */
    @PostMapping
    public ResponseEntity<TasaIgvResponse> registrar(@Valid @RequestBody TasaIgvRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tasaIgvService.registrar(request));
    }
}
