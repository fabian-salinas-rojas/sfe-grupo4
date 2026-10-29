package pe.sfe.core.pack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lee el ZIP de respuesta de SUNAT con topes (anti zip-bomb): nunca escribe a disco. */
public final class ZipReader {

    private static final int MAX_ENTRADAS = 50;
    private static final int MAX_BYTES_ENTRADA = 5 * 1024 * 1024;

    private ZipReader() {
    }

    public record Entrada(String nombre, byte[] contenido) {
    }

    /** Devuelve el primer .xml (preferentemente R-*.xml, la constancia). */
    public static Entrada primerXml(byte[] zip) {
        Entrada primero = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            int n = 0;
            while ((e = zis.getNextEntry()) != null) {
                if (++n > MAX_ENTRADAS) {
                    throw new IllegalArgumentException("ZIP con demasiadas entradas");
                }
                if (e.isDirectory() || !e.getName().toLowerCase().endsWith(".xml")) {
                    continue;
                }
                String simple = e.getName().substring(e.getName().lastIndexOf('/') + 1);
                Entrada actual = new Entrada(simple, leerAcotado(zis));
                if (simple.startsWith("R-")) {
                    return actual;
                }
                if (primero == null) {
                    primero = actual;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("ZIP de respuesta ilegible", e);
        }
        if (primero == null) {
            throw new IllegalArgumentException("El ZIP de respuesta no contiene un XML");
        }
        return primero;
    }

    private static byte[] leerAcotado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > MAX_BYTES_ENTRADA) {
                throw new IllegalArgumentException("Entrada del ZIP excede el tope permitido");
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }
}
