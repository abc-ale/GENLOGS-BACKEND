package com.genlogs.app.service;

import com.genlogs.app.dto.DetalleFacturacionResponse;
import com.genlogs.app.dto.FacturacionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** PDF de la facturación (lo descarga el frontend en GET /api/facturacion/{id}/pdf). */
@Service
@RequiredArgsConstructor
public class FacturacionDocumentoService {

    private final FacturacionService facturacionService;

    /** PDF y nombre de archivo del comprobante. */
    public record PdfFacturacion(byte[] contenido, String nombreArchivo) {}

    @Transactional(readOnly = true)
    public PdfFacturacion generarPdf(Long idFacturacion) {
        FacturacionResponse factura = facturacionService.buscarPorId(idFacturacion);
        List<DetalleFacturacionResponse> lineas = facturacionService.listarDetalle(idFacturacion);
        try {
            byte[] contenido = FacturacionPdfLayout.generar(factura, lineas);
            String codigo = factura.getCodigoComprobante() != null ? factura.getCodigoComprobante() : String.valueOf(idFacturacion);
            return new PdfFacturacion(contenido, "facturacion-" + codigo.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la facturación", e);
        }
    }
}
