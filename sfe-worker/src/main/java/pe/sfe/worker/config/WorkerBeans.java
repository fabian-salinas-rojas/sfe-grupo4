package pe.sfe.worker.config;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.sfe.core.app.Ports;
import pe.sfe.core.app.ProcesarEnviosService;
import pe.sfe.core.cdr.CdrParser;
import pe.sfe.core.policy.Backoff;
import pe.sfe.core.policy.EnvioClassifier;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.soap.BillClient;

@Configuration
public class WorkerBeans {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    @Bean
    EnvioClassifier envioClassifier() {
        return new EnvioClassifier(new CdrParser(new XmlSigner()));
    }

    /**
     * lease: tiempo que un worker "es dueno" de un envio. Debe superar el peor caso de un intento
     * (conexion 5 s + lectura 10 s + proceso) y, junto con el intervalo de sondeo, mantener la recuperacion tras una
     * caida por debajo de 60 s (RNF-03): 30 s de lease + 2 s de sondeo = ~32 s.
     */
    @Bean
    ProcesarEnviosService procesarEnviosService(Ports.Outbox outbox, BillClient client, EnvioClassifier classifier, Clock reloj,
                                                @Value("${sfe.worker.lease:PT30S}") Duration lease,
                                                @Value("${sfe.worker.batch-size:10}") int tamanoLote,
                                                @Value("${sfe.worker.backoff-base:PT10S}") Duration base,
                                                @Value("${sfe.worker.backoff-cap:PT15M}") Duration tope) {
        return new ProcesarEnviosService(outbox, client, classifier, new Backoff(base, tope, 0.2), reloj,
                () -> ThreadLocalRandom.current().nextDouble(), lease, tamanoLote, identificadorWorker());
    }

    private static String identificadorWorker() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "worker-" + ManagementFactory.getRuntimeMXBean().getPid();
        }
    }
}
