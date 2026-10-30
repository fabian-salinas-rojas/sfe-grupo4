package pe.sfe.api.web;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import pe.sfe.core.pdf.RepresentacionImpresa;

/** RF-06: representacion impresa (PDF con QR) del comprobante, generada desde su XML firmado. */
@RestController
@RequestMapping("/api/v1/comprobantes")
public class PdfController {

    private final ConsultaXml consultaXml;
    private final Ports.Emisores emisores;

    public PdfController(ConsultaXml consultaXml, Ports.Emisores emisores) {
        this.consultaXml = consultaXml;
        this.emisores = emisores;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal PosPrincipal pos, @PathVariable UUID id) {
        return consultaXml.porId(pos.posId(), id).map(c -> {
            Emisor emisor = emisores.delPos(pos.posId());
            byte[] pdf = RepresentacionImpresa.generar(emisor, c.tipo(), c.serie(), c.correlativo(), c.xmlFirmado());
            String nombre = emisor.ruc().trim() + "-" + c.tipo().codigo() + "-" + c.serie() + "-" + c.correlativo() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }).orElse(ResponseEntity.notFound().build());
    }
}
