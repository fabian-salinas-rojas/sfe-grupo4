package pe.sfe.core.sign;

/** RN-08: todo comprobante se firma con un certificado digital vigente. */
public class CertificadoNoVigenteException extends RuntimeException {
    public CertificadoNoVigenteException(String message, Throwable cause) {
        super(message, cause);
    }
}
