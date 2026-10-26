package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
                        .append("  ").append(nvl(c.getMoneda())).append(' ').append(dinero(l.getImporteLinea())).append('\n');
            }
            texto.append("Subtotal: ").append(nvl(c.getMoneda())).append(' ').append(dinero(c.getSubtotal())).append('\n')
                    .append("IGV: ").append(nvl(c.getMoneda())).append(' ').append(dinero(c.getIgv())).append('\n')
                    .append("TOTAL: ").append(nvl(c.getMoneda())).append(' ').append(dinero(c.getTotal())).append('\n');
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

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String num(BigDecimal n) {
        return n == null ? "-" : n.stripTrailingZeros().toPlainString();
    }

    private static String dinero(BigDecimal n) {
        return n == null ? "0.00" : n.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    /** Escribe líneas de texto y abre una página nueva cuando se acaba el espacio. */
    private static final class Pagina {
        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;

        Pagina(PDDocument doc) throws IOException {
            this.doc = doc;
            nueva();
        }

        private void nueva() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - 78;
            PdfBranding.logo(doc, cs, 40, PDRectangle.A4.getHeight() - 66, 92, 38);
        }

        void linea(PDType1Font fuente, int tam, String texto) throws IOException {
            if (y < 60) {
                nueva();
            }
            cs.setFont(fuente, tam);
            cs.beginText();
            cs.newLineAtOffset(40, y);
            cs.showText(sanitizar(texto));
            cs.endText();
            y -= tam + 6;
        }

        void espacio(float puntos) {
            y -= puntos;
        }

        void cerrar() throws IOException {
            cs.close();
        }

        /** Las fuentes estándar de PDFBox (WinAnsi) no admiten todos los caracteres Unicode. */
        private static String sanitizar(String t) {
            StringBuilder sb = new StringBuilder();
            for (char c : t.toCharArray()) {
                sb.append(c < 256 && c >= 32 ? c : '?');
            }
            return sb.toString();
        }
    }
}

