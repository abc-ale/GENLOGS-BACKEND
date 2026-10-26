package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backend del módulo de Reportes de Luana. Antes de esto, el frontend
 * (ReportesPage.tsx -> useGenerarReporte -> reportesApi.generarReporte)
 * llamaba a POST /api/reportes/generar y el backend no tenía NADA bajo ese
 * path: 404 garantizado. Esta clase arma el archivo (Excel con Apache POI o
 * PDF con PDFBox) para los 5 tipos de reporte que el frontend ya sabe pedir.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CotizacionRepository cotizacionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturacionRepository facturacionRepository;
    private final ServicioRepository servicioRepository;
    private final ProductoRepository productoRepository;

    public record ArchivoGenerado(byte[] contenido, String nombreArchivo, String contentType) {}

    @Transactional(readOnly = true)
    public ArchivoGenerado generar(ReporteRequest request) {
        if (request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new BusinessException("La fecha de inicio no puede ser mayor a la fecha fin");
        }

        String[][] filas = construirFilas(request);
        String[] encabezados = filas[0];
        String[][] datos = new String[filas.length - 1][];
        System.arraycopy(filas, 1, datos, 0, filas.length - 1);

        String nombreBase = "reporte-" + request.getTipoReporte().name().toLowerCase()
                + "-" + request.getFechaInicio() + "_" + request.getFechaFin();

        if (request.getFormato() == ReporteRequest.FormatoReporte.EXCEL) {
            byte[] bytes = generarExcel(encabezados, datos, request.getTipoReporte().name());
            return new ArchivoGenerado(bytes, nombreBase + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            byte[] bytes = generarPdf(encabezados, datos, request.getTipoReporte().name(), request.getFechaInicio(), request.getFechaFin());
            return new ArchivoGenerado(bytes, nombreBase + ".pdf", "application/pdf");
        }
    }

    // ------------------------------------------------------------------
    // Armado de filas por tipo de reporte
    // ------------------------------------------------------------------

    private String[][] construirFilas(ReporteRequest req) {
        LocalDate desde = req.getFechaInicio();
        LocalDate hasta = req.getFechaFin();

        return switch (req.getTipoReporte()) {
            case COTIZACIONES -> filasCotizaciones(desde, hasta);
            case ORDENES_COMPRA -> filasOrdenesCompra(desde, hasta);
            case FACTURACION -> filasFacturacion(desde, hasta);
            case SERVICIOS -> filasServicios();
            case PRODUCTOS -> filasProductos();
        };
    }

    private String[][] filasCotizaciones(LocalDate desde, LocalDate hasta) {
        List<Cotizacion> cotizaciones = cotizacionRepository.findAll().stream()
                .filter(c -> "A".equals(c.getStatus()))
                .filter(c -> !c.getFechaCotizacion().isBefore(desde) && !c.getFechaCotizacion().isAfter(hasta))
                .sorted((a, b) -> a.getFechaCotizacion().compareTo(b.getFechaCotizacion()))
                .toList();

        String[][] filas = new String[cotizaciones.size() + 1][];
        filas[0] = new String[] { "Código", "Cliente", "Fecha", "Estado", "Moneda", "Total" };
        for (int i = 0; i < cotizaciones.size(); i++) {
            Cotizacion c = cotizaciones.get(i);
            var totales = cotizacionRepository.findTotalesByCotizacion(c.getIdCotizacion()).orElse(null);
            filas[i + 1] = new String[] {
                    c.getCodigoCotizacion(),
                    c.getCliente().getTercero().getRazonSocial(),
                    c.getFechaCotizacion().format(FMT),
                    c.getEstadoCotizacion().getNombreEstado(),
                    c.getMoneda().getSimbolo(),
                    totales != null ? totales.getTotal().toPlainString() : "0.00",
            };
        }
        return filas;
    }

    private String[][] filasOrdenesCompra(LocalDate desde, LocalDate hasta) {
        List<OrdenCompra> ordenes = ordenCompraRepository.findAll().stream()
                .filter(o -> "A".equals(o.getStatus()))
                .filter(o -> !o.getFechaRecepcion().isBefore(desde) && !o.getFechaRecepcion().isAfter(hasta))
                .sorted((a, b) -> a.getFechaRecepcion().compareTo(b.getFechaRecepcion()))
                .toList();

        String[][] filas = new String[ordenes.size() + 1][];
        filas[0] = new String[] { "Número", "Cotización origen", "Cliente", "Fecha recepción", "Estado" };
        for (int i = 0; i < ordenes.size(); i++) {
            OrdenCompra o = ordenes.get(i);
            filas[i + 1] = new String[] {
                    o.getNumeroOrdenCompra(),
                    o.getCotizacion().getCodigoCotizacion(),
                    o.getCotizacion().getCliente().getTercero().getRazonSocial(),
                    o.getFechaRecepcion().format(FMT),
                    o.getEstadoOrdenCompra().getNombreEstado(),
            };
        }
        return filas;
    }

    private String[][] filasFacturacion(LocalDate desde, LocalDate hasta) {
        List<Facturacion> facturas = facturacionRepository.findAll().stream()
                .filter(f -> "A".equals(f.getStatus()))
                .filter(f -> !f.getFechaEmision().isBefore(desde) && !f.getFechaEmision().isAfter(hasta))
                .sorted((a, b) -> a.getFechaEmision().compareTo(b.getFechaEmision()))
                .toList();

        String[][] filas = new String[facturas.size() + 1][];
        filas[0] = new String[] { "Comprobante", "Cliente", "Fecha emisión", "Fecha vencimiento", "Monto pagado", "Estado" };
        for (int i = 0; i < facturas.size(); i++) {
            Facturacion f = facturas.get(i);
            filas[i + 1] = new String[] {
                    f.getSerieComprobante() + "-" + f.getNumeroComprobante(),
                    f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial(),
                    f.getFechaEmision().format(FMT),
                    f.getFechaVencimiento().format(FMT),
                    f.getMontoPagado().toPlainString(),
                    f.getEstadoFacturacion().getNombreEstado(),
            };
        }
        return filas;
    }

    /** Servicios y Productos no tienen una fecha de negocio propia para filtrar
     *  por rango (son catálogo, no movimiento); se listan completos (activos). */
    private String[][] filasServicios() {
        List<Servicio> servicios = servicioRepository.findAll().stream()
                .filter(s -> "A".equals(s.getStatus()))
                .sorted((a, b) -> a.getNombreServicio().compareTo(b.getNombreServicio()))
                .toList();

        String[][] filas = new String[servicios.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Unidad de medida", "Visible web" };
        for (int i = 0; i < servicios.size(); i++) {
            Servicio s = servicios.get(i);
            filas[i + 1] = new String[] {
                    s.getCodigoServicio(),
                    s.getNombreServicio(),
                    s.getCategoriaServicio().getNombreCategoria(),
                    s.getUnidadMedida().getNombreUnidad(),
                    Boolean.TRUE.equals(s.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
    }

    private String[][] filasProductos() {
        List<Producto> productos = productoRepository.findAll().stream()
                .filter(p -> "A".equals(p.getStatus()))
                .sorted((a, b) -> a.getNombreProducto().compareTo(b.getNombreProducto()))
                .toList();

        String[][] filas = new String[productos.size() + 1][];
        filas[0] = new String[] { "Código", "Nombre", "Categoría", "Marca", "Visible web" };
        for (int i = 0; i < productos.size(); i++) {
            Producto p = productos.get(i);
            filas[i + 1] = new String[] {
                    p.getCodigoProducto(),
                    p.getNombreProducto(),
                    p.getCategoriaProducto().getNombreCategoria(),
                    p.getMarca().getNombreMarca(),
                    Boolean.TRUE.equals(p.getVisibleWeb()) ? "Sí" : "No",
            };
        }
        return filas;
    }

    // ------------------------------------------------------------------
    // Generación de archivos
    // ------------------------------------------------------------------

    private byte[] generarExcel(String[] encabezados, String[][] datos, String nombreHoja) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(nombreHoja);

            CellStyle estiloEncabezado = workbook.createCellStyle();
            Font fuenteEncabezado = workbook.createFont();
            fuenteEncabezado.setBold(true);
            estiloEncabezado.setFont(fuenteEncabezado);

            Row filaEncabezado = sheet.createRow(0);
            for (int col = 0; col < encabezados.length; col++) {
                Cell celda = filaEncabezado.createCell(col);
                celda.setCellValue(encabezados[col]);
                celda.setCellStyle(estiloEncabezado);
            }

            for (int i = 0; i < datos.length; i++) {
                Row fila = sheet.createRow(i + 1);
                for (int col = 0; col < datos[i].length; col++) {
                    fila.createCell(col).setCellValue(datos[i][col]);
                }
            }

            for (int col = 0; col < encabezados.length; col++) {
                sheet.autoSizeColumn(col);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel del reporte", e);
        }
    }

    private byte[] generarPdf(String[] encabezados, String[][] datos, String titulo,
            LocalDate fechaInicio, LocalDate fechaFin) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean apaisado = encabezados.length >= 5;
            PDRectangle formato = apaisado
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float margen = 34;
            float anchoTabla = formato.getWidth() - margen * 2;
            float[] anchos = calcularAnchosColumnas(encabezados, datos, anchoTabla);
            float y = abrirPagina(documento, formato, fuenteTitulo, titulo, fechaInicio, fechaFin,
                    encabezados, anchos, margen, 1);
            int numeroPagina = 1;

            for (int fila = 0; fila < datos.length; fila++) {
                List<List<String>> celdas = new java.util.ArrayList<>();
                float altoContenido = 0;
                for (int columna = 0; columna < encabezados.length; columna++) {
                    List<String> lineas = ajustarTexto(datos[fila][columna], fuenteTexto, 8.5f,
                            anchos[columna] - 12, 5);
                    celdas.add(lineas);
                    altoContenido = Math.max(altoContenido, lineas.size() * 11f);
                }
                float altoFila = Math.max(24, altoContenido + 10);
                if (y - altoFila < 34) {
                    numeroPagina++;
                    y = abrirPagina(documento, formato, fuenteTitulo, titulo, fechaInicio, fechaFin,
                            encabezados, anchos, margen, numeroPagina);
                }
                dibujarFila(documento.getPage(documento.getNumberOfPages() - 1), documento,
                        celdas, anchos, margen, y, altoFila, fuenteTexto, fila % 2 == 0);
                y -= altoFila;
            }

            for (PDPage pagina : documento.getPages()) {
                try (PDPageContentStream pie = new PDPageContentStream(documento, pagina,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    pie.setFont(fuenteTexto, 8);
                    pie.setNonStrokingColor(new Color(100, 116, 139));
                    pie.beginText();
                    pie.newLineAtOffset(margen, 20);
                    pie.showText("GENLOGS S.A.C. | RUC 20516185644");
                    pie.endText();
                }
            }
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo PDF del reporte", e);
        }
    }

    private float abrirPagina(PDDocument documento, PDRectangle formato, PDType1Font fuenteTitulo,
            String titulo, LocalDate fechaInicio, LocalDate fechaFin, String[] encabezados,
            float[] anchos, float margen, int numeroPagina) throws IOException {
        PDPage pagina = new PDPage(formato);
        documento.addPage(pagina);
        float alto = formato.getHeight();
        try (PDPageContentStream cs = new PDPageContentStream(documento, pagina)) {
            PdfBranding.logo(documento, cs, margen, alto - 57, 72, 28);
            cs.setNonStrokingColor(new Color(16, 52, 96));
            cs.setFont(fuenteTitulo, 16);
            cs.beginText();
            cs.newLineAtOffset(margen + 84, alto - 39);
            cs.showText("Reporte GENLOGS - " + textoPdf(titulo));
            cs.endText();
            cs.setNonStrokingColor(new Color(90, 105, 125));
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
            cs.beginText();
            cs.newLineAtOffset(margen + 84, alto - 54);
            cs.showText("Periodo: " + fechaInicio.format(FMT) + " al " + fechaFin.format(FMT)
                    + " | Pagina " + numeroPagina);
            cs.endText();

            float altoEncabezado = 28;
            float x = margen;
            float top = alto - 76;
            for (int i = 0; i < anchos.length; i++) {
                cs.setNonStrokingColor(new Color(16, 52, 96));
                cs.addRect(x, top - altoEncabezado, anchos[i], altoEncabezado);
                cs.fill();
                cs.setFont(fuenteTitulo, 8.5f);
                cs.setNonStrokingColor(Color.WHITE);
                List<String> lineas = ajustarTexto(encabezados[i], fuenteTitulo, 8.5f, anchos[i] - 10, 2);
                float textoY = top - 11 - (lineas.size() - 1) * 9;
                for (String linea : lineas) {
                    cs.beginText();
                    cs.newLineAtOffset(x + 5, textoY);
                    cs.showText(linea);
                    cs.endText();
                    textoY -= 9;
                }
                x += anchos[i];
            }
        }
        return alto - 76 - 28;
    }

    private float[] calcularAnchosColumnas(String[] encabezados, String[][] datos, float anchoTabla)
            throws IOException {
        float[] pesos = new float[encabezados.length];
        float suma = 0;
        for (int columna = 0; columna < encabezados.length; columna++) {
            int maxCaracteres = encabezados[columna].length();
            for (String[] fila : datos) {
                if (columna < fila.length && fila[columna] != null) {
                    maxCaracteres = Math.max(maxCaracteres, Math.min(fila[columna].length(), 42));
                }
            }
            pesos[columna] = Math.max(7, Math.min(maxCaracteres, 42));
            suma += pesos[columna];
        }
        float[] anchos = new float[encabezados.length];
        float usado = 0;
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = i == anchos.length - 1 ? anchoTabla - usado : anchoTabla * pesos[i] / suma;
            usado += anchos[i];
        }
        return anchos;
    }

    private void dibujarFila(PDPage pagina, PDDocument documento, List<List<String>> celdas,
            float[] anchos, float margen, float top, float alto, PDType1Font fuente, boolean alternada)
            throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(documento, pagina,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            float x = margen;
            for (int columna = 0; columna < anchos.length; columna++) {
                cs.setNonStrokingColor(alternada ? new Color(245, 248, 252) : Color.WHITE);
                cs.setStrokingColor(new Color(218, 226, 237));
                cs.setLineWidth(0.45f);
                cs.addRect(x, top - alto, anchos[columna], alto);
                cs.fillAndStroke();
                cs.setFont(fuente, 8.5f);
                cs.setNonStrokingColor(new Color(35, 45, 58));
                float textoY = top - 14;
                for (String linea : celdas.get(columna)) {
                    cs.beginText();
                    cs.newLineAtOffset(x + 5, textoY);
                    cs.showText(linea);
                    cs.endText();
                    textoY -= 11;
                }
                x += anchos[columna];
            }
        }
    }

    private List<String> ajustarTexto(String valor, PDType1Font fuente, float tamano,
            float anchoMaximo, int maxLineas) throws IOException {
        String texto = textoPdf(valor == null ? "" : valor).trim();
        List<String> lineas = new java.util.ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(candidato) * tamano / 1000f <= anchoMaximo) {
                actual.setLength(0);
                actual.append(candidato);
            } else {
                if (!actual.isEmpty()) lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
                if (lineas.size() == maxLineas) break;
            }
        }
        if (!actual.isEmpty() && lineas.size() < maxLineas) lineas.add(actual.toString());
        if (lineas.isEmpty()) lineas.add("");
        if (lineas.size() == maxLineas && !texto.endsWith(lineas.get(lineas.size() - 1))) {
            String ultima = lineas.get(lineas.size() - 1);
            while (!ultima.isEmpty() && fuente.getStringWidth(ultima + "...") * tamano / 1000f > anchoMaximo) {
                ultima = ultima.substring(0, ultima.length() - 1);
            }
            lineas.set(lineas.size() - 1, ultima + "...");
        }
        return lineas;
    }

    private String textoPdf(String texto) {
        StringBuilder limpio = new StringBuilder();
        for (char caracter : texto.toCharArray()) {
            limpio.append(caracter >= 32 && caracter <= 255 ? caracter : '?');
        }
        return limpio.toString();
    }
}


