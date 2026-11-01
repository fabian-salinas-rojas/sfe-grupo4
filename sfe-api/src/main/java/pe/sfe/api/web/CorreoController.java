package pe.sfe.api.web;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.sfe.api.correo.EnvioCorreoService;
import pe.sfe.api.security.PosPrincipal;

/** RF-07: envio por correo del comprobante (PDF + XML). 200 enviado, 404 ajeno/inexistente, 409 no aceptado, 502 SMTP caido. */
@RestController
@RequestMapping("/api/v1/comprobantes")
public class CorreoController {

    private static final Logger log = LoggerFactory.getLogger(CorreoController.class);
    private final EnvioCorreoService servicio;

    public CorreoController(EnvioCorreoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/{id}/correo")
    public ResponseEntity<?> enviar(@AuthenticationPrincipal PosPrincipal pos, @PathVariable UUID id,
                                    @Valid @RequestBody EnviarCorreoRequest req) {
        EnvioCorreoService.Resultado r;
        try {
            r = servicio.enviar(pos.posId(), id, req.destinatario());
        } catch (MailException e) {
            // Sin detalle al cliente (podria incluir host/usuario SMTP); el motivo queda en el log del servidor.
            log.error("RF-07 no se pudo enviar el correo: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "No se pudo enviar el correo. Intente mas tarde."));
        }
        if (r == EnvioCorreoService.Resultado.NO_ENCONTRADO) {
            return ResponseEntity.notFound().build();
        }
        if (r == EnvioCorreoService.Resultado.NO_ACEPTADO) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Solo se envian por correo los comprobantes aceptados por SUNAT."));
        }
        return ResponseEntity.ok(Map.of("estado", "ENVIADO"));
    }
}
