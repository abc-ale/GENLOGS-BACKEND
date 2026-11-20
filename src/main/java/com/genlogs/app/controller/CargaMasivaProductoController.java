package com.genlogs.app.controller;

import com.genlogs.app.dto.CargaMasivaProductosResponse;
import com.genlogs.app.service.CargaMasivaProductosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Carga masiva del catálogo de productos por CSV (RF-10). Está bajo /api/productos, así que
 * sigue los permisos de productos: con la matriz de roles, solo el Jefe Administrativo carga.
 */
@RestController
@RequestMapping("/api/productos/carga-masiva")
@RequiredArgsConstructor
public class CargaMasivaProductoController {

    private final CargaMasivaProductosService cargaMasivaProductosService;

    /**
     * Sube el CSV. Con simular=true solo valida y cuenta (no guarda). Si alguna fila tiene
     * errores no se guarda nada y responde 422 con la lista de errores por fila.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaMasivaProductosResponse> cargar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean simular) {
        CargaMasivaProductosResponse resultado = cargaMasivaProductosService.cargar(archivo, simular);
        return resultado.getErrores().isEmpty()
                ? ResponseEntity.ok(resultado)
                : ResponseEntity.unprocessableEntity().body(resultado);
    }

    /** Plantilla CSV con la cabecera y una fila de ejemplo. */
    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> plantilla() {
        // BOM para que Excel abra el archivo en UTF-8 (tildes y ñ correctas).
        byte[] contenido = ("﻿" + CargaMasivaProductosService.PLANTILLA).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("plantilla-productos.csv").build());
        return ResponseEntity.ok().headers(headers).body(contenido);
    }
}
