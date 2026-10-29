package pe.sfe.core.xml.ubl.creditnote;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;
import pe.sfe.core.xml.ubl.Ns;
import pe.sfe.core.xml.ubl.Ubl;
import pe.sfe.core.xml.ubl.UblDocumentBase;

/** Nota de credito electronica (07). */
@XmlRootElement(name = "CreditNote", namespace = Ns.CREDIT_NOTE)
@XmlType(name = "CreditNoteDoc", namespace = Ns.CREDIT_NOTE, propOrder = {
        "ublExtensions", "ublVersionId", "customizationId", "id", "issueDate", "issueTime", "notes", "documentCurrencyCode", "discrepancyResponse",
        "billingReference", "signature", "accountingSupplierParty", "accountingCustomerParty", "paymentTerms",
        "taxTotal", "legalMonetaryTotal", "lines"})
@XmlAccessorType(XmlAccessType.FIELD)
public class CreditNoteDoc extends UblDocumentBase {

    @XmlElement(name = "LegalMonetaryTotal", namespace = Ns.CAC) public Ubl.TotalMonetario legalMonetaryTotal;
    @XmlElement(name = "CreditNoteLine", namespace = Ns.CAC) public List<Ubl.Linea> lines = new ArrayList<>();
}
