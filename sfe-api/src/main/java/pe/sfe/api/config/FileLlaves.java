package pe.sfe.api.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.security.Secret;
import pe.sfe.core.sign.SigningKey;

/**
 * Certificado digital del emisor desde un PKCS#12 montado como secreto (fuera de la imagen y del repositorio).
 * Se carga UNA vez al arrancar: si la ruta o la contrasena estan mal, la API no inicia (fail-fast).
 * Prototipo: un certificado por despliegue. Con varios emisores, cargar uno por emisor desde un almacen de secretos.
 */
@Component
public class FileLlaves implements Ports.Llaves {

    private final SigningKey llave;
    private final boolean exigirRucEnOu;

    public FileLlaves(@Value("${sfe.signing.keystore-path}") Path ruta,
                      @Value("${sfe.signing.keystore-password}") String contrasena,
                      @Value("${sfe.signing.require-ruc-in-ou:true}") boolean exigirRucEnOu) throws IOException {
        this.llave = SigningKey.fromPkcs12(Files.readAllBytes(ruta), Secret.of(contrasena));
        this.exigirRucEnOu = exigirRucEnOu;
    }

    @Override
    public SigningKey para(Emisor emisor) {
        if (exigirRucEnOu && !llave.perteneceARuc(emisor.ruc())) {
            throw new IllegalStateException("El certificado digital no corresponde al RUC " + emisor.ruc()
                    + " (el RUC debe estar en el campo OU del Subject)");
        }
        return llave;
    }
}
