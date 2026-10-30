package pe.sfe.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.ConsultaXml;
import pe.sfe.core.domain.TipoComprobante;

/** RF-06: lee el XML firmado (solo lectura). El filtro por pos_id impide ver comprobantes de otra caja. */
@Component
public class JdbcConsultaXml implements ConsultaXml {

    private final JdbcClient jdbc;

    public JdbcConsultaXml(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<XmlComprobante> porId(long posId, UUID id) {
        return jdbc.sql("SELECT tipo_comprobante, serie, correlativo, xml_firmado FROM comprobante "
                        + "WHERE id = :id AND pos_id = :pos")
                .param("id", id).param("pos", posId)
                .query((rs, n) -> new XmlComprobante(
                        TipoComprobante.deCodigo(rs.getString("tipo_comprobante").trim()),
                        rs.getString("serie").trim(),
                        rs.getLong("correlativo"),
                        rs.getBytes("xml_firmado")))
                .optional();
    }
}
