package pe.sfe.core.sign;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.Enumeration;
import pe.sfe.core.security.Secret;

/**
 * Llave privada + certificado X.509 v3 del emisor, cargados de un PKCS#12. No expone la contrasena ni la llave
 * en toString (nunca debe llegar a un log). El manual (3.1) exige longitud minima de 1024 bits.
 */
public final class SigningKey {

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    public SigningKey(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public static SigningKey fromPkcs12(byte[] pkcs12, Secret password) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pkcs12), password.reveal().toCharArray());
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isKeyEntry(alias)) {
                    PrivateKey pk = (PrivateKey) ks.getKey(alias, password.reveal().toCharArray());
                    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
                    SigningKey k = new SigningKey(pk, cert);
                    k.validarRequisitosTecnicos();
                    return k;
                }
            }
            throw new IllegalArgumentException("El PKCS#12 no contiene una llave privada");
        } catch (IOException e) {
            // Contrasena incorrecta o archivo danado. Mensaje generico: no se refleja la contrasena.
            throw new IllegalArgumentException("No se pudo abrir el PKCS#12 (contrasena incorrecta o archivo invalido)");
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("No se pudo leer el certificado digital", e);
        }
    }

    private void validarRequisitosTecnicos() {
        if (certificate.getVersion() != 3) {
            throw new IllegalArgumentException("El certificado debe ser X.509 v3");
        }
        if (certificate.getPublicKey() instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() < 1024) {
            throw new IllegalArgumentException("La llave del certificado debe tener al menos 1024 bits");
        }
    }

    /** SUNAT valida vigencia (y no revocacion) en el receptor; aqui se evita enviar algo condenado al rechazo. */
    public void assertVigente(Clock clock) {
        try {
            certificate.checkValidity(Date.from(clock.instant()));
        } catch (CertificateException e) {
            throw new CertificadoNoVigenteException("El certificado digital no esta vigente: " + e.getMessage(), e);
        }
    }

    /**
     * Manual SUNAT 3.1: el RUC de la empresa debe constar en el campo OU (Organizational Unit) del Subject del certificado.
     */
    public boolean perteneceARuc(String ruc) {
        try {
            for (javax.naming.ldap.Rdn rdn : new javax.naming.ldap.LdapName(
                    certificate.getSubjectX500Principal().getName()).getRdns()) {
                if ("OU".equalsIgnoreCase(rdn.getType()) && ruc.equals(String.valueOf(rdn.getValue()))) {
                    return true;
                }
            }
            return false;
        } catch (javax.naming.InvalidNameException e) {
            return false;
        }
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    @Override
    public String toString() {
        return "SigningKey[subject=" + certificate.getSubjectX500Principal().getName() + ", privateKey=***]";
    }
}
