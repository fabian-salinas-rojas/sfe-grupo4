package pe.sfe.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Proceso 2 de 2: consume el outbox y habla con SUNAT. Se pueden levantar N instancias (SKIP LOCKED las coordina). */
@SpringBootApplication(scanBasePackages = "pe.sfe")
@EnableScheduling
public class SfeWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SfeWorkerApplication.class, args);
    }
}
