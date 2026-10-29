package pe.sfe.core.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Empaqueta el XML firmado en el ZIP que exige SUNAT (manual 1.3): una carpeta vacia "dummy" (opcional,
 * configurable porque el Stack Ideal pide probar ambas variantes en BETA) y un unico XML con el mismo nombre base.
 * El ZipOutputStream se CIERRA (finish) antes de leer el buffer: leerlo antes produce un ZIP truncado.
 */
public final class ZipPackager {

    private ZipPackager() {
    }

    public static byte[] empaquetar(NombreArchivo nombre, byte[] xmlFirmado, boolean carpetaDummy) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(xmlFirmado.length / 2 + 1024);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (carpetaDummy) {
                zos.putNextEntry(new ZipEntry("dummy/"));
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry(nombre.xml()));
            zos.write(xmlFirmado);
            zos.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el ZIP", e);
        }
        return baos.toByteArray();
    }
}
