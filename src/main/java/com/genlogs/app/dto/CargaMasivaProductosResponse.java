package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** Resultado de la carga masiva de productos por CSV (RF-10). */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CargaMasivaProductosResponse {

    /** true si solo se validó el archivo (no se guardó nada). */
    private boolean simulacion;
    /** Filas de datos leídas (sin contar la cabecera). */
    private int totalFilas;
    /** Productos nuevos (o que se crearían, en simulación). */
    private int creados;
    /** Productos existentes actualizados por su código (o que se actualizarían). */
    private int actualizados;
    /** Si hay errores no se guarda ninguna fila: se corrige el archivo y se vuelve a subir. */
    private List<ErrorFila> errores;

    @Getter @AllArgsConstructor @NoArgsConstructor
    public static class ErrorFila {
        /** Número de fila en el archivo (la cabecera es la fila 1). */
        private int fila;
        private String codigo;
        private String mensaje;
    }
}
