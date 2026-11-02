package pe.sfe.api.web;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.sfe.api.security.PosPrincipal;
import pe.sfe.core.app.ConsultaXml;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.EstadoComprobante;
import pe.sfe.core.pdf.RepresentacionImpresa;

/**
 * RF-06: representacion impresa (PDF con QR) del comprobante, generada desde su XML firmado.
 * Un comprobante RECHAZADO o en EXCEPCION no es valido ante SUNAT: no se imprime (409). Uno aun sin CDR (PENDIENTE /
 * EN_CONTINGENCIA) se imprime con un aviso visible de que SUNAT todavia no lo confirmo.
 */
@RestController
@RequestMapping("/api/v1/comprobantes")
public class PdfController {

    private final ConsultaXml consultaXml;
    private final Ports.Emisores emisores;
    private final Ports.Comprobantes comprobantes;

    public PdfController(ConsultaXml consultaXml, Ports.Emisores emisores, Ports.Comprobantes comprobantes) {
        this.consultaXml = consultaXml;
        this.emisores = emisores;
        this.comprobantes = comprobantes;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> pdf(@AuthenticationPrincipal PosPrincipal pos, @PathVariable UUID id) {
        var resumen = comprobantes.buscarPorId(pos.posId(), id);
        if (resumen.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        EstadoComprobante estado = resumen.get().estado();
        if (estado == EstadoComprobante.RECHAZADO || estado == EstadoComprobante.EXCEPCION) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "El comprobante esta " + estado.name() + " ante SUNAT: no es valido y no se imprime."));
        }
        var xml = consultaXml.porId(pos.posId(), id);
        if (xml.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var c = xml.get();
        Emisor emisor = emisores.delPos(pos.posId());
        String aviso = estado == EstadoComprobante.PENDIENTE || estado == EstadoComprobante.EN_CONTINGENCIA
                ? "ESTADO ANTE SUNAT: PENDIENTE DE CONFIRMACI\u00d3N (sin constancia de recepci\u00f3n todav\u00eda)."
                : null;
        byte[] pdf = RepresentacionImpresa.generar(emisor, c.tipo(), c.serie(), c.correlativo(), c.xmlFirmado(), aviso);
        String nombre = emisor.ruc().trim() + "-" + c.tipo().codigo() + "-" + c.serie() + "-" + c.correlativo() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
