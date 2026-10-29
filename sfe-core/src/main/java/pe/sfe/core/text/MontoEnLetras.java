package pe.sfe.core.text;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Leyenda 1000 de SUNAT: importe total en letras, p. ej. "CIENTO DIECIOCHO CON 00/100 SOLES". */
public final class MontoEnLetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {
    }

    public static String convertir(BigDecimal importe, String moneda) {
        BigDecimal v = importe.setScale(2, RoundingMode.HALF_UP);
        long enteros = v.toBigInteger().longValueExact();
        int centimos = v.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String nombre = "USD".equals(moneda) ? "DOLARES AMERICANOS" : "SOLES";
        return enLetras(enteros) + String.format(" CON %02d/100 %s", centimos, nombre);
    }

    static String enLetras(long n) {
        if (n == 0) {
            return "CERO";
        }
        long millones = n / 1_000_000;
        long resto = n % 1_000_000;
        long miles = resto / 1000;
        long unidades = resto % 1000;
        StringBuilder sb = new StringBuilder();
        if (millones > 0) {
            sb.append(millones == 1 ? "UN MILLON" : apocope(menorMil((int) millones)) + " MILLONES");
        }
        if (miles > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(miles == 1 ? "MIL" : apocope(menorMil((int) miles)) + " MIL");
        }
        if (unidades > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(menorMil((int) unidades));
        }
        return sb.toString();
    }

    private static String menorMil(int n) {
        if (n == 100) {
            return "CIEN";
        }
        StringBuilder sb = new StringBuilder();
        int c = n / 100;
        int r = n % 100;
        if (c > 0) {
            sb.append(CENTENAS[c]);
        }
        if (r > 0) {
            if (sb.length() > 0) sb.append(' ');
            if (r < 30) {
                sb.append(UNIDADES[r]);
            } else {
                sb.append(DECENAS[r / 10]);
                if (r % 10 > 0) sb.append(" Y ").append(UNIDADES[r % 10]);
            }
        }
        return sb.toString();
    }

    /** "UNO" -> "UN" delante de MIL / MILLONES (VEINTIUNO -> VEINTIUN). */
    private static String apocope(String s) {
        return s.endsWith("UNO") ? s.substring(0, s.length() - 1) : s;
    }
}
