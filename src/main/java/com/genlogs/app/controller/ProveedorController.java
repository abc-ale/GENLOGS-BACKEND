package com.genlogs.app.controller;

import com.genlogs.app.dto.ProveedorRequest;
import com.genlogs.app.dto.ProveedorResponse;
import com.genlogs.app.service.ProveedorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @PostMapping
    // @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')") // lo agrega Luana con MethodSecurityConfig
    public ResponseEntity<ProveedorResponse> registrar(@Valid @RequestBody ProveedorRequest request) {
        ProveedorResponse proveedor = proveedorService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedor);
    }

    @GetMapping("/{idProveedor}")
    public ResponseEntity<ProveedorResponse> buscarPorId(@PathVariable Long idProveedor) {
        return ResponseEntity.ok(proveedorService.buscarPorId(idProveedor));
    }

    @GetMapping
    public ResponseEntity<List<ProveedorResponse>> listarActivos() {
        return ResponseEntity.ok(proveedorService.listarActivos());
    }

    @DeleteMapping("/{idProveedor}")
    public ResponseEntity<Void> desactivar(@PathVariable Long idProveedor) {
        proveedorService.desactivar(idProveedor);
        return ResponseEntity.noContent().build();
    }
}