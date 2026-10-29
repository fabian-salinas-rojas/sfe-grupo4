package pe.sfe.core.security;

import java.util.Objects;

/**
 * Envoltorio para secretos (Clave SOL, contrasenas de certificado). Su toString nunca revela el valor,
 * de modo que un log accidental ("log.info(\"{}\", credenciales)") no filtra nada.
 * El valor solo se obtiene con {@link #reveal()}, que debe usarse unicamente en el punto de uso.
 */
public final class Secret {

    private final String value;

    private Secret(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static Secret of(String value) {
        return new Secret(value);
    }

    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "***";
    }

    @Override
    public boolean equals(Object o) {
        return this == o; // identidad: evita comparar secretos por valor de forma accidental
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
