package pe.sfe.api.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.sfe.api.security.PosPrincipal;
import pe.sfe.core.app.EmitirComprobanteService;
import pe.sfe.core.app.Ports;

@RestController
@RequestMapping("/api/v1/comprobantes")
public class ComprobanteController {

    private final EmitirComprobanteService servicio;
    private final Ports.Comprobantes comprobantes;

    public ComprobanteController(EmitirComprobanteService servicio, Ports.Comprobantes comprobantes) {
        this.servicio = servicio;
        this.comprobantes = comprobantes;
    }

    /**
     * 202 Accepted inmediato: el comprobante ya esta numerado, firmado y persistido junto con su tarea de envio;
     * SUNAT se contacta despues (sfe-worker). Reenviar el mismo idVentaExterno devuelve el mismo comprobante
     * (cabecera Idempotent-Replayed: true) sin consumir otro correlativo.
     */
    @PostMapping
    public ResponseEntity<ComprobanteResponse> emitir(@AuthenticationPrincipal PosPrincipal pos,
                                                      @Valid @RequestBody EmitirRequest req) {
        var r = servicio.emitir(pos.posId(), req.aSolicitud());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/comprobantes/" + r.comprobante().id()))
                .header("Idempotent-Replayed", String.valueOf(r.yaExistia()))
                .body(ComprobanteResponse.de(r.comprobante()));
    }

    /** Estado del comprobante y de su CDR. Una caja solo ve los suyos (404 para los ajenos). */
    @GetMapping("/{id}")
    public ResponseEntity<ComprobanteResponse> consultar(@AuthenticationPrincipal PosPrincipal pos, @PathVariable UUID id) {
        return comprobantes.buscarPorId(pos.posId(), id)
                .map(c -> ResponseEntity.ok(ComprobanteResponse.de(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
