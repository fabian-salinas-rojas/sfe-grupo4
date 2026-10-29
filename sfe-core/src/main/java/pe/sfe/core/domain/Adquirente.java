package pe.sfe.core.domain;

/** tipoDocumento segun Catalogo 06 (6 = RUC, 1 = DNI, 0 = sin documento, 4 = CE, 7 = pasaporte). */
public record Adquirente(String tipoDocumento, String numeroDocumento, String denominacion, String direccion) {

    public static final String SIN_DOCUMENTO = "0";

    public static Adquirente consumidorFinal() {
        return new Adquirente(SIN_DOCUMENTO, "-", "CLIENTES VARIOS", null);
    }

    public boolean identificado() {
        return !SIN_DOCUMENTO.equals(tipoDocumento);
    }
}
