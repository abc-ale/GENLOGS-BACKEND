package com.genlogs.app.service;

import com.genlogs.app.dto.ProveedorRequest;
import com.genlogs.app.dto.ProveedorResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Proveedor;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final DistritoRepository distritoRepository;

    /**
     * Da de alta un proveedor. Si ya existe un Tercero con el mismo
     * (tipo_documento, numero_documento) —por ejemplo porque ya es
     * cliente— reutiliza ese tercero en vez de duplicarlo.
     */
    @Transactional
    public ProveedorResponse registrar(ProveedorRequest request) {
        TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no existe"));

        validarLongitudDocumento(tipoDocumento, request.getNumeroDocumento());

        Tercero tercero = terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
                        request.getIdTipoDocumento(), request.getNumeroDocumento())
                .orElseGet(() -> crearTercero(request, tipoDocumento));

        if (proveedorRepository.findByTercero_IdTercero(tercero.getIdTercero()).isPresent()) {
            throw new BusinessException("Este documento ya está registrado como proveedor");
        }

        Proveedor proveedor = Proveedor.builder()
                .tercero(tercero)
                .situacion("ACTIVO")
                .build();
        proveedor.setUserCreate(usuarioActual());
        proveedor.setProcessCreate("ALTA_PROVEEDOR");

        return toResponse(proveedorRepository.save(proveedor));
    }

    /**
     * Actualiza los datos de un proveedor (PUT /api/proveedores/{id}).
     * Igual que en clientes: los datos viven en el Tercero (compartido) y el
     * tipo/número de documento no se pueden cambiar.
     */
    @Transactional
    public ProveedorResponse actualizar(Long idProveedor, ProveedorRequest request) {
        Proveedor proveedor = obtenerEntidad(idProveedor);
        Tercero tercero = proveedor.getTercero();

        if (!tercero.getTipoDocumento().getIdTipoDocumento().equals(request.getIdTipoDocumento())
                || !tercero.getNumeroDocumento().equals(request.getNumeroDocumento())) {
            throw new BusinessException("No se puede cambiar el tipo ni el número de documento de un proveedor existente");
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe"));

        String usuario = usuarioActual();
        LocalDateTime ahora = LocalDateTime.now();

        tercero.setRazonSocial(request.getRazonSocial());
        tercero.setDistrito(distrito);
        tercero.setDireccion(request.getDireccion());
        tercero.setTelefono(request.getTelefono());
        tercero.setCorreo(request.getCorreo());
        tercero.setUserUpdate(usuario);
        tercero.setProcessUpdate("EDICION_PROVEEDOR");
        tercero.setDateUpdate(ahora);
        terceroRepository.save(tercero);

        proveedor.setUserUpdate(usuario);
        proveedor.setProcessUpdate("EDICION_PROVEEDOR");
        proveedor.setDateUpdate(ahora);
        return toResponse(proveedorRepository.save(proveedor));
    }

    @Transactional(readOnly = true)
    public ProveedorResponse buscarPorId(Long idProveedor) {
        return toResponse(obtenerEntidad(idProveedor));
    }

    private Proveedor obtenerEntidad(Long idProveedor) {
        return proveedorRepository.findById(idProveedor)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    private ProveedorResponse toResponse(Proveedor p) {
        Tercero t = p.getTercero();
        return ProveedorResponse.builder()
                .idProveedor(p.getIdProveedor())
                .idTercero(t.getIdTercero())
                .idTipoDocumento(t.getTipoDocumento().getIdTipoDocumento())
                .tipoDocumento(t.getTipoDocumento().getCodigoTipo())
                .numeroDocumento(t.getNumeroDocumento())
                .razonSocial(t.getRazonSocial())
                .idDistrito(t.getDistrito().getIdDistrito())
                .direccion(t.getDireccion())
                .telefono(t.getTelefono())
                .correo(t.getCorreo())
                .situacion(p.getSituacion())
                .status(p.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listarActivos() {
        return proveedorRepository.findBySituacionAndStatus("ACTIVO", "A").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void desactivar(Long idProveedor) {
        Proveedor proveedor = obtenerEntidad(idProveedor);
        proveedor.setSituacion("INACTIVO");
        proveedor.setStatus("I");
        proveedor.setUserUpdate(usuarioActual());
        proveedor.setProcessUpdate("BAJA_PROVEEDOR");
        proveedor.setDateUpdate(LocalDateTime.now());
        proveedorRepository.save(proveedor);
    }

    // ------------------------------------------------------------------

    private Tercero crearTercero(ProveedorRequest request, TipoDocumento tipoDocumento) {
        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe"));

        Tercero tercero = Tercero.builder()
                .tipoDocumento(tipoDocumento)
                .distrito(distrito)
                .numeroDocumento(request.getNumeroDocumento())
                .razonSocial(request.getRazonSocial())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .correo(request.getCorreo())
                .build();
        tercero.setUserCreate(usuarioActual());
        tercero.setProcessCreate("ALTA_PROVEEDOR");

        return terceroRepository.save(tercero);
    }

    private void validarLongitudDocumento(TipoDocumento tipoDocumento, String numeroDocumento) {
        if (numeroDocumento.length() != tipoDocumento.getLongitudDocumento()) {
            throw new BusinessException(String.format(
                    "El documento %s debe tener %d dígitos",
                    tipoDocumento.getCodigoTipo(), tipoDocumento.getLongitudDocumento()));
        }
        if (Boolean.TRUE.equals(tipoDocumento.getSoloNumerico()) && !numeroDocumento.matches("^[0-9]+$")) {
            throw new BusinessException(
                    "El documento " + tipoDocumento.getCodigoTipo() + " debe ser numérico");
        }
    }

    /** Toma el usuario autenticado del JWT. */
    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}