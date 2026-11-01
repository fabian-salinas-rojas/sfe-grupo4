package pe.sfe.api.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import pe.sfe.core.app.ConsultaXml;
import pe.sfe.core.app.Ports;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.EstadoComprobante;
import pe.sfe.core.pdf.RepresentacionImpresa;

/**
 * RF-07: envia al cliente el PDF (con QR) y el XML firmado de un comprobante. Reglas:
 * solo se envian comprobantes aceptados por SUNAT (un comprobante pendiente o rechazado no es valido para el cliente) y
 * una caja solo puede enviar los suyos. El PDF se genera desde el XML ya firmado, igual que en RF-06.
 */
@Service
public class EnvioCorreoService {

    public enum Resultado { ENVIADO, NO_ENCONTRADO, NO_ACEPTADO }

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoService.class);

    private final JavaMailSender mail;
    private final ConsultaXml consultaXml;
    private final Ports.Comprobantes comprobantes;
    private final Ports.Emisores emisores;
    private final String remitente;

    public EnvioCorreoService(JavaMailSender mail, ConsultaXml consultaXml, Ports.Comprobantes comprobantes,
                              Ports.Emisores emisores, @Value("${sfe.mail.from:no-responder@sfe.local}") String remitente) {
        this.mail = mail;
        this.consultaXml = consultaXml;
        this.comprobantes = comprobantes;
        this.emisores = emisores;
        this.remitente = remitente;
    }

    public Resultado enviar(long posId, UUID id, String destinatario) {
        var resumen = comprobantes.buscarPorId(posId, id);
        if (resumen.isEmpty()) {
            return Resultado.NO_ENCONTRADO;
        }
        EstadoComprobante estado = resumen.get().estado();
        if (estado != EstadoComprobante.ACEPTADO && estado != EstadoComprobante.ACEPTADO_CON_OBSERVACIONES) {
            return Resultado.NO_ACEPTADO;
        }
        var xml = consultaXml.porId(posId, id);
        if (xml.isEmpty()) {
            return Resultado.NO_ENCONTRADO;
        }
        var c = xml.get();
        Emisor emisor = emisores.delPos(posId);
        String numero = c.serie() + "-" + c.correlativo();
        String base = emisor.ruc().trim() + "-" + c.tipo().codigo() + "-" + numero;
        byte[] pdf = RepresentacionImpresa.generar(emisor, c.tipo(), c.serie(), c.correlativo(), c.xmlFirmado());
        try {
            MimeMessage mensaje = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mensaje, true, "UTF-8");
            h.setFrom(remitente);
            h.setTo(destinatario);
            h.setSubject(limpiar("Comprobante electronico " + numero + " - " + emisor.razonSocial()));
            h.setText("Estimado cliente:\n\nAdjuntamos su comprobante electronico " + numero + " emitido por "
                    + limpiar(emisor.razonSocial()) + " (RUC " + emisor.ruc().trim() + ").\n"
                    + "Estado ante SUNAT: " + estado.name() + ".\n\n"
                    + "Se incluyen la representacion impresa (PDF) y el archivo XML firmado.\n", false);
            h.addAttachment(base + ".pdf", new ByteArrayResource(pdf), "application/pdf");
            h.addAttachment(base + ".xml", new ByteArrayResource(c.xmlFirmado()), "application/xml");
            mail.send(mensaje);
        } catch (MessagingException e) {
            throw new MailPreparationException("No se pudo armar el correo del comprobante " + numero, e);
        }
        // Solo el numero y el dominio: la direccion completa es dato personal y no se escribe en el log.
        log.info("RF-07 correo enviado: comprobante {} -> dominio {}", numero,
                destinatario.substring(destinatario.indexOf('@') + 1));
        return Resultado.ENVIADO;
    }

    /** Evita saltos de linea en cabeceras (inyeccion de cabeceras de correo). */
    private static String limpiar(String s) {
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
