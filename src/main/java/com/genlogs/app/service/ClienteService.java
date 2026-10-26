package com.genlogs.app.service;

import com.genlogs.app.dto.ClienteRequest;
import com.genlogs.app.dto.ClienteResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
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
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ProveedorRepository proveedorRepository;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final DistritoRepository distritoRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository;

    /**
     * Da de alta un cliente. Si ya existe un Tercero con el mismo
     * (tipo_documento, numero_documento), reutiliza ese tercero
     * (por ejemplo, si ya era proveedor) en vez de duplicarlo.
     */
    @Transactional
    public ClienteResponse registrar(ClienteRequest request) {
        TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no existe"));

        validarLongitudDocumento(tipoDocumento, request.getNumeroDocumento());

        Tercero tercero = terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
                        request.getIdTipoDocumento(), request.getNumeroDocumento())
                .orElseGet(() -> crearTercero(request, tipoDocumento));

        if (clienteRepository.findByTercero_IdTercero(tercero.getIdTercero()).isPresent()) {
            throw new BusinessException("Este documento ya está registrado como cliente");
        }

        SectorEconomico sector = sectorEconomicoRepository.findById(request.getIdSectorEconomico())
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no existe"));

        Cliente cliente = Cliente.builder()
                .tercero(tercero)
                .sectorEconomico(sector)
                .situacion("ACTIVO")
                .build();
        cliente.setUserCreate(usuarioActual());
        cliente.setProcessCreate("ALTA_CLIENTE");

        cliente = clienteRepository.save(cliente);
        return toResponse(cliente);
    }

    /**
     * Actualiza los datos de un cliente (PUT /api/clientes/{id}).
     * Los datos de contacto viven en el Tercero, que puede ser compartido con un
     * proveedor: al editarlos se actualizan para ambos roles. El tipo y número de
     * documento no se pueden cambiar (identifican al tercero).
     */
    @Transactional
    public ClienteResponse actualizar(Long idCliente, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        Tercero tercero = cliente.getTercero();

        if (!tercero.getTipoDocumento().getIdTipoDocumento().equals(request.getIdTipoDocumento())
                || !tercero.getNumeroDocumento().equals(request.getNumeroDocumento())) {
            throw new BusinessException("No se puede cambiar el tipo ni el número de documento de un cliente existente");
        }

        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe"));
        SectorEconomico sector = sectorEconomicoRepository.findById(request.getIdSectorEconomico())
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no existe"));

        String usuario = usuarioActual();
        LocalDateTime ahora = LocalDateTime.now();

        tercero.setRazonSocial(request.getRazonSocial());
        tercero.setDistrito(distrito);
        tercero.setDireccion(request.getDireccion());
        tercero.setTelefono(request.getTelefono());
        tercero.setCorreo(request.getCorreo());
        tercero.setUserUpdate(usuario);
        tercero.setProcessUpdate("EDICION_CLIENTE");
        tercero.setDateUpdate(ahora);
        terceroRepository.save(tercero);

        cliente.setSectorEconomico(sector);
        cliente.setUserUpdate(usuario);
        cliente.setProcessUpdate("EDICION_CLIENTE");
        cliente.setDateUpdate(ahora);
        return toResponse(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(Long idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        return toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorDocumento(String numeroDocumento) {
        Cliente cliente = clienteRepository.findByNumeroDocumento(numeroDocumento)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        return toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listarActivos() {
        java.util.Set<Long> terceroProveedores = proveedorRepository.idsTerceroProveedores();
        return clienteRepository.listarParaPantalla().stream()
                .map(c -> toResponse(c, terceroProveedores.contains(c.getTercero().getIdTercero())))
                .toList();
    }

    @Transactional
    public void desactivar(Long idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        cliente.setSituacion("INACTIVO");
        cliente.setStatus("I");
        cliente.setUserUpdate(usuarioActual());
        cliente.setProcessUpdate("BAJA_CLIENTE");
        cliente.setDateUpdate(LocalDateTime.now());
        clienteRepository.save(cliente);
    }

    // ------------------------------------------------------------------

    private Tercero crearTercero(ClienteRequest request, TipoDocumento tipoDocumento) {
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
        tercero.setProcessCreate("ALTA_CLIENTE");

        return terceroRepository.save(tercero);
    }

    /** Misma regla que valida el trigger fn_tercero_valida_documento() en la BD. */
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

    private ClienteResponse toResponse(Cliente cliente) {
        Tercero t = cliente.getTercero();
        boolean tambienProveedor = proveedorRepository
                .findByTercero_IdTercero(t.getIdTercero())
                .isPresent();
        return toResponse(cliente, tambienProveedor);
    }

    private ClienteResponse toResponse(Cliente cliente, boolean tambienProveedor) {
        Tercero t = cliente.getTercero();

        return ClienteResponse.builder()
                .idCliente(cliente.getIdCliente())
                .idTercero(t.getIdTercero())
                .idTipoDocumento(t.getTipoDocumento().getIdTipoDocumento())
                .tipoDocumento(t.getTipoDocumento().getCodigoTipo())
                .numeroDocumento(t.getNumeroDocumento())
                .razonSocial(t.getRazonSocial())
                .idDistrito(t.getDistrito().getIdDistrito())
                .direccion(t.getDireccion())
                .telefono(t.getTelefono())
                .correo(t.getCorreo())
                .idSectorEconomico(cliente.getSectorEconomico() != null
                        ? cliente.getSectorEconomico().getIdSectorEconomico() : null)
                .sectorEconomico(cliente.getSectorEconomico() != null
                        ? cliente.getSectorEconomico().getNombreSector() : null)
                .situacion(cliente.getSituacion())
                .esTambienProveedor(tambienProveedor)
                .build();
    }

    /** Toma el usuario autenticado del JWT. */
    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}