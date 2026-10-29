package pe.sfe.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.TipoComprobante;

/**
 * Correlativo sin saltos: UPDATE ... RETURNING sobre la fila de la serie, dentro de la transaccion de emision
 * (bloqueo de fila por serie). Debe llamarse con una transaccion activa; si esa transaccion hace rollback, el numero vuelve.
 */
@Component
public class JdbcCorrelativos implements Ports.Correlativos {

    private final JdbcClient jdbc;
    private final String sql = SqlResources.cargar("serie-siguiente-correlativo.sql");

    public JdbcCorrelativos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Ports.Asignacion asignar(long posId, TipoComprobante tipo, char letraSerie) {
        return jdbc.sql(sql)
                .param("pos", posId)
                .param("tipo", tipo.codigo())
                .param("letra", String.valueOf(letraSerie))
                .query((rs, n) -> new Ports.Asignacion(rs.getString("serie").trim(), rs.getLong("ultimo_correlativo")))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "El POS " + posId + " no tiene una serie configurada para tipo " + tipo.codigo() + " y letra " + letraSerie));
    }
}
