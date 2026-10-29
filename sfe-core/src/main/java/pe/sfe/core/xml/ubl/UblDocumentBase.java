package pe.sfe.core.xml.ubl;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.List;

/**
 * Campos comunes de Invoice / CreditNote / DebitNote. El ORDEN lo fija el propOrder de cada raiz
 * (el XSD de UBL es una secuencia estricta). Los campos propios de cada documento viven en la subclase.
 */
@XmlTransient
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class UblDocumentBase {

    @XmlElement(name = "UBLExtensions", namespace = Ns.EXT) public Ubl.UblExtensions ublExtensions;
    @XmlElement(name = "UBLVersionID", namespace = Ns.CBC) public String ublVersionId;
    @XmlElement(name = "CustomizationID", namespace = Ns.CBC) public String customizationId;
    @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
    @XmlElement(name = "IssueDate", namespace = Ns.CBC) public String issueDate;
    @XmlElement(name = "IssueTime", namespace = Ns.CBC) public String issueTime;
    @XmlElement(name = "Note", namespace = Ns.CBC) public List<Ubl.NotaTexto> notes = new ArrayList<>();
    @XmlElement(name = "DocumentCurrencyCode", namespace = Ns.CBC) public String documentCurrencyCode;
    @XmlElement(name = "DiscrepancyResponse", namespace = Ns.CAC) public List<Ubl.DiscrepancyResponse> discrepancyResponse = new ArrayList<>();
    @XmlElement(name = "BillingReference", namespace = Ns.CAC) public List<Ubl.BillingReference> billingReference = new ArrayList<>();
    @XmlElement(name = "Signature", namespace = Ns.CAC) public Ubl.Firma signature;
    @XmlElement(name = "AccountingSupplierParty", namespace = Ns.CAC) public Ubl.ParteContable accountingSupplierParty;
    @XmlElement(name = "AccountingCustomerParty", namespace = Ns.CAC) public Ubl.ParteContable accountingCustomerParty;
    @XmlElement(name = "PaymentTerms", namespace = Ns.CAC) public List<Ubl.PaymentTerms> paymentTerms = new ArrayList<>();
    @XmlElement(name = "TaxTotal", namespace = Ns.CAC) public List<Ubl.TaxTotal> taxTotal = new ArrayList<>();
}
