package pe.sfe.core.policy;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.sfe.core.cdr.Cdr;
import pe.sfe.core.cdr.CdrParser;
import pe.sfe.core.pack.ZipReader;
import pe.sfe.core.soap.SunatFaultException;
import pe.sfe.core.soap.SunatTransportException;

/**
 * Politica de reintentos (regla de negocio no negociable #1), basada en el manual SUNAT 4.1:
 *   0100-0999  excepciones propias de SUNAT           -> reintentar
 *   1000-1999  excepciones del contribuyente (SOAP Fault) -> NO reintentar (corregir y reenviar)
 *   2000-3999  errores que generan rechazo            -> NO reintentar jamas (numeracion consumida)
 *   4000+      observaciones: documento aceptado con advertencias (CDR con codigo 0 y cbc:Note)
 * RN-05: en una CDR, codigo 0 = ACEPTADO; cualquier otro (salvo 0100-0999, SUNAT) = RECHAZADO.
 * El codigo numerico manda sobre el prefijo del faultcode; sin codigo, Server/red/timeout -> reintentar y Client -> no.
 */
public final class EnvioClassifier {

    private static final Pattern CODIGO_FAULT = Pattern.compile("[.:](\\d{3,4})\\s*$");

    private final CdrParser parser;

    public EnvioClassifier(CdrParser parser) {
        this.parser = parser;
    }

    /** Resultado de un sendBill que respondio con el ZIP de la CDR. */
    public ResultadoEnvio deCdrZip(byte[] cdrZip) {
        Cdr cdr;
        try {
            cdr = parser.parse(ZipReader.primerXml(cdrZip).contenido());
        } catch (RuntimeException e) {
            return new ResultadoEnvio.Reintentar("CDR ilegible: " + e.getMessage());
        }
        return deCdr(cdr, cdrZip);
    }

    public ResultadoEnvio deCdr(Cdr cdr, byte[] cdrZip) {
        if (cdr.aceptado()) {
            return cdr.observaciones().isEmpty()
                    ? new ResultadoEnvio.Aceptado(cdr, cdrZip)
                    : new ResultadoEnvio.AceptadoConObservaciones(cdr, cdrZip);
        }
        Integer n = numero(cdr.codigoRespuesta());
        if (n != null && n >= 2000 && n <= 3999) {
            return new ResultadoEnvio.Rechazado(cdr.codigoRespuesta(), cdr.descripcion(), cdr, cdrZip);
        }
        if (n != null && n >= 100 && n <= 999) {
            return new ResultadoEnvio.Reintentar("Excepcion de SUNAT " + cdr.codigoRespuesta() + ": " + cdr.descripcion());
        }
        // RN-05 (APF1): una CDR con codigo distinto de 0 deja el comprobante RECHAZADO. Una CDR es un resultado ya
        // procesado, asi que cualquier otro codigo (incluso uno atipico o no numerico) se trata como rechazo: no se
        // reintenta a ciegas y queda a la vista para corregirlo. Las "excepciones" 1000-1999 del manual (4.1) llegan
        // como SOAP Fault, no como CDR: ver deFault().
        return new ResultadoEnvio.Rechazado(cdr.codigoRespuesta(), cdr.descripcion(), cdr, cdrZip);
    }

    public ResultadoEnvio deFalla(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SunatFaultException f) {
                return deFault(f);
            }
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SunatTransportException || c instanceof IOException) {
                return new ResultadoEnvio.Reintentar("Red/timeout: " + resumen(c));
            }
        }
        return new ResultadoEnvio.Reintentar("Falla inesperada: " + resumen(t));
    }

    /**
     * Stack Ideal 6: solo "red, timeout o fault tipo Server" se reintenta. Un Fault Client (o un codigo 1000-1999)
     * significa documento NO informado: se corrige y se reenvia, reintentar tal cual fallaria igual.
     */
    private ResultadoEnvio deFault(SunatFaultException f) {
        String fc = f.faultCode() == null ? "" : f.faultCode();
        Matcher m = CODIGO_FAULT.matcher(fc);
        Integer n = m.find() ? Integer.valueOf(m.group(1)) : null;
        if (n != null && n >= 2000 && n <= 3999) {
            return new ResultadoEnvio.Rechazado(String.valueOf(n), f.getMessage(), null, null); // nunca se reintenta
        }
        boolean deContribuyente = fc.contains("Client") || (n != null && n >= 1000 && n <= 1999);
        if (deContribuyente) {
            return new ResultadoEnvio.Excepcion(n != null ? String.valueOf(n) : fc, f.getMessage());
        }
        return new ResultadoEnvio.Reintentar("Fault " + fc); // Server (0100-0999 u otro): SUNAT no pudo procesar
    }

    private static Integer numero(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resumen(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }
}
