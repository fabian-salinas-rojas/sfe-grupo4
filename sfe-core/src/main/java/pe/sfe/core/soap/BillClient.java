package pe.sfe.core.soap;

/**
 * Puerto hacia el servicio billService de SUNAT. Implementacion real: Spring-WS + WSS4J en sfe-worker.
 * Contrato: lanza {@link SunatFaultException} ante SOAP Fault y {@link SunatTransportException} ante red/timeout.
 */
public interface BillClient {

    /** Envio sincrono de UN comprobante. Devuelve el ZIP de la CDR. */
    byte[] sendBill(String zipFileName, byte[] zipContent);

    /** Envio asincrono (resumen diario / baja). Devuelve el ticket. */
    String sendSummary(String zipFileName, byte[] zipContent);

    /** Consulta el resultado de un ticket (el RUC identifica las credenciales con que se envio). */
    StatusResponse getStatus(String rucEmisor, String ticket);

    StatusResponse getStatusCdr(String rucEmisor, String tipo, String serie, long numero);
}
