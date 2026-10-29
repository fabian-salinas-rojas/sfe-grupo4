package pe.sfe.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Proceso 1 de 2 del monolito modular: recibe ventas de los POS, genera el XML firmado y responde 202 sin esperar a SUNAT. */
@SpringBootApplication(scanBasePackages = "pe.sfe")
public class SfeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SfeApiApplication.class, args);
    }
}
