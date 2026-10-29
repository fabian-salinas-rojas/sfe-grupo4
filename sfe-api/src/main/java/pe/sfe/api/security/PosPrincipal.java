package pe.sfe.api.security;

/** Caja autenticada por su API Key. */
public record PosPrincipal(long posId, long emisorId, String nombre) {
}
