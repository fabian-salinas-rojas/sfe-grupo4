package pe.sfe.api.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.sfe.core.app.EmisionPipeline;
import pe.sfe.core.app.EmitirComprobanteService;
import pe.sfe.core.app.Ports;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XsdValidator;

@Configuration
public class ApiBeans {

    /** Toda fecha/hora de emision usa America/Lima (los comprobantes llevan la hora local del emisor). */
    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    /** Compila los XSD de UBL una sola vez al arrancar (varios segundos); despues es inmutable y thread-safe. */
    @Bean
    XsdValidator xsdValidator() {
        return new XsdValidator();
    }

    @Bean
    EmisionPipeline emisionPipeline(XsdValidator xsd, Clock reloj,
                                    @Value("${sfe.signing.algorithm:SHA256}") SignatureAlgo algoritmo,
                                    @Value("${sfe.zip.dummy-folder:false}") boolean carpetaDummy) {
        return new EmisionPipeline(new UblXmlBuilder(), xsd, new XmlSigner(), algoritmo, carpetaDummy, reloj);
    }

    @Bean
    EmitirComprobanteService emitirComprobanteService(Ports.Transaccion tx, Ports.Emisores emisores, Ports.Llaves llaves,
                                                      Ports.Correlativos correlativos, Ports.Comprobantes comprobantes,
                                                      EmisionPipeline pipeline, Clock reloj,
                                                      @Value("${sfe.emision.plazo-envio-dias:3}") int plazoEnvioDias) {
        return new EmitirComprobanteService(tx, emisores, llaves, correlativos, comprobantes, pipeline, reloj, plazoEnvioDias);
    }
}
