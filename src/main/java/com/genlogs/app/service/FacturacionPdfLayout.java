package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.genlogs.app.dto.DetalleFacturacionResponse;
import com.genlogs.app.dto.FacturacionResponse;

/**
 * PDF de la facturación (RF-36, RF-37) con el mismo formato corporativo que la cotización:
 * datos del comprobante, líneas, desglose IGV + IPM con la tasa guardada y estado de cobranza.
 * Es una representación interna: no reemplaza al comprobante electrónico ante SUNAT.
 */
final class FacturacionPdfLayout {
    private static final float W = PDRectangle.A4.getWidth();
    private static final float H = PDRectangle.A4.getHeight();
    private static final float LEFT = 55;
    private static final float RIGHT = W - 55;
    private static final float TABLE_W = RIGHT - LEFT;
    /** ITEM, CANT, DESCRIPCIÓN, P. UNITARIO, DSCTO, IMPORTE. */
    private static final float[] COL = { 30, 40, 226, 64, 52, 73 };
    private static final PDType1Font REG = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final int NAVY_R = 16, NAVY_G = 52, NAVY_B = 96;

    private FacturacionPdfLayout() {}

    // PDFBox 3 recibe los colores en escala 0..1 (con 0..255 lanza IllegalArgumentException).
    private static void trazo(PDPageContentStream cs, int r, int g, int b) throws IOException {
        cs.setStrokingColor(r / 255f, g / 255f, b / 255f);
    }

    private static void relleno(PDPageContentStream cs, int r, int g, int b) throws IOException {
        cs.setNonStrokingColor(r / 255f, g / 255f, b / 255f);
    }

    static byte[] generar(FacturacionResponse factura, List<DetalleFacturacionResponse> lineas) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Pagina pagina = new Pagina(doc);
            cabecera(pagina, factura);
            encabezadoTabla(pagina);
            int item = 1;
            for (DetalleFacturacionResponse linea : lineas) {
                List<String> descripcion = descripcion(linea);
                float alto = Math.max(26, 12 + descripcion.size() * 12);
                if (pagina.y - alto < 82) {
                    pagina.nueva();
                    encabezadoTabla(pagina);
                }
                fila(pagina, item++, linea, descripcion, alto);
            }
            if (pagina.y < 260) pagina.nueva();
            totales(pagina, factura);
            pagina.cerrar();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void cabecera(Pagina p, FacturacionResponse f) throws IOException {
        p.textCentered(BOLD, 14, valor(f.getTipoComprobante()).toUpperCase(Locale.ROOT) + ": " + valor(f.getCodigoComprobante()), W / 2, p.y);
        p.line(LEFT + 130, p.y - 4, RIGHT - 130, p.y - 4);
        p.y -= 28;
        p.textRight(BOLD, 10, "FECHA DE EMISIÓN: " + fechaLarga(f.getFechaEmision()), RIGHT, p.y);
        p.y -= 26;
        p.text(REG, 10, "Cliente:", LEFT, p.y); p.y -= 15;
        p.text(BOLD, 11, valor(f.getClienteRazonSocial() != null ? f.getClienteRazonSocial() : f.getCliente()), LEFT, p.y); p.y -= 22;
        dato(p, "Orden de compra", valor(f.getNumeroOrdenCompra()));
        dato(p, "Vencimiento", fechaLarga(f.getFechaVencimiento()));
        dato(p, "Moneda", moneda(f));
        dato(p, "Estado", valor(f.getEstado()));
        p.y -= 8;
    }

    private static void encabezadoTabla(Pagina p) throws IOException {
        float top = p.y, alto = 19;
        relleno(p.cs, 231, 235, 241);
        p.cs.addRect(LEFT, top - alto, TABLE_W, alto);
        p.cs.fill();
        relleno(p.cs, 0, 0, 0);
        String[] titulos = { "ITEM", "CANT", "DESCRIPCIÓN", "P. UNITARIO", "DSCTO", "IMPORTE" };
        float x = LEFT;
        for (int i = 0; i < COL.length; i++) {
            trazo(p.cs, 35, 45, 58); p.cs.setLineWidth(.55f);
            p.cs.addRect(x, top - alto, COL[i], alto); p.cs.stroke();
            p.textCentered(BOLD, 7.5f, titulos[i], x + COL[i] / 2, top - 12.5f);
            x += COL[i];
        }
        p.y = top - alto;
    }

    private static void fila(Pagina p, int item, DetalleFacturacionResponse l, List<String> desc, float alto) throws IOException {
        float top = p.y, bottom = top - alto, x = LEFT;
        for (float ancho : COL) {
            trazo(p.cs, 35, 45, 58); p.cs.setLineWidth(.55f);
            p.cs.addRect(x, bottom, ancho, alto); p.cs.stroke(); x += ancho;
        }
        float medio = bottom + alto / 2 - 3;
        p.textCentered(REG, 8, String.valueOf(item), LEFT + COL[0] / 2, medio);
        p.textCentered(REG, 8, numero(l.getCantidad()), LEFT + COL[0] + COL[1] / 2, medio);
        float ty = top - 11;
        // El nombre (aunque ocupe varias líneas) va en negrita; la descripción adicional, normal.
        int lineasNombre = wrap(nombreDe(l), BOLD, 8, COL[2] - 10).size();
        for (int i = 0; i < desc.size(); i++) {
            p.text(i < lineasNombre ? BOLD : REG, 8, desc.get(i), LEFT + COL[0] + COL[1] + 5, ty);
            ty -= 12;
        }
        float finPrecio = LEFT + COL[0] + COL[1] + COL[2] + COL[3];
        p.textRight(REG, 8, dinero(l.getPrecioUnitario()), finPrecio - 5, medio);
        p.textRight(REG, 8, dinero(l.getDescuentoUnitario()), finPrecio + COL[4] - 5, medio);
        p.textRight(REG, 8, dinero(l.getImporteLinea()), RIGHT - 5, medio);
        p.y = bottom;
    }

    private static void totales(Pagina p, FacturacionResponse f) throws IOException {
        String m = moneda(f) + " ";
        p.y -= 14;
        importe(p, "Subtotal", m + dinero(f.getSubtotal()), false);
        importe(p, "I.G.V. (" + porcentaje(f.getPorcentajeIgv()) + ")", m + dinero(f.getMontoIgv()), false);
        importe(p, "I.P.M. (" + porcentaje(f.getPorcentajeIpm()) + ")", m + dinero(f.getMontoIpm()), false);
        importe(p, "TOTAL (tributos " + porcentaje(f.getTasaTotal()) + ")", m + dinero(f.getTotal()), true);
        p.y -= 6;
        importe(p, "Pagado", m + dinero(f.getMontoPagado()), false);
        importe(p, "Saldo pendiente", m + dinero(f.getSaldoPendiente()), true);

        p.y -= 24;
        relleno(p.cs, 90, 98, 110);
        for (String linea : wrap("Documento de control interno de GENLOGS. No reemplaza al comprobante "
                + "electrónico emitido ante SUNAT.", REG, 8, TABLE_W)) {
            p.text(REG, 8, linea, LEFT, p.y); p.y -= 11;
        }
        relleno(p.cs, 0, 0, 0);
        p.y -= 16;
        p.text(BOLD, 10, "GENERAL LOGISTIC SOLUTIONS S.A.C.", LEFT, p.y); p.y -= 14;
        p.text(REG, 9, "RUC 20516185644", LEFT, p.y);
    }

    /** Fila de importe alineada a la derecha, sobre el ancho de las tres últimas columnas. */
    private static void importe(Pagina p, String titulo, String valor, boolean resaltar) throws IOException {
        PDType1Font fuente = resaltar ? BOLD : REG;
        float inicio = RIGHT - 230;
        p.text(fuente, 9.5f, titulo, inicio, p.y);
        p.textRight(fuente, 9.5f, valor, RIGHT - 5, p.y);
        p.y -= 15;
    }

    private static void dato(Pagina p, String titulo, String valor) throws IOException {
        p.text(BOLD, 10, titulo, LEFT, p.y);
        p.text(REG, 10, ":   " + valor, LEFT + 110, p.y);
        p.y -= 16;
    }

    private static String nombreDe(DetalleFacturacionResponse l) {
        return l.getProductoNombre() != null ? l.getProductoNombre()
                : l.getServicioNombre() != null ? l.getServicioNombre() : "Ítem facturado";
    }

    private static List<String> descripcion(DetalleFacturacionResponse l) {
        String nombre = nombreDe(l);
        float ancho = COL[2] - 10;
        List<String> out = new ArrayList<>(wrap(nombre, BOLD, 8, ancho));
        String personalizada = l.getDescripcionPersonalizada();
        if (personalizada != null && !personalizada.isBlank() && !personalizada.equalsIgnoreCase(nombre)) {
            out.addAll(wrap(personalizada, REG, 8, ancho));
        }
        return out;
    }

    private static List<String> wrap(String texto, PDType1Font fuente, float tam, float maxAncho) {
        List<String> lineas = new ArrayList<>();
        StringBuilder linea = new StringBuilder();
        for (String palabra : limpiar(texto).split("\\s+")) {
            String candidata = linea.length() == 0 ? palabra : linea + " " + palabra;
            try {
                if (fuente.getStringWidth(candidata) / 1000 * tam > maxAncho && linea.length() > 0) {
                    lineas.add(linea.toString()); linea.setLength(0); linea.append(palabra);
                } else {
                    if (linea.length() > 0) linea.append(' ');
                    linea.append(palabra);
                }
            } catch (IOException e) { /* Las fuentes estándar siempre permiten medir el texto saneado. */ }
        }
        if (linea.length() > 0) lineas.add(linea.toString());
        return lineas;
    }

    /** Código ISO de la moneda a partir del símbolo que guarda la BD (S/, $, €). */
    private static String moneda(FacturacionResponse f) {
        String s = f.getMoneda() == null ? "" : f.getMoneda().trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "", "S/", "S/.", "SOL", "SOLES", "PEN" -> "PEN";
            case "$", "US$", "USD" -> "USD";
            case "€", "EUR" -> "EUR";
            default -> s;
        };
    }

    private static String porcentaje(BigDecimal v) { return v == null ? "-" : v.stripTrailingZeros().toPlainString().replace('.', ',') + " %"; }
    private static String fechaLarga(LocalDate d) { return d == null ? "No especificada" : d.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"))); }
    private static String numero(BigDecimal n) { return n == null ? "-" : n.stripTrailingZeros().toPlainString(); }
    private static String dinero(BigDecimal n) { return n == null ? "0.00" : n.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String valor(String s) { return s == null || s.isBlank() ? "-" : s; }

    /** Solo caracteres que Helvetica (WinAnsi) puede dibujar; el resto rompería PDFBox con un 500. */
    private static String limpiar(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '–', '—', '−', '•' -> b.append('-');
                case '‘', '’', '´' -> b.append('\'');
                case '“', '”' -> b.append('"');
                case '…' -> b.append("...");
                case '€' -> b.append("EUR");
                default -> b.append((c >= 32 && c <= 126) || (c >= 161 && c <= 255 && c != 173) ? c : ' ');
            }
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    private static final class Pagina {
        final PDDocument doc; PDPageContentStream cs; float y; int numero = 0;
        Pagina(PDDocument doc) throws IOException { this.doc = doc; nueva(); }
        void nueva() throws IOException {
            cerrarPagina(); numero++;
            PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
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
        void line(float x1, float y1, float x2, float y2) throws IOException { trazo(cs, NAVY_R, NAVY_G, NAVY_B); cs.setLineWidth(.8f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke(); }
        void cerrarPagina() throws IOException {
            if (cs == null) return;
            relleno(cs, 75, 83, 95);
            text(REG, 8, "Jr. República de Ecuador Nro. 1025, Lima", W / 2 - 78, 37);
            text(REG, 8, "ventas1@genlogsac.com  -  +51 946 563 252", W / 2 - 91, 25);
            text(REG, 8, "Página " + numero, RIGHT - 45, 18);
            cs.close(); cs = null;
        }
        void cerrar() throws IOException { cerrarPagina(); }
    }
}
