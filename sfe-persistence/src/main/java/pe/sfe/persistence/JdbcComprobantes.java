package pe.sfe.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.EstadoComprobante;
import pe.sfe.core.domain.TipoComprobante;

/**
 * Inserta comprobante + fila de outbox + bitacora. Se invoca DENTRO de la transaccion de emision, asi la API solo
 * confirma (202) algo que ya quedo persistido junto con su tarea de envio (patron outbox transaccional).
 */
@Component
public class JdbcComprobantes implements Ports.Comprobantes {

    private static final RowMapper<Ports.ComprobanteResumen> RESUMEN = (rs, n) -> new Ports.ComprobanteResumen(
            rs.getObject("id", UUID.class),
            TipoComprobante.deCodigo(rs.getString("tipo_comprobante").trim()),
            rs.getString("serie").trim(),
            rs.getLong("correlativo"),
            EstadoComprobante.valueOf(rs.getString("estado")),
            rs.getBigDecimal("importe_total"),
            rs.getString("cdr_codigo"),
            rs.getString("cdr_descripcion"));

    private static final String SELECT = "SELECT id, tipo_comprobante, serie, correlativo, estado, importe_total, "
            + "cdr_codigo, cdr_descripcion FROM comprobante ";

    private final JdbcClient jdbc;

    public JdbcComprobantes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Ports.ComprobanteResumen> buscarPorIdVenta(long posId, String idVentaExterno) {
        return jdbc.sql(SELECT + "WHERE pos_id = :pos AND id_venta_externo = :idv")
                .param("pos", posId).param("idv", idVentaExterno).query(RESUMEN).optional();
    }

    @Override
    public Optional<Ports.ComprobanteResumen> buscarPorId(long posId, UUID id) {
        // El filtro por pos_id impide que una caja consulte comprobantes de otra (control de acceso a nivel de dato).
        return jdbc.sql(SELECT + "WHERE id = :id AND pos_id = :pos")
                .param("id", id).param("pos", posId).query(RESUMEN).optional();
    }

    @Override
    public Ports.ComprobanteResumen registrar(Ports.NuevoComprobante n) {
        UUID id = UUID.randomUUID();
        try {
            // VALUES (no INSERT ... SELECT): PostgreSQL infiere el tipo de cada parametro desde la columna destino;
            // en la lista de un SELECT los parametros sin tipo se inferirian como text y fallarian contra uuid/date.
            jdbc.sql("""
                    INSERT INTO comprobante (id, emisor_id, pos_id, id_venta_externo, tipo_comprobante, serie, correlativo,
                        fecha_emision, moneda, importe_total, estado, xml_firmado, xml_sha256, zip_nombre, zip_contenido, plazo_limite)
                    VALUES (:id, (SELECT emisor_id FROM pos WHERE id = :pos), :pos, :idv, :tipo, :serie, :correlativo,
                            :fecha, :moneda, :importe, 'PENDIENTE', :xml, :sha, :zipNombre, :zip, :plazo)
                    """)
                    .param("id", id).param("idv", n.idVentaExterno()).param("tipo", n.tipo().codigo())
                    .param("serie", n.serie()).param("correlativo", n.correlativo()).param("fecha", n.fechaEmision())
                    .param("moneda", n.moneda()).param("importe", n.importeTotal()).param("xml", n.xmlFirmado())
                    .param("sha", n.xmlSha256()).param("zipNombre", n.zipNombre()).param("zip", n.zip())
                    .param("plazo", n.plazoLimite()).param("pos", n.posId())
                    .update();
        } catch (DuplicateKeyException e) {
            String causa = e.getMostSpecificCause().getMessage();
            if (causa != null && causa.contains("uq_comprobante_idventa")) {
                throw new Ports.VentaDuplicadaException("Venta ya registrada: " + n.idVentaExterno());
            }
            throw e;
        }
        jdbc.sql("INSERT INTO envio_outbox (id, comprobante_id, proximo_intento) VALUES (:id, :cid, :ahora)")
                .param("id", UUID.randomUUID()).param("cid", id)
                .param("ahora", OffsetDateTime.ofInstant(n.creadoEn(), ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO bitacora_auditoria (comprobante_id, evento, detalle, actor) VALUES (:cid, 'EMITIDO', :d, :a)")
                .param("cid", id).param("d", n.zipNombre() + " sha256=" + n.xmlSha256()).param("a", "sfe-api").update();
        return new Ports.ComprobanteResumen(id, n.tipo(), n.serie(), n.correlativo(), EstadoComprobante.PENDIENTE,
                n.importeTotal(), null, null);
    }
}
