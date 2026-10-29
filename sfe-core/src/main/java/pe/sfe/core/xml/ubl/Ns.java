package pe.sfe.core.xml.ubl;

/** Namespaces de UBL 2.1 y XMLDSig. */
public final class Ns {
    public static final String INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    public static final String CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    public static final String DEBIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";
    public static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    public static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    public static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    public static final String DS = "http://www.w3.org/2000/09/xmldsig#";

    private Ns() {
    }
}
