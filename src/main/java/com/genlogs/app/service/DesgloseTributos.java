package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Desglose de tributos de una cotización o facturación (RF-21, RF-37).
 *
 * <p>Se redondea primero el total de tributos sobre la tasa total (IGV + IPM) y luego el IPM;
 * el IGV es la diferencia. Así IGV + IPM = tributos exactamente y el total coincide con el
 * cálculo anterior (subtotal × 1,18) cuando la tasa total es 18 %.</p>
 */
public record DesgloseTributos(
        BigDecimal subtotal,
        BigDecimal porcentajeIgv,
        BigDecimal porcentajeIpm,
        BigDecimal montoIgv,
        BigDecimal montoIpm,
        BigDecimal totalTributos,
        BigDecimal total) {

    private static final BigDecimal CIEN = new BigDecimal("100");

    public static DesgloseTributos calcular(BigDecimal subtotal, BigDecimal porcentajeIgv, BigDecimal porcentajeIpm) {
        BigDecimal base = (subtotal != null ? subtotal : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = porcentajeIgv != null ? porcentajeIgv : BigDecimal.ZERO;
        BigDecimal ipm = porcentajeIpm != null ? porcentajeIpm : BigDecimal.ZERO;

        BigDecimal tributos = base.multiply(igv.add(ipm)).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal montoIpm = base.multiply(ipm).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal montoIgv = tributos.subtract(montoIpm);

        return new DesgloseTributos(base, igv, ipm, montoIgv, montoIpm, tributos, base.add(tributos));
    }

    /** Tasa total aplicable (IGV + IPM), en porcentaje. */
    public BigDecimal tasaTotal() {
        return porcentajeIgv.add(porcentajeIpm);
    }
}
