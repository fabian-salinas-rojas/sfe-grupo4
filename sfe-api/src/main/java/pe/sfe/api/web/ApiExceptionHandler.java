package pe.sfe.api.web;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pe.sfe.core.domain.NegocioException;
import pe.sfe.core.sign.CertificadoNoVigenteException;
import pe.sfe.core.xml.XsdValidator;

/**
 * Errores como application/problem+json (RFC 9457). Nunca devuelve trazas ni el cuerpo recibido; los errores
 * inesperados se correlacionan con un errorId que SI se escribe en el log.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    ProblemDetail negocio(NegocioException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida");
        p.setProperty("violaciones", e.violaciones());
        return p;
    }

    @ExceptionHandler(CertificadoNoVigenteException.class)
    ProblemDetail certificado(CertificadoNoVigenteException e) {
        log.error("Certificado digital no vigente: no se pueden emitir comprobantes");
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Certificado digital no vigente: contacte a soporte. No se consumio ningun correlativo.");
    }

    @ExceptionHandler(XsdValidator.XsdValidationException.class)
    ProblemDetail xsd(XsdValidator.XsdValidationException e) {
        String id = UUID.randomUUID().toString();
        log.error("errorId={} el XML generado no cumple el XSD UBL: {}", id, e.errores());
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar un XML valido");
        p.setProperty("errorId", id);
        return p;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        String id = UUID.randomUUID().toString();
        log.error("errorId={} error inesperado", id, e);
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");
        p.setProperty("errorId", id);
        return p;
    }
}
