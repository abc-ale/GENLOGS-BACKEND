package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.dto.CotizacionResponse;

/** Diseño formal de cotizaciones basado en el formato corporativo GENLOGS. */
final class CotizacionPdfLayout {
    private static final float W = PDRectangle.A4.getWidth();
    private static final float H = PDRectangle.A4.getHeight();
    private static final float LEFT = 55;
    private static final float RIGHT = W - 55;
    private static final float TABLE_W = RIGHT - LEFT;
    private static final float[] COL = { 30, 36, 38, 235, 68, 78 };
    private static final PDType1Font REG = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final int NAVY_R = 16, NAVY_G = 52, NAVY_B = 96;

    private CotizacionPdfLayout() {}

    static byte[] generar(CotizacionResponse cotizacion, List<CotizacionDetalleResponse> lineas) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Pagina pagina = new Pagina(doc);
            portada(pagina, cotizacion);
            encabezadoTabla(pagina);
            int item = 10;
            for (CotizacionDetalleResponse linea : lineas) {
                List<String> descripcion = descripcion(linea);
                float rowHeight = Math.max(44, 18 + descripcion.size() * 13);
                if (pagina.y - rowHeight < 82) {
                    pagina.nueva();
                    encabezadoTabla(pagina);
                }
                fila(pagina, item, linea, descripcion, rowHeight);
                item += 10;
            }
            if (pagina.y < 250) pagina.nueva();
            totalesYCondiciones(pagina, cotizacion);
            pagina.cerrar();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void portada(Pagina p, CotizacionResponse c) throws IOException {
        PdfBranding.logo(p.doc, p.cs, LEFT, H - 104, 205, 61);
        p.y = H - 128;
        p.textCentered(BOLD, 14, "COTIZACIÓN: " + valor(c.getCodigoCotizacion()), W / 2, p.y);
        p.line(LEFT + 145, p.y - 4, RIGHT - 145, p.y - 4);
        p.y -= 28;
        p.textRight(BOLD, 10, "FECHA: " + fechaLarga(c.getFechaCotizacion()), RIGHT, p.y);
        p.y -= 28;
        p.text(REG, 10, "Señores:", LEFT, p.y); p.y -= 15;
        p.text(BOLD, 11, valor(c.getClienteRazonSocial()), LEFT, p.y); p.y -= 18;
        p.y -= 7;
        p.text(BOLD, 10, "Vencimiento", LEFT, p.y);
        p.text(REG, 10, ":   " + fechaLarga(c.getFechaValidez()), LEFT + 83, p.y); p.y -= 23;
        p.text(REG, 10, "Por la presente nos dirigimos a ustedes para hacerles llegar nuestra cotización por el siguiente", LEFT, p.y); p.y -= 14;
        p.text(REG, 10, "material:", LEFT, p.y); p.y -= 21;
    }

    private static void encabezadoTabla(Pagina p) throws IOException {
        float top = p.y;
        float height = 19;
        p.cs.setNonStrokingColor(231, 235, 241);
        p.cs.addRect(LEFT, top - height, TABLE_W, height);
        p.cs.fill();
        String[] labels = { "ITEM", "CANT", "U/M", "DESCRIPCIÓN", "P. UNITARIO", "TOTAL" };
        float x = LEFT;
        for (int i = 0; i < COL.length; i++) {
            p.cs.setStrokingColor(35, 45, 58); p.cs.setLineWidth(.55f);
            p.cs.addRect(x, top - height, COL[i], height); p.cs.stroke();
            p.textCentered(BOLD, 7.5f, labels[i], x + COL[i] / 2, top - 12.5f);
            x += COL[i];
        }
        p.y = top - height;
    }

    private static void fila(Pagina p, int item, CotizacionDetalleResponse l, List<String> desc, float height) throws IOException {
        float top = p.y, bottom = top - height, x = LEFT;
        for (float width : COL) {
            p.cs.setStrokingColor(35, 45, 58); p.cs.setLineWidth(.55f);
            p.cs.addRect(x, bottom, width, height); p.cs.stroke(); x += width;
        }
        float middle = bottom + height / 2 - 3;
        p.textCentered(REG, 8, String.valueOf(item), LEFT + COL[0] / 2, middle);
        p.textCentered(REG, 8, numero(l.getCantidad()), LEFT + COL[0] + COL[1] / 2, middle);
        p.textCentered(REG, 8, recortar(valor(l.getUnidadMedida()), 7), LEFT + COL[0] + COL[1] + COL[2] / 2, middle);
        float dx = LEFT + COL[0] + COL[1] + COL[2] + 5;
        float ty = top - 14;
        for (int i = 0; i < desc.size(); i++) {
            p.text(i == 0 ? BOLD : REG, 8, desc.get(i), dx, ty);
            ty -= 12;
        }
        p.textRight(REG, 8, dinero(l.getPrecioUnitario()), LEFT + TABLE_W - COL[5] - 5, middle);
        p.textRight(REG, 8, dinero(l.getImporteLinea()), RIGHT - 5, middle);
        p.y = bottom;
    }

    private static void totalesYCondiciones(Pagina p, CotizacionResponse c) throws IOException {
        float top = p.y;
        p.cs.setStrokingColor(35, 45, 58); p.cs.setLineWidth(.55f);
        p.cs.addRect(LEFT, top - 20, TABLE_W, 20); p.cs.stroke();
        p.textRight(BOLD, 9, dinero(c.getSubtotal()), RIGHT - 6, top - 14);
        p.y = top - 34;
        p.text(BOLD, 11, "TOTAL COTIZACIÓN " + moneda(c.getMoneda()) + " " + dinero(c.getSubtotal()) + " + IGV (18%)", LEFT, p.y);
        p.y -= 25;
        detalle(p, "Moneda", moneda(c.getMoneda()));
        detalle(p, "I.G.V. (18%)", moneda(c.getMoneda()) + " " + dinero(c.getIgv()));
        detalle(p, "Total con I.G.V.", moneda(c.getMoneda()) + " " + dinero(c.getTotal()));
        detalle(p, "Validez de la oferta", fechaLarga(c.getFechaValidez()));
        if (c.getPlazoEntregaEstimadoDias() != null) detalle(p, "Plazo de entrega", c.getPlazoEntregaEstimadoDias() + " días (referencial)");
        if (c.getObservaciones() != null && !c.getObservaciones().isBlank()) {
            p.y -= 12;
            p.text(BOLD, 9, "Observaciones:", LEFT, p.y); p.y -= 15;
            for (String text : wrap(c.getObservaciones(), REG, 9, TABLE_W)) {
                p.text(REG, 9, text, LEFT, p.y); p.y -= 13;
            }
        }
        p.y -= 20;
        p.text(REG, 10, "Atentamente,", LEFT, p.y); p.y -= 40;
        p.text(BOLD, 10, "Administrador del Sistema", LEFT, p.y); p.y -= 14;
        p.text(REG, 9, "Área Comercial", LEFT, p.y); p.y -= 13;
        p.text(REG, 9, "ventas1@genlogsac.com", LEFT, p.y); p.y -= 14;
        p.text(BOLD, 10, "GENERAL LOGISTIC SOLUTIONS S.A.C.", LEFT, p.y); p.y -= 14;
        p.text(REG, 9, "RUC 20516185644", LEFT, p.y);
    }

    private static void detalle(Pagina p, String titulo, String valor) throws IOException {
        p.text(REG, 10, titulo, LEFT, p.y);
        p.text(REG, 10, ":   " + valor, LEFT + 124, p.y);
        p.y -= 17;
    }

    private static List<String> descripcion(CotizacionDetalleResponse l) {
        String nombre = l.getProductoNombre() != null ? l.getProductoNombre()
                : l.getServicioNombre() != null ? l.getServicioNombre() : "Ítem de cotización";
        List<String> out = new ArrayList<>(wrap(nombre, BOLD, 8, COL[3] - 10));
        String personalizada = l.getDescripcionPersonalizada();
        if (personalizada != null && !personalizada.isBlank() && !personalizada.equalsIgnoreCase(nombre)) {
            out.addAll(wrap("DESCRIPCIÓN: " + personalizada, REG, 8, COL[3] - 10));
        }
        return out;
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : limpiar(text).split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            try {
                if (font.getStringWidth(candidate) / 1000 * size > maxWidth && line.length() > 0) {
                    lines.add(line.toString()); line.setLength(0); line.append(word);
                } else {
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
            } catch (IOException e) { /* Las fuentes estándar siempre permiten medir el texto saneado. */ }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static String moneda(String s) {
        if (s == null || s.isBlank()) return "PEN";
        return s.equalsIgnoreCase("S/") ? "PEN" : s;
    }
    private static String fechaLarga(java.time.LocalDate d) { return d == null ? "No especificada" : d.format(java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-PE"))); }
    private static String numero(BigDecimal n) { return n == null ? "-" : n.stripTrailingZeros().toPlainString(); }
    private static String dinero(BigDecimal n) { return n == null ? "0.00" : n.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String valor(String s) { return s == null || s.isBlank() ? "-" : s; }
    private static String recortar(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
    private static String limpiar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c >= 32 && c <= 255 ? c : ' ');
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    private static final class Pagina {
        final PDDocument doc; PDPageContentStream cs; PDPage page; float y; int numero = 0;
        Pagina(PDDocument doc) throws IOException { this.doc = doc; nueva(); }
        void nueva() throws IOException {
            cerrarPagina(); numero++;
            page = new PDPage(PDRectangle.A4); doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            PdfBranding.logo(doc, cs, LEFT, H - 104, 205, 61);
            y = H - 128;
        }
        void text(PDType1Font font, float size, String text, float x, float baseline) throws IOException {
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, baseline); cs.showText(limpiar(text)); cs.endText();
        }
        void textCentered(PDType1Font font, float size, String text, float center, float baseline) throws IOException {
            String safe = limpiar(text); text(font, size, safe, center - font.getStringWidth(safe) / 1000 * size / 2, baseline);
        }
        void textRight(PDType1Font font, float size, String text, float right, float baseline) throws IOException {
            String safe = limpiar(text); text(font, size, safe, right - font.getStringWidth(safe) / 1000 * size, baseline);
        }
        void line(float x1, float y1, float x2, float y2) throws IOException { cs.setStrokingColor(NAVY_R, NAVY_G, NAVY_B); cs.setLineWidth(.8f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke(); }
        void cerrarPagina() throws IOException {
            if (cs == null) return;
            cs.setNonStrokingColor(75, 83, 95);
            text(REG, 8, "Jr. República de Ecuador Nro. 1025, Lima", W / 2 - 78, 37);
            text(REG, 8, "ventas1@genlogsac.com  -  +51 946 563 252", W / 2 - 91, 25);
            text(REG, 8, "Página " + numero, RIGHT - 45, 18);
            cs.close(); cs = null;
        }
        void cerrar() throws IOException { cerrarPagina(); }
    }
}



