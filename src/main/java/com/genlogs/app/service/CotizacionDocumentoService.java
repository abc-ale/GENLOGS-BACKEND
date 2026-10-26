package com.genlogs.app.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.dto.EnvioCotizacionResponse;

import lombok.RequiredArgsConstructor;

/**
 * PDF y envío por correo de una cotización. Se apoya en CotizacionService
 * (que ya calcula subtotal/IGV/total desde la vista) y en MailService (Brevo).
 */
@Service
@RequiredArgsConstructor
public class CotizacionDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(CotizacionDocumentoService.class);

    private final CotizacionService cotizacionService;
    private final MailService mailService;

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------

    public byte[] generarPdf(Long idCotizacion) {
        CotizacionResponse c = cotizacionService.buscarPorId(idCotizacion);
        List<CotizacionDetalleResponse> lineas = cotizacionService.listarLineas(idCotizacion);
        try {
            return CotizacionPdfLayout.generar(c, lineas);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la cotización", e);
        }
    }

    public String nombreArchivo(Long idCotizacion) {
        CotizacionResponse c = cotizacionService.buscarPorId(idCotizacion);
        String codigo = c.getCodigoCotizacion() != null ? c.getCodigoCotizacion() : String.valueOf(idCotizacion);
        return "cotizacion-" + codigo.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf";
    }

    // ------------------------------------------------------------------
    // Envío por correo
    // ------------------------------------------------------------------

    public EnvioCotizacionResponse enviar(Long idCotizacion, EnviarCotizacionRequest request) {
        CotizacionResponse c = cotizacionService.buscarPorId(idCotizacion);
        byte[] pdf = generarPdf(idCotizacion);

        StringBuilder texto = new StringBuilder(request.getMensaje().trim());
        if (Boolean.TRUE.equals(request.getIncluirDetalles())) {
            texto.append("\n\n--- Detalle ---\n");
            for (CotizacionDetalleResponse l : cotizacionService.listarLineas(idCotizacion)) {
                texto.append("- ").append(num(l.getCantidad())).append(" x ").append(descripcionDe(l))
                        .append("  ").append(codigoMoneda(c)).append(' ').append(dinero(l.getImporteLinea())).append('\n');
            }
            texto.append("Subtotal: ").append(codigoMoneda(c)).append(' ').append(dinero(c.getSubtotal())).append('\n')
                    .append("IGV: ").append(codigoMoneda(c)).append(' ').append(dinero(c.getIgv())).append('\n')
                    .append("TOTAL: ").append(codigoMoneda(c)).append(' ').append(dinero(c.getTotal())).append('\n');
        }

        // Lanza BusinessException si el correo no está configurado o Brevo rechaza el envío.
        mailService.enviarConAdjunto(request.getCorreoDestinatario(), request.getAsunto(), texto.toString(),
                nombreArchivo(idCotizacion), pdf);

        // Una cotización en BORRADOR pasa a ENVIADA al enviarse (queda en el seguimiento).
        if ("BORRADOR".equals(c.getEstadoCodigo())) {
            try {
                CambiarEstadoCotizacionRequest cambio = new CambiarEstadoCotizacionRequest();
                cambio.setCodigoEstado("ENVIADA");
                cambio.setComentario("Enviada por correo a " + request.getCorreoDestinatario());
                cotizacionService.cambiarEstado(idCotizacion, cambio);
            } catch (RuntimeException e) {
                log.warn("Correo enviado, pero no se pudo pasar la cotización {} a ENVIADA: {}", idCotizacion, e.getMessage());
            }
        }
        return new EnvioCotizacionResponse(true, "Cotización enviada a " + request.getCorreoDestinatario());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static String descripcionDe(CotizacionDetalleResponse l) {
        if (l.getDescripcionPersonalizada() != null && !l.getDescripcionPersonalizada().isBlank()) {
            return l.getDescripcionPersonalizada();
        }
        if (l.getProductoNombre() != null) {
            return l.getProductoNombre();
        }
        return l.getServicioNombre() != null ? l.getServicioNombre() : "Item";
    }

    private static String codigoMoneda(CotizacionResponse c) {
        String codigo = c.getMonedaCodigo();
        return codigo == null || codigo.isBlank() ? "PEN" : codigo.trim().toUpperCase();
    }

    private static String num(BigDecimal n) {
        return n == null ? "-" : n.stripTrailingZeros().toPlainString();
    }

    private static String dinero(BigDecimal n) {
        return n == null ? "0.00" : n.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}