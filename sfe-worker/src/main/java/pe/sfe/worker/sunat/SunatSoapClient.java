package pe.sfe.worker.sunat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.dom.DOMSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import pe.sfe.core.soap.BillClient;
import pe.sfe.core.soap.StatusResponse;
import pe.sfe.core.soap.SunatCredentials;
import pe.sfe.core.soap.SunatFaultException;
import pe.sfe.core.soap.SunatSoapPayloads;
import pe.sfe.core.soap.SunatTransportException;
import pe.sfe.persistence.ConsultasEmisor;

/**
 * Cliente SOAP de billService con Spring-WS + WSS4J (UsernameToken en texto plano sobre TLS, como exige SUNAT).
 * Timeouts: 5 s de conexion y 10 s de lectura. Traduce los errores de Spring-WS a las excepciones del dominio
 * (SunatFaultException / SunatTransportException) que la politica de reintentos sabe clasificar.
 *
 * Una WebServiceTemplate por credencial (cache): el interceptor WS-Security guarda usuario/contrasena.
 * La contrasena jamas se registra; el logback enmascara cualquier rastro residual.
 */
@Component
public class SunatSoapClient implements BillClient {

    private static final Logger log = LoggerFactory.getLogger(SunatSoapClient.class);

    private final ConsultasEmisor emisores;
    private final String urlBeta;
    private final String urlProduccion;
    private final Duration timeoutConexion;
    private final Duration timeoutLectura;
    private final Map<String, WebServiceTemplate> plantillas = new ConcurrentHashMap<>();

    public SunatSoapClient(ConsultasEmisor emisores,
                           @Value("${sfe.sunat.url-beta}") String urlBeta,
                           @Value("${sfe.sunat.url-produccion}") String urlProduccion,
                           @Value("${sfe.sunat.timeout-conexion:PT5S}") Duration timeoutConexion,
                           @Value("${sfe.sunat.timeout-lectura:PT10S}") Duration timeoutLectura) {
        this.emisores = emisores;
        this.urlBeta = urlBeta;
        this.urlProduccion = urlProduccion;
        this.timeoutConexion = timeoutConexion;
        this.timeoutLectura = timeoutLectura;
    }

    @Override
    public byte[] sendBill(String zipFileName, byte[] zipContent) {
        Document respuesta = invocar(rucDe(zipFileName), SunatSoapPayloads.sendBill(zipFileName, zipContent));
        return SunatSoapPayloads.zipDeSendBillResponse(respuesta);
    }

    @Override
    public String sendSummary(String zipFileName, byte[] zipContent) {
        Document respuesta = invocar(rucDe(zipFileName), SunatSoapPayloads.sendSummary(zipFileName, zipContent));
        return SunatSoapPayloads.ticketDeSendSummaryResponse(respuesta);
    }

    @Override
    public StatusResponse getStatus(String rucEmisor, String ticket) {
        return SunatSoapPayloads.status(invocar(rucEmisor, SunatSoapPayloads.getStatus(ticket)));
    }

    @Override
    public StatusResponse getStatusCdr(String rucEmisor, String tipo, String serie, long numero) {
        return SunatSoapPayloads.status(invocar(rucEmisor, SunatSoapPayloads.getStatusCdr(rucEmisor, tipo, serie, numero)));
    }

    // ------------------------------------------------------------------ infraestructura

        private Document invocar(String ruc, Document cuerpo) {
        ConsultasEmisor.Conexion c = emisores.conexionDe(ruc)
                .orElseThrow(() -> new IllegalStateException("Emisor desconocido para el RUC " + ruc));
        String url = "BETA".equals(c.entorno()) ? urlBeta : urlProduccion;
        WebServiceTemplate plantilla = plantillas.computeIfAbsent(
                c.credenciales().username() + "|" + Integer.toHexString(c.credenciales().password().reveal().hashCode()),
                k -> nuevaPlantilla(c.credenciales()));
        try {
            Document respuesta = plantilla.sendSourceAndReceive(url, new DOMSource(cuerpo), null, SunatSoapPayloads::aDom);
            Element raiz = respuesta.getDocumentElement();
            if (raiz != null && "Fault".equals(raiz.getLocalName())) { // Spring-WS no lo detecto como Fault
                String codigo = textoHijo(raiz, "faultcode");
                String detalle = textoHijo(raiz, "faultstring");
                int i = codigo.indexOf(':');
                if (i >= 0) {
                    codigo = codigo.substring(i + 1);
                }
                log.warn("SUNAT respondio SOAP Fault {} (entorno {})", codigo, c.entorno());
                throw new SunatFaultException(codigo, detalle);
            }
            return respuesta;
        } catch (SoapFaultClientException f) {
            // faultcode de SUNAT: "soap-env:Server.0135" / "soap-env:Client.1033"; la parte local lleva el codigo.
            String codigo = f.getFaultCode() == null ? "desconocido" : f.getFaultCode().getLocalPart();
            log.warn("SUNAT respondio SOAP Fault {} (entorno {})", codigo, c.entorno());
            throw new SunatFaultException(codigo, f.getFaultStringOrReason());
        } catch (WebServiceIOException e) { // incluye WebServiceTransportException (HTTP sin SOAP Fault) y timeouts/IO
            throw new SunatTransportException("Falla de comunicacion con SUNAT (" + c.entorno() + ")", e);
        }
    }

    private static String textoHijo(Element padre, String nombre) {
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nombre.equals(e.getLocalName())) {
                return e.getTextContent().strip();
            }
        }
        return "";
    }

    private WebServiceTemplate nuevaPlantilla(SunatCredentials credenciales) {
        try {
            SaajSoapMessageFactory fabrica = new SaajSoapMessageFactory();
            fabrica.setSoapVersion(SoapVersion.SOAP_11);
            fabrica.afterPropertiesSet();

            Wss4jSecurityInterceptor wss = new Wss4jSecurityInterceptor();
            wss.setSecurementActions("UsernameToken");
            wss.setSecurementUsername(credenciales.username());          // [RUC]+[usuario SOL]  (BETA: [RUC]MODDATOS)
            wss.setSecurementPassword(credenciales.password().reveal()); // BETA: moddatos
            wss.setSecurementPasswordType("PasswordText");
            wss.afterPropertiesSet();

            HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
            sender.setConnectionTimeout(timeoutConexion);
            sender.setReadTimeout(timeoutLectura);

            WebServiceTemplate t = new WebServiceTemplate((WebServiceMessageFactory) fabrica);
            t.setMessageSender(sender);
            t.setInterceptors(new ClientInterceptor[]{wss});
            return t;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el cliente SOAP de SUNAT", e);
        }
    }

    /** El nombre del ZIP empieza con el RUC del emisor (11 digitos): de ahi salen las credenciales. */
    private static String rucDe(String zipFileName) {
        return zipFileName.substring(0, 11);
    }
}
