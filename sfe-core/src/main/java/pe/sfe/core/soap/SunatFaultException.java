package pe.sfe.core.soap;

/** SOAP Fault de SUNAT, p. ej. faultcode "soap-env:Server.0135" / "soap-env:Client.1033". */
public class SunatFaultException extends RuntimeException {

    private final String faultCode;

    public SunatFaultException(String faultCode, String faultString) {
        super(faultCode + ": " + faultString);
        this.faultCode = faultCode;
    }

    public String faultCode() {
        return faultCode;
    }
}
