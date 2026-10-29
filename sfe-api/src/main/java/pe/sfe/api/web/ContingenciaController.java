package pe.sfe.api.web;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.sfe.api.security.PosPrincipal;
import pe.sfe.core.app.Ports;

/** RF-10: reporte de comprobantes en cola de contingencia (intentos, ultimo intento, estado de envio, error). */
@RestController
@RequestMapping("/api/v1/contingencia")
public class ContingenciaController {

    private final Ports.Contingencia contingencia;

    public ContingenciaController(Ports.Contingencia contingencia) {
        this.contingencia = contingencia;
    }

    @GetMapping
    public List<Ports.ItemContingencia> pendientes(@AuthenticationPrincipal PosPrincipal pos) {
        return contingencia.pendientes(pos.posId());
    }
}