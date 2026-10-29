package pe.sfe.core.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import pe.sfe.core.app.Ports.Asignacion;
import pe.sfe.core.app.Ports.ComprobanteResumen;
import pe.sfe.core.app.Ports.NuevoComprobante;
import pe.sfe.core.app.Ports.VentaDuplicadaException;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.ReglasNegocio;
import pe.sfe.core.domain.SolicitudEmision;

/**
 * Caso de uso de la API (sfe-api): valida, numera, genera XML firmado + ZIP, y deja el envio en el outbox,
 * todo en UNA transaccion. No toca a SUNAT: por eso el POS recibe 202 de inmediato.
 * Idempotente por (POS, idVentaExterno): un reintento del POS devuelve el mismo comprobante sin gastar otro correlativo.
 */
public final class EmitirComprobanteService {

    public record Resultado(ComprobanteResumen comprobante, boolean yaExistia) {
    }

    private final Ports.Transaccion tx;
    private final Ports.Emisores emisores;
    private final Ports.Llaves llaves;
    private final Ports.Correlativos correlativos;
    private final Ports.Comprobantes comprobantes;
    private final EmisionPipeline pipeline;
    private final Clock clock;
    private final int plazoEnvioDias;

    public EmitirComprobanteService(Ports.Transaccion tx, Ports.Emisores emisores, Ports.Llaves llaves,
                                    Ports.Correlativos correlativos, Ports.Comprobantes comprobantes,
                                    EmisionPipeline pipeline, Clock clock, int plazoEnvioDias) {
        this.tx = tx;
        this.emisores = emisores;
        this.llaves = llaves;
        this.correlativos = correlativos;
        this.comprobantes = comprobantes;
        this.pipeline = pipeline;
        this.clock = clock;
        this.plazoEnvioDias = plazoEnvioDias;
    }

    public Resultado emitir(long posId, SolicitudEmision s) {
        ReglasNegocio.validar(s);
        var previo = tx.ejecutar(() -> comprobantes.buscarPorIdVenta(posId, s.idVentaExterno()));
        if (previo.isPresent()) {
            return new Resultado(previo.get(), true);
        }
        try {
            return tx.ejecutar(() -> new Resultado(emitirNuevo(posId, s), false));
        } catch (VentaDuplicadaException carrera) {
            // Dos peticiones simultaneas con el mismo idVentaExterno: gano la otra; devolvemos su comprobante.
            var ganador = tx.ejecutar(() -> comprobantes.buscarPorIdVenta(posId, s.idVentaExterno()));
            return new Resultado(ganador.orElseThrow(() -> carrera), true);
        }
    }

    private ComprobanteResumen emitirNuevo(long posId, SolicitudEmision s) {
        Emisor emisor = emisores.delPos(posId);
        var llave = llaves.para(emisor);
        Asignacion num = correlativos.asignar(posId, s.tipo(), s.letraSerie());

        ZonedDateTime ahora = ZonedDateTime.now(clock);
        LocalDate fecha = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime().withNano(0);
        DocumentoVenta doc = new DocumentoVenta(s.tipo(), num.serie(), num.correlativo(), fecha, hora, s.moneda(),
                emisor, s.adquirente(), s.items(), s.referencia(), s.motivo());

        ComprobanteGenerado g = pipeline.generar(doc, llave);
        return comprobantes.registrar(new NuevoComprobante(posId, s.idVentaExterno(), s.tipo(), num.serie(),
                num.correlativo(), fecha, s.moneda(), g.totales().importeTotal(), g.xmlFirmado(), g.xmlSha256(),
                g.nombre().zip(), g.zip(), fecha.plusDays(plazoEnvioDias), clock.instant()));
    }
}
