package pe.sfe.core.sign;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;

/** Algoritmo de firma configurable (Stack Ideal, seccion 5: confirmar SHA-256 vs SHA-1 en BETA / guia vigente). */
public enum SignatureAlgo {
    SHA256("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", DigestMethod.SHA256, true),
    SHA1(SignatureMethod.RSA_SHA1, DigestMethod.SHA1, false);

    private final String signatureUri;
    private final String digestUri;
    private final boolean secureValidationOk;

    SignatureAlgo(String signatureUri, String digestUri, boolean secureValidationOk) {
        this.signatureUri = signatureUri;
        this.digestUri = digestUri;
        this.secureValidationOk = secureValidationOk;
    }

    public String signatureUri() {
        return signatureUri;
    }

    public String digestUri() {
        return digestUri;
    }

    /** El JDK 21 rechaza SHA-1 en su modo "secureValidation"; para autoverificar SHA-1 hay que relajarlo. */
    public boolean secureValidationOk() {
        return secureValidationOk;
    }
}
