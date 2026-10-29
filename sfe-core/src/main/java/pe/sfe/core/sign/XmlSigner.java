package pe.sfe.core.sign;

import java.security.Key;
import java.security.KeyException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pe.sfe.core.xml.SecureXml;
import pe.sfe.core.xml.ubl.Ns;

/**
 * XMLDSig ENVELOPED con javax.xml.crypto.dsig (JDK, sin dependencias). Manual SUNAT, seccion 3.2:
 * un solo ext:UBLExtension, la firma dentro de ext:ExtensionContent, se firma el documento completo (Reference URI="")
 * y cac:Signature (ya presente) apunta con #SignatureSP al atributo Id de ds:Signature.
 *
 * Despues de {@link #firmar} NO se debe modificar el documento: el unico paso permitido es serializarlo UNA vez.
 */
public final class XmlSigner {

    /** Atributo Id de ds:Signature; cac:Signature/.../cbc:URI lo referencia como "#SignatureSP". */
    public static final String SIGNATURE_ID = "SignatureSP";
    /** cbc:ID de cac:Signature. */
    public static final String UBL_SIGNATURE_ID = "IDSignSP";

    static {
        // El Base64 de SignatureValue/X509Certificate saldria con saltos CRLF (serializados como "&#13;").
        // Debe fijarse ANTES de que el JDK cargue su implementacion de XMLDSig (por eso va en el inicializador estatico);
        // el Dockerfile tambien lo pasa como -D por si otra clase la cargara antes.
        System.setProperty("com.sun.org.apache.xml.internal.security.ignoreLineBreaks", "true");
    }

    private final XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

    public void firmar(Document doc, SigningKey key, SignatureAlgo algo) {
        Element contenido = unicoExtensionContent(doc);
        if (contenido.getElementsByTagNameNS(Ns.DS, "Signature").getLength() > 0) {
            throw new IllegalStateException("El documento ya contiene una firma");
        }
        try {
            Reference ref = fac.newReference("", fac.newDigestMethod(algo.digestUri(), null),
                    Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(algo.signatureUri(), null),
                    Collections.singletonList(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509 = kif.newX509Data(Collections.singletonList(key.certificate()));
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(x509));

            DOMSignContext ctx = new DOMSignContext(key.privateKey(), contenido);
            ctx.setDefaultNamespacePrefix("ds");
            fac.newXMLSignature(si, ki, null, SIGNATURE_ID, null).sign(ctx);
        } catch (java.security.GeneralSecurityException | MarshalException | XMLSignatureException e) {
            throw new IllegalStateException("No se pudo firmar el documento", e);
        }
    }

    /** Autoverificacion de la firma de los bytes FINALES (los que viajaran en el ZIP). Valida integridad, no confianza. */
    public boolean verificar(byte[] xml, SignatureAlgo algo) {
        return verificar(xml, algo.secureValidationOk());
    }

    public boolean verificar(byte[] xml, boolean secureValidation) {
        Document doc = SecureXml.parse(xml);
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nl.getLength() != 1) {
            return false;
        }
        try {
            DOMValidateContext vc = new DOMValidateContext(new SelectorX509(), nl.item(0));
            vc.setProperty("org.jcp.xml.dsig.secureValidation", secureValidation);
            return fac.unmarshalXMLSignature(vc).validate(vc);
        } catch (MarshalException | XMLSignatureException e) {
            return false;
        }
    }

    private static Element unicoExtensionContent(Document doc) {
        NodeList exts = doc.getElementsByTagNameNS(Ns.EXT, "UBLExtension");
        NodeList cont = doc.getElementsByTagNameNS(Ns.EXT, "ExtensionContent");
        if (exts.getLength() != 1 || cont.getLength() != 1) {
            throw new IllegalStateException("Se espera exactamente un ext:UBLExtension/ext:ExtensionContent para la firma");
        }
        return (Element) cont.item(0);
    }

    /** Toma la llave publica del X509Certificate embebido en KeyInfo. */
    private static final class SelectorX509 extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext ctx)
                throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Sin KeyInfo");
            }
            for (Object o : keyInfo.getContent()) {
                if (o instanceof X509Data data) {
                    for (Object c : data.getContent()) {
                        if (c instanceof X509Certificate cert) {
                            PublicKey pk = cert.getPublicKey();
                            return () -> pk;
                        }
                    }
                }
            }
            throw new KeySelectorException("KeyInfo sin certificado X509");
        }
    }
}
