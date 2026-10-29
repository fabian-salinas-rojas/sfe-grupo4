package pe.sfe.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import pe.sfe.core.security.AesGcmCipher;

@Configuration
@EntityScan("pe.sfe.persistence.entity")
@EnableJpaRepositories("pe.sfe.persistence.repo")
public class PersistenceConfig {

    /** Falla al arrancar si SFE_MASTER_KEY falta o no mide 32 bytes: mejor no iniciar que cifrar con una clave debil. */
    @Bean
    AesGcmCipher aesGcmCipher(@Value("${sfe.security.master-key}") String masterKeyBase64) {
        return AesGcmCipher.fromBase64Key(masterKeyBase64);
    }
}
