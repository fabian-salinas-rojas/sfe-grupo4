package pe.sfe.core.xml.ubl.invoice;

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

/** Factura (01) y boleta de venta (03): ambas usan la raiz Invoice de UBL 2.1. */
@XmlRootElement(name = "Invoice", namespace = Ns.INVOICE)
@XmlType(name = "InvoiceDoc", namespace = Ns.INVOICE, propOrder = {
        "ublExtensions", "ublVersionId", "customizationId", "id", "issueDate", "issueTime", "invoiceTypeCode", "notes", "documentCurrencyCode", "discrepancyResponse",
        "billingReference", "signature", "accountingSupplierParty", "accountingCustomerParty", "paymentTerms",
        "taxTotal", "legalMonetaryTotal", "lines"})
@XmlAccessorType(XmlAccessType.FIELD)
public class InvoiceDoc extends UblDocumentBase {

    @XmlElement(name = "InvoiceTypeCode", namespace = Ns.CBC) public Ubl.Codigo invoiceTypeCode;
    @XmlElement(name = "LegalMonetaryTotal", namespace = Ns.CAC) public Ubl.TotalMonetario legalMonetaryTotal;
    @XmlElement(name = "InvoiceLine", namespace = Ns.CAC) public List<Ubl.Linea> lines = new ArrayList<>();
}
