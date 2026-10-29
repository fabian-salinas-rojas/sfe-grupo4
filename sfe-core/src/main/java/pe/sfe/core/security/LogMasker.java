package pe.sfe.core.security;

import java.util.regex.Pattern;

/**
 * Enmascara secretos que, por error, lleguen a un mensaje de log (regla no negociable #3: jamas registrar Clave SOL,
 * contrasenas de certificado ni llaves). Es la ULTIMA barrera: el codigo ya evita registrar secretos (clase Secret).
 */
public final class LogMasker {

    /** clave=valor, clave: valor, "clave":"valor" para nombres tipicos de secretos. */
    private static final Pattern CLAVE_VALOR = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|pass|clave\\w*|secret\\w*|api[-_]?key|x-api-key|token|private[-_]?key)([\"']?\\s*[:=]\\s*[\"']?)[^\\s,;\"'<&}\\]]+");
    /** Elementos XML de WS-Security / credenciales. */
    private static final Pattern XML = Pattern.compile("(?i)(<(?:[\\w-]+:)?(?:password|clave\\w*|secret\\w*|privatekey)[^>]*>)[^<]*");
    /** Cabeceras de autorizacion. */
    private static final Pattern AUTH = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(?:bearer|basic)?\\s*[^\\s,;]+");
    /** Bloques PEM de llaves privadas. */
    private static final Pattern PEM = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");

    private LogMasker() {
    }

    public static String mask(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) {
            return mensaje;
        }
        String s = PEM.matcher(mensaje).replaceAll("***PRIVATE KEY***");
        s = XML.matcher(s).replaceAll("$1***");
        s = AUTH.matcher(s).replaceAll("$1***");
        s = CLAVE_VALOR.matcher(s).replaceAll("$1$2***");
        return s;
    }
}
