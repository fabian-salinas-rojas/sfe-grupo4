package pe.sfe.core.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import pe.sfe.core.app.ComprobanteGenerado;
import pe.sfe.core.app.EmisionPipeline;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XsdValidator;

/** Genera XML y ZIP de ejemplo en el directorio dado (args[0]). Solo para documentacion; usa una llave de prueba. */
public final class EjemplosMain {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        EmisionPipeline p = new EmisionPipeline(new UblXmlBuilder(), new XsdValidator(), new XmlSigner(),
                SignatureAlgo.SHA256, true, Clock.system(ZoneId.of("America/Lima")));
        var llave = Fixtures.llaveVigente();
        for (DocumentoVenta d : new DocumentoVenta[]{Fixtures.factura(), Fixtures.boleta(), Fixtures.notaCredito(), Fixtures.notaDebito()}) {
            ComprobanteGenerado g = p.generar(d, llave);
            Files.write(out.resolve(g.nombre().xml()), g.xmlFirmado());
            Files.write(out.resolve(g.nombre().zip()), g.zip());
        }
    }
}
