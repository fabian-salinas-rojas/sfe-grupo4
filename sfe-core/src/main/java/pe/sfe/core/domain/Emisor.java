package pe.sfe.core.domain;

public record Emisor(
        String ruc,
        String razonSocial,
        String nombreComercial,
        String ubigeo,
        String direccion,
        String departamento,
        String provincia,
        String distrito) {
}
