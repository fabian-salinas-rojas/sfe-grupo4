package pe.sfe.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Carga las consultas de src/main/resources/db/queries (las mismas que se validaron contra PostgreSQL). */
final class SqlResources {

    private SqlResources() {
    }

    static String cargar(String nombre) {
        try (InputStream in = SqlResources.class.getResourceAsStream("/db/queries/" + nombre)) {
            if (in == null) {
                throw new IllegalStateException("Consulta SQL no encontrada: " + nombre);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
