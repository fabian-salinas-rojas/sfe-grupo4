package pe.sfe.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.security.AesGcmCipher;
import pe.sfe.core.soap.SunatCredentials;

/** Emisor del POS (API) y credenciales SUNAT por RUC (worker). La Clave SOL se descifra solo al usarla. */
@Component
public class ConsultasEmisor implements Ports.Emisores {

    public record Conexion(String entorno, SunatCredentials credenciales) {
    }

    private final JdbcClient jdbc;
    private final AesGcmCipher cipher;
    private final String passwordBeta;

    public ConsultasEmisor(JdbcClient jdbc, AesGcmCipher cipher,
                           @org.springframework.beans.factory.annotation.Value("${sfe.sunat.beta-password:moddatos}") String passwordBeta) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.passwordBeta = passwordBeta;
    }

    @Override
    public Emisor delPos(long posId) {
        return jdbc.sql("SELECT e.ruc, e.razon_social, e.nombre_comercial, e.ubigeo, e.direccion, e.departamento, "
                        + "e.provincia, e.distrito FROM emisor e JOIN pos p ON p.emisor_id = e.id WHERE p.id = :pos")
                .param("pos", posId)
                .query((rs, n) -> new Emisor(rs.getString("ruc").trim(), rs.getString("razon_social"),
                        rs.getString("nombre_comercial"), rs.getString("ubigeo").trim(), rs.getString("direccion"),
                        rs.getString("departamento"), rs.getString("provincia"), rs.getString("distrito")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("POS sin emisor: " + posId));
    }

    /** BETA -> [RUC]MODDATOS/moddatos; PRODUCCION -> RUC+usuario SOL y Clave SOL descifrada (nunca se registra). */
    public Optional<Conexion> conexionDe(String ruc) {
        return jdbc.sql("SELECT entorno, sol_usuario, sol_clave_cifrada FROM emisor WHERE ruc = :ruc")
                .param("ruc", ruc)
                .query((rs, n) -> {
                    String entorno = rs.getString("entorno");
                    if ("BETA".equals(entorno)) {
                        return new Conexion(entorno, SunatCredentials.beta(ruc, passwordBeta));
                    }
                    String usuario = rs.getString("sol_usuario");
                    String cifrada = rs.getString("sol_clave_cifrada");
                    if (usuario == null || cifrada == null) {
                        throw new IllegalStateException("Emisor " + ruc + " en PRODUCCION sin credenciales SOL configuradas");
                    }
                    return new Conexion(entorno, SunatCredentials.produccion(ruc, usuario,
                            cipher.decryptToSecret(cifrada, "emisor:" + ruc + ":sol")));
                })
                .optional();
    }
}
