package com.genlogs.app.service;

import com.genlogs.app.dto.CargaMasivaProductosResponse;
import com.genlogs.app.dto.CargaMasivaProductosResponse.ErrorFila;
import com.genlogs.app.dto.ProductoRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Carga masiva del catálogo de productos desde un CSV (RF-10): registra productos nuevos y
 * actualiza los existentes por su código. Primero valida el archivo completo; si alguna fila
 * tiene errores no se guarda nada y se devuelve la lista para corregir y volver a subir.
 *
 * <p>Columnas (la cabecera es obligatoria; no importan mayúsculas, tildes ni el orden):
 * codigo*, nombre*, categoria*, unidad_medida*, marca, procedencia, stock, visible_web,
 * descripcion. La categoría se busca por nombre o slug, la marca por nombre y la unidad por
 * código o nombre. En un producto existente, una celda opcional vacía conserva el valor actual.
 * Las características técnicas, imágenes y documentos se agregan después desde la ficha.</p>
 */
@Service
@RequiredArgsConstructor
public class CargaMasivaProductosService {

    /** Límite de filas por archivo, para no bloquear el servidor con un archivo enorme. */
    static final int MAXIMO_FILAS = 2000;

    public static final String PLANTILLA = "codigo,nombre,categoria,unidad_medida,marca,procedencia,stock,visible_web,descripcion\n"
            + "BMB-001,Bomba centrífuga de 2 pulgadas,Bombas industriales,UND,Grundfos,Dinamarca,0,si,Bomba para agua limpia\n";

    private static final List<String> OBLIGATORIAS = List.of("codigo", "nombre", "categoria", "unidad_medida");
    private static final Set<String> CONOCIDAS = Set.of(
            "codigo", "nombre", "categoria", "unidad_medida", "marca", "procedencia", "stock", "visible_web", "descripcion");
    /** Nombres alternativos aceptados en la cabecera. */
    private static final Map<String, String> ALIAS = Map.of(
            "codigo_producto", "codigo",
            "nombre_producto", "nombre",
            "unidad", "unidad_medida",
            "visible", "visible_web",
            "visible_en_web", "visible_web");

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaProductoRepository;
    private final MarcaRepository marcaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    /** Fila ya validada, lista para guardar. */
    private record FilaValida(int fila, Long idExistente, ProductoRequest request) {}

    @Transactional
    public CargaMasivaProductosResponse cargar(MultipartFile archivo, boolean simular) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Selecciona un archivo CSV con productos");
        }
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!nombre.endsWith(".csv")) {
            throw new BusinessException("El archivo debe ser .csv (en Excel: Guardar como → CSV UTF-8)");
        }

        List<List<String>> filas = LectorCsv.leer(leerTexto(archivo));
        if (filas.isEmpty()) {
            throw new BusinessException("El archivo está vacío");
        }
        if (filas.size() - 1 > MAXIMO_FILAS) {
            throw new BusinessException("El archivo tiene más de " + MAXIMO_FILAS + " productos; divídelo en varios archivos");
        }

        Map<String, Integer> columnas = leerCabecera(filas.get(0));
        Catalogos catalogos = cargarCatalogos();

        List<ErrorFila> errores = new ArrayList<>();
        List<FilaValida> validas = new ArrayList<>();
        Set<String> codigosEnArchivo = new HashSet<>();

        for (int i = 1; i < filas.size(); i++) {
            int numeroFila = i + 1;
            Map<String, String> celdas = celdas(filas.get(i), columnas);
            validarFila(numeroFila, celdas, catalogos, codigosEnArchivo, errores, validas);
        }

        int creados = (int) validas.stream().filter(v -> v.idExistente() == null).count();
        int actualizados = validas.size() - creados;

        boolean hayErrores = !errores.isEmpty();
        if (!simular && !hayErrores) {
            for (FilaValida v : validas) {
                if (v.idExistente() == null) productoService.registrar(v.request());
                else productoService.actualizar(v.idExistente(), v.request());
            }
        }

        return CargaMasivaProductosResponse.builder()
                .simulacion(simular || hayErrores)
                .totalFilas(filas.size() - 1)
                .creados(hayErrores ? 0 : creados)
                .actualizados(hayErrores ? 0 : actualizados)
                .errores(errores)
                .build();
    }

    // ------------------------------------------------------------------

    private void validarFila(int fila, Map<String, String> c, Catalogos cat, Set<String> codigosEnArchivo,
                             List<ErrorFila> errores, List<FilaValida> validas) {
        String codigo = c.get("codigo");
        List<String> problemas = new ArrayList<>();

        if (codigo.isEmpty()) problemas.add("Falta el código");
        else if (codigo.length() > 30) problemas.add("El código admite como máximo 30 caracteres");
        else if (!codigosEnArchivo.add(codigo.toUpperCase(Locale.ROOT))) problemas.add("El código está repetido en el archivo");

        Optional<Producto> existente = codigo.isEmpty() ? Optional.empty() : productoRepository.findByCodigoProducto(codigo);
        if (existente.isPresent() && "I".equals(existente.get().getStatus())) {
            problemas.add("El producto con este código está desactivado; reactívalo antes de actualizarlo");
        }
        Producto actual = existente.orElse(null);

        String nombre = c.get("nombre");
        if (nombre.isEmpty() && actual == null) problemas.add("Falta el nombre");
        else if (nombre.length() > 200) problemas.add("El nombre admite como máximo 200 caracteres");

        Integer idCategoria = null;
        String categoria = c.get("categoria");
        if (categoria.isEmpty()) {
            if (actual == null) problemas.add("Falta la categoría");
        } else {
            List<CategoriaProducto> encontradas = cat.categorias.getOrDefault(clave(categoria), List.of());
            if (encontradas.isEmpty()) problemas.add("No existe la categoría «" + categoria + "»");
            else if (encontradas.size() > 1) problemas.add("Hay varias categorías «" + categoria + "»; usa su slug web");
            else idCategoria = encontradas.get(0).getIdCategoriaProducto();
        }

        Integer idUnidad = null;
        String unidad = c.get("unidad_medida");
        if (unidad.isEmpty()) {
            if (actual == null) problemas.add("Falta la unidad de medida");
        } else {
            UnidadMedida u = cat.unidades.get(clave(unidad));
            if (u == null) problemas.add("No existe la unidad de medida «" + unidad + "»");
            else idUnidad = u.getIdUnidadMedida();
        }

        Integer idMarca = null;
        String marca = c.get("marca");
        if (!marca.isEmpty()) {
            Marca m = cat.marcas.get(clave(marca));
            if (m == null) problemas.add("No existe la marca «" + marca + "»");
            else idMarca = m.getIdMarca();
        }

        String procedencia = c.get("procedencia");
        if (procedencia.length() > 100) problemas.add("La procedencia admite como máximo 100 caracteres");

        Integer stock = null;
        if (!c.get("stock").isEmpty()) {
            try {
                stock = Integer.valueOf(c.get("stock"));
                if (stock < 0) problemas.add("El stock no puede ser negativo");
            } catch (NumberFormatException e) {
                problemas.add("El stock debe ser un número entero");
            }
        }

        Boolean visible = null;
        if (!c.get("visible_web").isEmpty()) {
            visible = siNo(c.get("visible_web"));
            if (visible == null) problemas.add("visible_web debe ser si/no");
        }

        if (!problemas.isEmpty()) {
            errores.add(new ErrorFila(fila, codigo, String.join("; ", problemas)));
            return;
        }

        // En un producto existente, las celdas vacías conservan el valor actual.
        ProductoRequest r = new ProductoRequest();
        r.setCodigoProducto(codigo);
        r.setNombreProducto(!nombre.isEmpty() ? nombre : actual.getNombreProducto());
        r.setIdCategoriaProducto(idCategoria != null ? idCategoria : actual.getCategoriaProducto().getIdCategoriaProducto());
        r.setIdUnidadMedida(idUnidad != null ? idUnidad : actual.getUnidadMedida().getIdUnidadMedida());
        r.setIdMarca(idMarca != null ? idMarca
                : actual != null && actual.getMarca() != null ? actual.getMarca().getIdMarca() : null);
        r.setProcedencia(!procedencia.isEmpty() ? procedencia : actual != null ? actual.getProcedencia() : null);
        r.setStock(stock != null ? stock : actual != null ? actual.getStock() : 0);
        r.setVisibleWeb(visible != null ? visible : actual == null || Boolean.TRUE.equals(actual.getVisibleWeb()));
        r.setDescripcion(!c.get("descripcion").isEmpty() ? c.get("descripcion") : actual != null ? actual.getDescripcion() : null);

        validas.add(new FilaValida(fila, actual != null ? actual.getIdProducto() : null, r));
    }

    private Map<String, Integer> leerCabecera(List<String> cabecera) {
        Map<String, Integer> columnas = new HashMap<>();
        List<String> desconocidas = new ArrayList<>();
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = clave(cabecera.get(i)).replace(' ', '_');
            nombre = ALIAS.getOrDefault(nombre, nombre);
            if (nombre.isEmpty()) continue;
            if (!CONOCIDAS.contains(nombre)) desconocidas.add(cabecera.get(i).trim());
            else columnas.putIfAbsent(nombre, i);
        }
        List<String> faltan = OBLIGATORIAS.stream().filter(o -> !columnas.containsKey(o)).toList();
        if (!faltan.isEmpty()) {
            throw new BusinessException("Faltan columnas en la cabecera: " + String.join(", ", faltan)
                    + ". Descarga la plantilla para ver el formato.");
        }
        if (!desconocidas.isEmpty()) {
            throw new BusinessException("Columnas no reconocidas: " + String.join(", ", desconocidas)
                    + ". Columnas válidas: " + String.join(", ", CONOCIDAS));
        }
        return columnas;
    }

    private static Map<String, String> celdas(List<String> fila, Map<String, Integer> columnas) {
        Map<String, String> valores = new HashMap<>();
        for (String nombre : CONOCIDAS) {
            Integer indice = columnas.get(nombre);
            String valor = indice != null && indice < fila.size() ? fila.get(indice) : "";
            valores.put(nombre, valor == null ? "" : valor.trim());
        }
        return valores;
    }

    private record Catalogos(Map<String, List<CategoriaProducto>> categorias,
                             Map<String, Marca> marcas,
                             Map<String, UnidadMedida> unidades) {}

    /** Categorías, marcas y unidades activas indexadas por nombre normalizado (una consulta por tabla). */
    private Catalogos cargarCatalogos() {
        Map<String, List<CategoriaProducto>> categorias = new HashMap<>();
        for (CategoriaProducto c : categoriaProductoRepository.findByStatus("A")) {
            categorias.computeIfAbsent(clave(c.getNombreCategoria()), k -> new ArrayList<>()).add(c);
            if (c.getSlugWeb() != null) {
                categorias.computeIfAbsent(clave(c.getSlugWeb()), k -> new ArrayList<>()).add(c);
            }
        }
        categorias.replaceAll((k, lista) -> lista.stream().distinct().toList());

        Map<String, Marca> marcas = new HashMap<>();
        for (Marca m : marcaRepository.findByStatus("A")) marcas.put(clave(m.getNombreMarca()), m);

        Map<String, UnidadMedida> unidades = new HashMap<>();
        for (UnidadMedida u : unidadMedidaRepository.findByStatus("A")) {
            unidades.put(clave(u.getCodigoUnidad()), u);
            unidades.putIfAbsent(clave(u.getNombreUnidad()), u);
        }
        return new Catalogos(categorias, marcas, unidades);
    }

    /** Minúsculas, sin tildes ni espacios sobrantes: "Válvulas  " → "valvulas". */
    private static String clave(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static Boolean siNo(String valor) {
        return switch (clave(valor)) {
            case "si", "s", "true", "1", "x", "verdadero" -> true;
            case "no", "n", "false", "0", "falso" -> false;
            default -> null;
        };
    }

    /** UTF-8 (lo recomendado); si el archivo no lo es, se lee como Windows-1252 (CSV de Excel antiguo). */
    private static String leerTexto(MultipartFile archivo) {
        try {
            byte[] bytes = archivo.getBytes();
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                return new String(bytes, Charset.forName("windows-1252"));
            }
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo");
        }
    }
}
