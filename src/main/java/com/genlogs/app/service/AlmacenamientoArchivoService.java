package com.genlogs.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.genlogs.app.exception.BusinessException;

/**
 * Guarda y elimina archivos (imagenes y documentos) en el disco del servidor.
 * Reemplaza al antiguo servicio externo: ya no se depende de ningun tercero.
 * Los archivos se sirven publicamente bajo /uploads/** (ver WebStorageConfig).
 */
@Service
public class AlmacenamientoArchivoService {

    public static final String TIPO_IMAGEN = "image";
    public static final String TIPO_DOCUMENTO = "raw";

    private static final String PREFIJO_URL = "/uploads/";

    private final Path raiz;
    private final String urlPublicaBase;

    public AlmacenamientoArchivoService(
            @Value("${app.storage.dir:uploads}") String directorio,
            @Value("${app.storage.public-base-url:}") String urlPublicaBase) {
        this.raiz = Paths.get(directorio).toAbsolutePath().normalize();
        this.urlPublicaBase = urlPublicaBase == null ? "" : urlPublicaBase.trim().replaceAll("/+$", "");
    }

    public Path getRaiz() {
        return raiz;
    }

    public String subirImagen(MultipartFile archivo, String carpeta) {
        return guardar(archivo, carpeta);
    }

    public String subirDocumento(MultipartFile archivo, String carpeta) {
        return guardar(archivo, carpeta);
    }

    /** Elimina el archivo al que apunta la URL guardada en la BD (si existe). */
    public void eliminarPorUrl(String url, String tipo) {
        if (url == null) return;
        int idx = url.indexOf(PREFIJO_URL);
        if (idx < 0) return; // URL antigua u externa: no hay nada local que borrar
        String relativa = url.substring(idx + PREFIJO_URL.length());
        Path destino = raiz.resolve(relativa).normalize();
        if (!destino.startsWith(raiz)) return; // evita salir de la carpeta de subidas
        try {
            Files.deleteIfExists(destino);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo: " + relativa, e);
        }
    }

    private String guardar(MultipartFile archivo, String carpeta) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo está vacío");
        }
        String carpetaLimpia = carpeta == null ? "" : carpeta.replace("\\", "/").replaceAll("[^a-zA-Z0-9/_-]", "");
        String nombre = UUID.randomUUID().toString().replace("-", "") + extension(archivo.getOriginalFilename());
        try {
            Path dir = raiz.resolve(carpetaLimpia).normalize();
            if (!dir.startsWith(raiz)) {
                throw new BusinessException("Carpeta de destino no válida");
            }
            Files.createDirectories(dir);
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, dir.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo", e);
        }
        String relativa = (carpetaLimpia.isEmpty() ? "" : carpetaLimpia + "/") + nombre;
        return baseUrl() + PREFIJO_URL + relativa;
    }

    private String baseUrl() {
        if (!urlPublicaBase.isEmpty()) return urlPublicaBase;
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private String extension(String nombreOriginal) {
        if (nombreOriginal == null) return "";
        int punto = nombreOriginal.lastIndexOf('.');
        if (punto < 0 || punto == nombreOriginal.length() - 1) return "";
        String ext = nombreOriginal.substring(punto).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }
}