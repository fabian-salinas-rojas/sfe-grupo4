package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.sfe.core.app.*;
import pe.sfe.core.app.Ports.*;
import pe.sfe.core.cdr.CdrParser;
import pe.sfe.core.domain.*;
import pe.sfe.core.policy.Backoff;
import pe.sfe.core.policy.EnvioClassifier;
import pe.sfe.core.policy.ResultadoEnvio;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.SigningKey;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.soap.*;
import pe.sfe.core.support.Fixtures;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XsdValidator;

/** Casos de uso de la API y del worker con puertos falsos en memoria (sin BD ni red). */
class CasosDeUsoTest {

    static final Clock RELOJ = Clock.system(ZoneId.of("America/Lima"));
    static SigningKey llave;
    static EmisionPipeline pipeline;

    @BeforeAll
    static void init() {
        llave = Fixtures.llaveVigente();
        pipeline = new EmisionPipeline(new UblXmlBuilder(), new XsdValidator(), new XmlSigner(), SignatureAlgo.SHA256, true, RELOJ);
    }

    // ------------------------------------------------------------ fakes
    /** "Transaccion" que revierte el estado si el trabajo falla, como una BD real. */
    static class Mundo implements Ports.Transaccion, Ports.Correlativos, Ports.Emisores, Ports.Llaves, Ports.Comprobantes {
        Map<String, Long> contadores = new HashMap<>();
        Map<String, ComprobanteResumen> porIdVenta = new LinkedHashMap<>();
        /** Confirmados por OTRA transaccion: sobreviven al rollback de la nuestra. */
        Map<String, ComprobanteResumen> ajenos = new LinkedHashMap<>();
        List<NuevoComprobante> guardados = new ArrayList<>();
        boolean chocarAlRegistrar;
        SigningKey llaveActual = llave;

        @Override public <T> T ejecutar(Supplier<T> trabajo) {
            var c = new HashMap<>(contadores);
            var p = new LinkedHashMap<>(porIdVenta);
            var g = new ArrayList<>(guardados);
            try { return trabajo.get(); } catch (RuntimeException e) { contadores = c; porIdVenta = p; guardados = g; throw e; }
        }
        @Override public Asignacion asignar(long posId, TipoComprobante t, char letra) {
            String serie = switch (t) { case FACTURA -> "F001"; case BOLETA -> "B001"; default -> letra + "C01"; };
            long n = contadores.merge(serie, 1L, Long::sum);
            return new Asignacion(serie, n);
        }
        @Override public Emisor delPos(long posId) { return Fixtures.emisor(); }
        @Override public SigningKey para(Emisor e) { return llaveActual; }
        @Override public Optional<ComprobanteResumen> buscarPorIdVenta(long posId, String id) {
            return Optional.ofNullable(porIdVenta.getOrDefault(posId + ":" + id, ajenos.get(posId + ":" + id)));
        }
        @Override public Optional<ComprobanteResumen> buscarPorId(long posId, UUID id) { return porIdVenta.values().stream().filter(c -> c.id().equals(id)).findFirst(); }
        @Override public ComprobanteResumen registrar(NuevoComprobante n) {
            if (chocarAlRegistrar) {
                chocarAlRegistrar = false;
                // simula que otra peticion gano la carrera y ya inserto el mismo idVentaExterno
                ajenos.put(n.posId() + ":" + n.idVentaExterno(), new ComprobanteResumen(UUID.randomUUID(), n.tipo(), "F001", 99,
                        EstadoComprobante.PENDIENTE, n.importeTotal(), null, null));
                throw new VentaDuplicadaException("dup");
            }
            var r = new ComprobanteResumen(UUID.randomUUID(), n.tipo(), n.serie(), n.correlativo(), EstadoComprobante.PENDIENTE, n.importeTotal(), null, null);
            porIdVenta.put(n.posId() + ":" + n.idVentaExterno(), r);
            guardados.add(n);
            return r;
        }
    }

    Mundo mundo;
    EmitirComprobanteService svc;

    @BeforeEach
    void preparar() {
        mundo = new Mundo();
        svc = new EmitirComprobanteService(mundo, mundo, mundo, mundo, mundo, pipeline, RELOJ, 3);
    }

    static SolicitudEmision facturaDe(String idVenta) {
        return new SolicitudEmision(idVenta, TipoComprobante.FACTURA, "PEN",
                new Adquirente("6", "20512345678", "CLIENTE EJEMPLO SAC", null),
                List.of(Fixtures.item("Producto", "2", "50.00", "10")), null, null);
    }

    // ------------------------------------------------------------ emision
    @Test
    void emisionNumeraGeneraZipYDejaPendiente() {
        var r = svc.emitir(1, facturaDe("V-1"));
        assertFalse(r.yaExistia());
        assertEquals("F001", r.comprobante().serie());
        assertEquals(1, r.comprobante().correlativo());
        assertEquals(EstadoComprobante.PENDIENTE, r.comprobante().estado());
        assertEquals(new BigDecimal("118.00"), r.comprobante().importeTotal());
        var g = mundo.guardados.get(0);
        assertEquals("20608945123-01-F001-1.zip", g.zipNombre());
        assertTrue(g.zip().length > 500);
        assertEquals(LocalDate.now(RELOJ).plusDays(3), g.plazoLimite());
        assertEquals(64, g.xmlSha256().length());
        assertEquals(2, svc.emitir(1, facturaDe("V-2")).comprobante().correlativo());
    }

    @Test
    void reintentoDelPosConElMismoIdDevuelveElMismoComprobanteSinGastarCorrelativo() {
        var a = svc.emitir(1, facturaDe("V-1"));
        var b = svc.emitir(1, facturaDe("V-1"));
        assertTrue(b.yaExistia());
        assertEquals(a.comprobante().id(), b.comprobante().id());
        assertEquals(1L, mundo.contadores.get("F001"));
        assertEquals(1, mundo.guardados.size());
    }

    @Test
    void sinSaltosDeCorrelativoSiLaEmisionFalla() {
        svc.emitir(1, facturaDe("V-1"));
        mundo.llaveActual = SigningKey.fromPkcs12(Fixtures.pkcs12("2020/01/01 00:00:00", 30), Fixtures.PASS); // vencido
        assertThrows(pe.sfe.core.sign.CertificadoNoVigenteException.class, () -> svc.emitir(1, facturaDe("V-2")));
        assertEquals(1L, mundo.contadores.get("F001"), "el numero se revierte con la transaccion");
        mundo.llaveActual = llave;
        assertEquals(2, svc.emitir(1, facturaDe("V-2")).comprobante().correlativo());
    }

    @Test
    void carreraEntreDosPeticionesIgualesDevuelveLaGanadora() {
        mundo.chocarAlRegistrar = true;
        var r = svc.emitir(1, facturaDe("V-9"));
        assertTrue(r.yaExistia());
        assertEquals(99, r.comprobante().correlativo());
        assertEquals(0, mundo.contadores.getOrDefault("F001", 0L), "el numero de la perdedora se revirtio");
    }

    @Test
    void reglasDeNegocioFallanAntesDeTocarNada() {
        var mala = new SolicitudEmision("V-1", TipoComprobante.FACTURA, "PEN", Adquirente.consumidorFinal(),
                List.of(Fixtures.item("x", "1", "1", "10")), null, null);
        assertThrows(NegocioException.class, () -> svc.emitir(1, mala));
        assertTrue(mundo.contadores.isEmpty());
    }

    // ------------------------------------------------------------ worker
    static class ColaFalsa implements Ports.Outbox {
        List<EnvioPendiente> pendientes = new ArrayList<>();
        List<Object[]> resultados = new ArrayList<>();
        @Override public List<EnvioPendiente> reclamar(int max, Duration lease, String w, Instant ahora) {
            var l = new ArrayList<>(pendientes); pendientes.clear(); return l;
        }
        @Override public void registrarResultado(EnvioPendiente e, ResultadoEnvio r, Instant prox, Instant ahora) {
            resultados.add(new Object[]{e, r, prox, ahora});
        }
    }

    static EnvioPendiente envio(int intento) {
        return new EnvioPendiente(UUID.randomUUID(), UUID.randomUUID(), "20608945123-01-F001-1.zip", new byte[]{1}, intento, LocalDate.now().plusDays(3));
    }

    ProcesarEnviosService worker(ColaFalsa cola, BillClient client) {
        return new ProcesarEnviosService(cola, client, new EnvioClassifier(new CdrParser(new XmlSigner())),
                new Backoff(Duration.ofSeconds(10), Duration.ofMinutes(15), 0.0), RELOJ, () -> 0.5, Duration.ofSeconds(30), 10, "w1");
    }

    static BillClient clienteQue(java.util.function.Function<String, byte[]> f) {
        return new BillClient() {
            public byte[] sendBill(String n, byte[] z) { return f.apply(n); }
            public String sendSummary(String n, byte[] z) { throw new UnsupportedOperationException(); }
            public StatusResponse getStatus(String ruc, String t) { throw new UnsupportedOperationException(); }
            public StatusResponse getStatusCdr(String r, String t, String s, long n) { throw new UnsupportedOperationException(); }
        };
    }

    @Test
    void workerAceptaYRegistraElCdr() {
        var cola = new ColaFalsa();
        cola.pendientes.add(envio(1));
        var res = worker(cola, clienteQue(n -> CdrYPoliticaTest.zip("R-x.xml", CdrYPoliticaTest.cdr("0", "aceptada")))).procesarLote();
        assertEquals(1, res.aceptados());
        assertInstanceOf(ResultadoEnvio.Aceptado.class, cola.resultados.get(0)[1]);
        assertNull(cola.resultados.get(0)[2]);
    }

    @Test
    void workerReprogramaConBackoffExponencialEnRedYFaultServer() {
        var cola = new ColaFalsa();
        cola.pendientes.add(envio(1));
        cola.pendientes.add(envio(3));
        cola.pendientes.add(envio(2));
        var llamadas = new int[]{0};
        var res = worker(cola, clienteQue(n -> {
            switch (llamadas[0]++) {
                case 0 -> throw new SunatTransportException("timeout", new java.net.SocketTimeoutException("Read timed out"));
                case 1 -> throw new SunatFaultException("soap-env:Server.0135", "no disponible");
                default -> throw new SunatTransportException("conexion", new java.net.ConnectException("refused"));
            }
        })).procesarLote();
        assertEquals(3, res.reintentos());
        for (Object[] r : cola.resultados) {
            assertEquals(EstadoComprobante.EN_CONTINGENCIA, ((ResultadoEnvio) r[1]).estado());
        }
        Instant ahora = (Instant) cola.resultados.get(0)[3];
        long seg0 = Duration.between(ahora, (Instant) cola.resultados.get(0)[2]).toSeconds();
        long seg1 = Duration.between((Instant) cola.resultados.get(1)[3], (Instant) cola.resultados.get(1)[2]).toSeconds();
        long seg2 = Duration.between((Instant) cola.resultados.get(2)[3], (Instant) cola.resultados.get(2)[2]).toSeconds();
        assertEquals(10, seg0);   // intento 1
        assertEquals(40, seg1);   // intento 3
        assertEquals(20, seg2);   // intento 2
    }

    @Test
    void workerNuncaReprogramaUnRechazoNormativo() {
        var cola = new ColaFalsa();
        cola.pendientes.add(envio(1));
        cola.pendientes.add(envio(1));
        var llamadas = new int[]{0};
        var res = worker(cola, clienteQue(n -> {
            if (llamadas[0]++ == 0) return CdrYPoliticaTest.zip("R-x.xml", CdrYPoliticaTest.cdr("2335", "El numero de RUC no existe"));
            throw new SunatFaultException("soap-env:Client.3030", "rechazo como Fault");
        })).procesarLote();
        assertEquals(2, res.rechazados());
        for (Object[] r : cola.resultados) {
            assertInstanceOf(ResultadoEnvio.Rechazado.class, r[1]);
            assertNull(r[2], "un rechazo no tiene proximo intento");
            assertEquals(EstadoComprobante.RECHAZADO, ((ResultadoEnvio) r[1]).estado());
        }
    }

    @Test
    void unaFallaInesperadaNoAbortaElLote() {
        var cola = new ColaFalsa();
        cola.pendientes.addAll(List.of(envio(1), envio(1), envio(1)));
        var n = new int[]{0};
        var res = worker(cola, clienteQue(x -> {
            if (n[0]++ == 1) throw new IllegalStateException("bug inesperado");
            return CdrYPoliticaTest.zip("R-x.xml", CdrYPoliticaTest.cdr("0", "ok"));
        })).procesarLote();
        assertEquals(3, res.procesados());
        assertEquals(2, res.aceptados());
        assertEquals(1, res.reintentos());
    }

    @Test
    void excepcionDelContribuyenteQuedaParaRevisionManual() {
        var cola = new ColaFalsa();
        cola.pendientes.add(envio(1));
        var res = worker(cola, clienteQue(n -> { throw new SunatFaultException("soap-env:Client.1033", "x"); })).procesarLote();
        assertEquals(1, res.excepciones());
        assertEquals(EstadoComprobante.EXCEPCION, ((ResultadoEnvio) cola.resultados.get(0)[1]).estado());
        assertNull(cola.resultados.get(0)[2]);
    }
}
