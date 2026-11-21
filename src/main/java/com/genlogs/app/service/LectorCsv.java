package com.genlogs.app.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lector de CSV sin dependencias externas (RFC 4180): campos entre comillas dobles,
 * comillas escapadas ("") y saltos de línea dentro de comillas. Detecta el separador
 * por la cabecera: coma o punto y coma (Excel en español guarda los CSV con ";").
 */
final class LectorCsv {

    private LectorCsv() {}

    /** Filas del archivo; cada fila es la lista de celdas. Omite filas totalmente vacías. */
    static List<List<String>> leer(String contenido) {
        String texto = contenido.startsWith("﻿") ? contenido.substring(1) : contenido; // BOM de Excel
        char separador = detectarSeparador(texto);

        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    celda.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == separador) {
                fila.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                fila.add(celda.toString());
                celda.setLength(0);
                agregarSiNoVacia(filas, fila);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        fila.add(celda.toString());
        agregarSiNoVacia(filas, fila);
        return filas;
    }

    private static void agregarSiNoVacia(List<List<String>> filas, List<String> fila) {
        boolean vacia = fila.stream().allMatch(s -> s.isBlank());
        if (!vacia) filas.add(fila);
    }

    /** Usa el separador que más aparece en la primera línea (fuera de comillas). */
    private static char detectarSeparador(String texto) {
        int comas = 0;
        int puntoYComa = 0;
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') entreComillas = !entreComillas;
            else if (!entreComillas && (c == '\n' || c == '\r')) break;
            else if (!entreComillas && c == ',') comas++;
            else if (!entreComillas && c == ';') puntoYComa++;
        }
        return puntoYComa > comas ? ';' : ',';
    }
}
