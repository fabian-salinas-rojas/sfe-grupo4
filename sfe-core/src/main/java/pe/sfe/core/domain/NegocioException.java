package pe.sfe.core.domain;

import java.util.List;

/** Violacion de reglas de negocio (RN-xx). Se traduce a HTTP 422 en la API. */
public class NegocioException extends RuntimeException {

    private final transient List<String> violaciones;

    public NegocioException(List<String> violaciones) {
        super(String.join("; ", violaciones));
        this.violaciones = List.copyOf(violaciones);
    }

    public List<String> violaciones() {
        return violaciones;
    }
}
