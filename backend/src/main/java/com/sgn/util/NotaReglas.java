package com.sgn.util;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class NotaReglas {
    private static final BigDecimal NOTA_MINIMA_APROBACION = BigDecimal.valueOf(6);

    private NotaReglas() {}


    public static void validarVal(BigDecimal val) {
        if (val == null || val.compareTo(BigDecimal.ONE) < 0 || val.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("La nota debe estar entre 1 y 10");
        }
        if (val.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("La nota puede tener como máximo dos decimales");
        }
    }

    public static BigDecimal redondearVal(BigDecimal val) {
        if (val == null) throw new IllegalArgumentException("La nota es obligatoria");
        if (val.compareTo(BigDecimal.ONE) < 0 || val.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("La nota debe estar entre 1 y 10");
        }
        BigDecimal redondeada = val.setScale(0, RoundingMode.HALF_UP);
        validarVal(redondeada);
        return redondeada;
    }


    public static boolean enProceso(BigDecimal val) {
        return desaprobada(val);
    }

    public static boolean aprobada(BigDecimal val) {
        return val != null && val.compareTo(NOTA_MINIMA_APROBACION) >= 0;
    }

    public static boolean desaprobada(BigDecimal val) {
        return val != null && val.compareTo(NOTA_MINIMA_APROBACION) < 0;
    }


    public static String normalizarIdent(String ident) {
        if (ident == null) return "";
        String t = ident.trim();
        String digits = t.replaceAll("[^0-9]", "");
        return digits.length() >= 10 ? digits : t.toLowerCase();
    }


    public static void validarCuil(String cuil) {
        if (cuil == null || cuil.isBlank()) {
            throw new IllegalArgumentException("CUIL/CUIT obligatorio");
        }
        String d = cuil.replaceAll("[^0-9]", "");
        if (d.length() != 11) {
            throw new IllegalArgumentException("CUIL/CUIT inválido: debe tener 11 dígitos");
        }
    }
}
