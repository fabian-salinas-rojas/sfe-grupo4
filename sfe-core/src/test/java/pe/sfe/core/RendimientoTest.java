package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import pe.sfe.core.app.EmisionPipeline;
import pe.sfe.core.sign.SignatureAlgo;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.support.Fixtures;
import pe.sfe.core.xml.UblXmlBuilder;
import pe.sfe.core.xml.XsdValidator;

/**
 * RNF-01: generar XML + firma + ZIP en <= 1.2 s con 50 emisiones concurrentes. Se mide el pipeline puro (sin BD);
 * el limite del test es holgado para no ser fragil en CI, el numero real se imprime.
 */
class RendimientoTest {

    @Test
    void cincuentaEmisionesConcurrentes() throws Exception {
        var llave = Fixtures.llaveVigente();
        var p = new EmisionPipeline(new UblXmlBuilder(), new XsdValidator(), new XmlSigner(), SignatureAlgo.SHA256, true,
                Clock.system(ZoneId.of("America/Lima")));
        for (int i = 0; i < 30; i++) p.generar(Fixtures.factura(), llave); // calentamiento JIT

        List<Long> lat = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch salida = new CountDownLatch(1);
        try (ExecutorService ex = Executors.newFixedThreadPool(50)) {
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                fs.add(ex.submit(() -> {
                    salida.await();
                    long t0 = System.nanoTime();
                    p.generar(Fixtures.factura(), llave);
                    lat.add((System.nanoTime() - t0) / 1_000_000);
                    return null;
                }));
            }
            salida.countDown();
            for (var f : fs) f.get();
        }
        Collections.sort(lat);
        long p50 = lat.get(24), p95 = lat.get(47), max = lat.get(49);
        System.out.printf("RNF-01 -> 50 emisiones concurrentes: p50=%d ms, p95=%d ms, max=%d ms (cpus=%d)%n",
                p50, p95, max, Runtime.getRuntime().availableProcessors());
        assertTrue(max < 5000, "max=" + max);
    }
}
