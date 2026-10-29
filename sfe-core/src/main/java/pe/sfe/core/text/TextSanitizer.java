package pe.sfe.core.text;

import java.text.Normalizer;
import java.util.Map;

/**
 * Sanea texto libre (descripciones, razon social, direcciones) para que sea representable en
 * ISO-8859-1 y valido en XML 1.0. Cubre el riesgo "no asumir ISO-8859-1 para todo texto":
 * comillas tipograficas, guiones largos, euro, caracteres de control, etc.
 *
 * Latin-1 equivale exactamente a los code points 0x00-0xFF, por eso la prueba es {@code cp <= 0xFF}.
 */
public final class TextSanitizer {

    private static final Map<Integer, String> REEMPLAZOS = Map.ofEntries(
            Map.entry(0x2018, "'"), Map.entry(0x2019, "'"), Map.entry(0x201A, "'"), Map.entry(0x201B, "'"),
            Map.entry(0x2032, "'"),
            Map.entry(0x201C, "\""), Map.entry(0x201D, "\""), Map.entry(0x201E, "\""), Map.entry(0x201F, "\""),
            Map.entry(0x2033, "\""),
            Map.entry(0x2010, "-"), Map.entry(0x2011, "-"), Map.entry(0x2012, "-"), Map.entry(0x2013, "-"),
            Map.entry(0x2014, "-"), Map.entry(0x2015, "-"), Map.entry(0x2212, "-"),
            Map.entry(0x2026, "..."), Map.entry(0x2022, "-"), Map.entry(0x00B7, "."),
            Map.entry(0x20AC, "EUR"), Map.entry(0x2122, "(TM)"),
            Map.entry(0x00A0, " "), Map.entry(0x202F, " "), Map.entry(0x205F, " "), Map.entry(0x3000, " "),
            Map.entry(0x200B, ""), Map.entry(0x200C, ""), Map.entry(0x200D, ""), Map.entry(0x2060, ""),
            Map.entry(0xFEFF, ""));

    private TextSanitizer() {
    }

    /** Devuelve texto seguro para ISO-8859-1. Nunca devuelve null. */
    public static String latin1(String entrada) {
        if (entrada == null) {
            return "";
        }
        String s = Normalizer.normalize(entrada, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> apendar(sb, cp));
        return sb.toString().replaceAll(" {2,}", " ").strip();
    }

    private static void apendar(StringBuilder sb, int cp) {
        if (cp == '\t' || cp == '\n' || cp == '\r' || (cp >= 0x2000 && cp <= 0x200A)) {
            sb.append(' ');
            return;
        }
        if (cp < 0x20 || (cp >= 0x7F && cp <= 0x9F)) {
            return; // control: invalido o desaconsejado en XML 1.0
        }
        String rem = REEMPLAZOS.get(cp);
        if (rem != null) {
            sb.append(rem);
            return;
        }
        if (cp <= 0xFF) {
            sb.appendCodePoint(cp);
            return;
        }
        // Fuera de Latin-1: intentar descomponer (p. ej. "ő" -> "o") y quitar marcas combinantes.
        String base = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        if (!base.isEmpty() && base.codePoints().allMatch(c -> c >= 0x20 && c <= 0xFF)) {
            sb.append(base);
        } else {
            sb.append('?');
        }
    }
}
