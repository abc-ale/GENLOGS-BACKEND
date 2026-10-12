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

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            Pagina p = new Pagina(doc);

            p.linea(negrita, 14, "GENLOGS - General Logistic Solutions S.A.C.");
            p.linea(normal, 9, "RUC 20516185644 - Jr. Republica de Ecuador 1025, Lima, Peru");
            p.espacio(8);
            p.linea(negrita, 13, "COTIZACION " + nvl(c.getCodigoCotizacion()));
            p.espacio(4);
            p.linea(normal, 10, "Cliente: " + nvl(c.getClienteRazonSocial()));
            p.linea(normal, 10, "Fecha: " + (c.getFechaCotizacion() != null ? c.getFechaCotizacion().format(FMT) : "-")
                    + "    Valida hasta: " + (c.getFechaValidez() != null ? c.getFechaValidez().format(FMT) : "-"));
            p.linea(normal, 10, "Estado: " + nvl(c.getEstado()) + "    Moneda: " + nvl(c.getMoneda()));
            if (c.getPlazoEntregaEstimadoDias() != null) {
                p.linea(normal, 10, "Plazo de entrega estimado: " + c.getPlazoEntregaEstimadoDias()
                        + " dias (referencial, sujeto a confirmacion)");
            }
            p.espacio(10);

            p.linea(negrita, 9, String.format("%-6s %-10s %-44s %10s %10s", "Cant.", "Unidad", "Descripcion", "P.Unit", "Importe"));
            for (CotizacionDetalleResponse l : lineas) {
                String desc = recortar(descripcionDe(l), 44);
                p.linea(normal, 9, String.format("%-6s %-10s %-44s %10s %10s",
                        num(l.getCantidad()), recortar(nvl(l.getUnidadMedida()), 10), desc,
                        dinero(l.getPrecioUnitario()), dinero(l.getImporteLinea())));
            }
            p.espacio(10);
            p.linea(normal, 10, "Subtotal: " + nvl(c.getMoneda()) + " " + dinero(c.getSubtotal()));
            p.linea(normal, 10, "IGV (18%): " + nvl(c.getMoneda()) + " " + dinero(c.getIgv()));
            p.linea(negrita, 11, "TOTAL: " + nvl(c.getMoneda()) + " " + dinero(c.getTotal()));
            if (c.getObservaciones() != null && !c.getObservaciones().isBlank()) {
                p.espacio(8);
                p.linea(normal, 9, "Observaciones: " + recortar(c.getObservaciones(), 110));
            }

            p.cerrar();
            doc.save(out);
            return out.toByteArray();
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
            y = PDRectangle.A4.getHeight() - 50;
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