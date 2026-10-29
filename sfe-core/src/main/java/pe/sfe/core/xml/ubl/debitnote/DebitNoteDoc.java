package pe.sfe.core.xml.ubl.debitnote;

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

/** Nota de debito electronica (08): usa RequestedMonetaryTotal en lugar de LegalMonetaryTotal. */
@XmlRootElement(name = "DebitNote", namespace = Ns.DEBIT_NOTE)
@XmlType(name = "DebitNoteDoc", namespace = Ns.DEBIT_NOTE, propOrder = {
        "ublExtensions", "ublVersionId", "customizationId", "id", "issueDate", "issueTime", "notes", "documentCurrencyCode", "discrepancyResponse",
        "billingReference", "signature", "accountingSupplierParty", "accountingCustomerParty", "paymentTerms",
        "taxTotal", "requestedMonetaryTotal", "lines"})
@XmlAccessorType(XmlAccessType.FIELD)
public class DebitNoteDoc extends UblDocumentBase {

    @XmlElement(name = "RequestedMonetaryTotal", namespace = Ns.CAC) public Ubl.TotalMonetario requestedMonetaryTotal;
    @XmlElement(name = "DebitNoteLine", namespace = Ns.CAC) public List<Ubl.Linea> lines = new ArrayList<>();
}
