package com.genlogs.app.security;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Roles del sistema (RF-31): uno por cada trabajador del negocio del documento
 * (sección 2.1.2): Vendedor / Asesor Comercial, Jefe Administrativo y Contador General.
 * Los nombres coinciden con rol.nombre_rol normalizado (mayúsculas, sin tildes y
 * con "_" en lugar de espacios).
 */
public final class Roles {

    private Roles() {}

    /** Vendedor / Asesor Comercial: clientes, cotizaciones y servicios. */
    public static final String ASESOR_COMERCIAL = "ASESOR_COMERCIAL";
    /** Jefe Administrativo: órdenes de compra, catálogo, usuarios, catálogos maestros y reportes. */
    public static final String JEFE_ADMINISTRATIVO = "JEFE_ADMINISTRATIVO";
    /** Contador General: facturación, pagos y reportes. */
    public static final String CONTADOR_GENERAL = "CONTADOR_GENERAL";

    /** Los 3 roles: consultas generales y catálogos para los formularios. */
    public static final String[] TODOS = { ASESOR_COMERCIAL, JEFE_ADMINISTRATIVO, CONTADOR_GENERAL };

    /** CU-04 clientes, CU-05/CU-08 contactos, CU-19 adjuntos y subida de archivos. */
    public static final String[] ASESOR_Y_JEFE = { ASESOR_COMERCIAL, JEFE_ADMINISTRATIVO };

    /** CU-25 consulta de facturación, CU-26 reportes y CU-27 indicadores. */
    public static final String[] JEFE_Y_CONTADOR = { JEFE_ADMINISTRATIVO, CONTADOR_GENERAL };

    /**
     * Autoridad de Spring a partir del nombre guardado en la BD:
     * "Jefe Administrativo" → "ROLE_JEFE_ADMINISTRATIVO", "contador general" → "ROLE_CONTADOR_GENERAL".
     */
    public static String authority(String nombreRol) {
        return "ROLE_" + normalizar(nombreRol);
    }

    /** Mayúsculas, sin tildes y con "_" en lugar de espacios u otros separadores. */
    public static String normalizar(String nombreRol) {
        if (nombreRol == null) return "";
        String sinTildes = Normalizer.normalize(nombreRol.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
