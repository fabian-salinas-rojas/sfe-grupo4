package pe.sfe.core.xml.ubl;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 * Subconjunto de UBL 2.1 (CommonAggregateComponents / CommonBasicComponents / Extension) que SUNAT usa
 * en factura, boleta, nota de credito y nota de debito. El modelo es deliberadamente mas chico que el
 * UBL completo: la conformidad la garantiza la validacion contra el XSD oficial (XsdValidator) ANTES de firmar.
 * Los montos viajan como String ya formateado (2 decimales fijos) para evitar notacion cientifica.
 */
public final class Ubl {

    private Ubl() {
    }

    // ------------------------------------------------------------------ tipos cbc con atributos

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IdEsquema {
        @XmlValue public String value;
        @XmlAttribute public String schemeID;
        @XmlAttribute public String schemeName;
        @XmlAttribute public String schemeAgencyName;
        @XmlAttribute public String schemeURI;

        public static IdEsquema of(String value, String schemeID, String schemeName, String agency, String uri) {
            IdEsquema i = new IdEsquema();
            i.value = value;
            i.schemeID = schemeID;
            i.schemeName = schemeName;
            i.schemeAgencyName = agency;
            i.schemeURI = uri;
            return i;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Codigo {
        @XmlValue public String value;
        @XmlAttribute public String listAgencyName;
        @XmlAttribute public String listName;
        @XmlAttribute public String listURI;
        @XmlAttribute public String listID;

        public static Codigo of(String value, String listName, String listURI) {
            Codigo c = new Codigo();
            c.value = value;
            c.listAgencyName = "PE:SUNAT";
            c.listName = listName;
            c.listURI = listURI;
            return c;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Monto {
        @XmlValue public String value;
        @XmlAttribute public String currencyID;

        public static Monto of(String value, String currency) {
            Monto m = new Monto();
            m.value = value;
            m.currencyID = currency;
            return m;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Cantidad {
        @XmlValue public String value;
        @XmlAttribute public String unitCode;
        @XmlAttribute public String unitCodeListID;
        @XmlAttribute public String unitCodeListAgencyName;

        public static Cantidad of(String value, String unitCode) {
            Cantidad c = new Cantidad();
            c.value = value;
            c.unitCode = unitCode;
            c.unitCodeListID = "UN/ECE rec 20";
            c.unitCodeListAgencyName = "United Nations Economic Commission for Europe";
            return c;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NotaTexto {
        @XmlValue public String value;
        @XmlAttribute public String languageLocaleID;

        public static NotaTexto of(String value, String languageLocaleID) {
            NotaTexto n = new NotaTexto();
            n.value = value;
            n.languageLocaleID = languageLocaleID;
            return n;
        }
    }

    // ------------------------------------------------------------------ extension + firma

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UblExtensions {
        @XmlElement(name = "UBLExtension", namespace = Ns.EXT)
        public List<UblExtension> extensions = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UblExtension {
        @XmlElement(name = "ExtensionContent", namespace = Ns.EXT)
        public ExtensionContent content = new ExtensionContent();
    }

    /** Vacio antes de firmar: ahi el firmante inserta ds:Signature (manual SUNAT, 3.2). */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ExtensionContent {
        @XmlAnyElement public Element any;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "signatoryParty", "digitalSignatureAttachment"})
    public static class Firma {
        @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
        @XmlElement(name = "SignatoryParty", namespace = Ns.CAC) public Firmante signatoryParty;
        @XmlElement(name = "DigitalSignatureAttachment", namespace = Ns.CAC) public FirmaAdjunta digitalSignatureAttachment;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"partyIdentification", "partyName"})
    public static class Firmante {
        @XmlElement(name = "PartyIdentification", namespace = Ns.CAC) public PartyIdentification partyIdentification;
        @XmlElement(name = "PartyName", namespace = Ns.CAC) public PartyName partyName;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FirmaAdjunta {
        @XmlElement(name = "ExternalReference", namespace = Ns.CAC) public ExternalReference externalReference;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ExternalReference {
        @XmlElement(name = "URI", namespace = Ns.CBC) public String uri;
    }

    // ------------------------------------------------------------------ partes

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PartyIdentification {
        @XmlElement(name = "ID", namespace = Ns.CBC) public IdEsquema id;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PartyName {
        @XmlElement(name = "Name", namespace = Ns.CBC) public String name;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"registrationName", "registrationAddress"})
    public static class PartyLegalEntity {
        @XmlElement(name = "RegistrationName", namespace = Ns.CBC) public String registrationName;
        @XmlElement(name = "RegistrationAddress", namespace = Ns.CAC) public Direccion registrationAddress;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "addressTypeCode", "cityName", "countrySubentity", "district", "addressLine", "country"})
    public static class Direccion {
        @XmlElement(name = "ID", namespace = Ns.CBC) public IdEsquema id;
        @XmlElement(name = "AddressTypeCode", namespace = Ns.CBC) public String addressTypeCode;
        @XmlElement(name = "CityName", namespace = Ns.CBC) public String cityName;
        @XmlElement(name = "CountrySubentity", namespace = Ns.CBC) public String countrySubentity;
        @XmlElement(name = "District", namespace = Ns.CBC) public String district;
        @XmlElement(name = "AddressLine", namespace = Ns.CAC) public List<AddressLine> addressLine = new ArrayList<>();
        @XmlElement(name = "Country", namespace = Ns.CAC) public Country country;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AddressLine {
        @XmlElement(name = "Line", namespace = Ns.CBC) public String line;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Country {
        @XmlElement(name = "IdentificationCode", namespace = Ns.CBC) public String identificationCode;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"partyIdentification", "partyName", "partyLegalEntity"})
    public static class Party {
        @XmlElement(name = "PartyIdentification", namespace = Ns.CAC) public List<PartyIdentification> partyIdentification = new ArrayList<>();
        @XmlElement(name = "PartyName", namespace = Ns.CAC) public List<PartyName> partyName = new ArrayList<>();
        @XmlElement(name = "PartyLegalEntity", namespace = Ns.CAC) public List<PartyLegalEntity> partyLegalEntity = new ArrayList<>();
    }

    /** Sirve para AccountingSupplierParty y AccountingCustomerParty. */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ParteContable {
        @XmlElement(name = "Party", namespace = Ns.CAC) public Party party;
    }

    // ------------------------------------------------------------------ pago, tributos, totales

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "paymentMeansId"})
    public static class PaymentTerms {
        @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
        @XmlElement(name = "PaymentMeansID", namespace = Ns.CBC) public String paymentMeansId;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"taxAmount", "taxSubtotal"})
    public static class TaxTotal {
        @XmlElement(name = "TaxAmount", namespace = Ns.CBC) public Monto taxAmount;
        @XmlElement(name = "TaxSubtotal", namespace = Ns.CAC) public List<TaxSubtotal> taxSubtotal = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"taxableAmount", "taxAmount", "taxCategory"})
    public static class TaxSubtotal {
        @XmlElement(name = "TaxableAmount", namespace = Ns.CBC) public Monto taxableAmount;
        @XmlElement(name = "TaxAmount", namespace = Ns.CBC) public Monto taxAmount;
        @XmlElement(name = "TaxCategory", namespace = Ns.CAC) public TaxCategory taxCategory;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "percent", "taxExemptionReasonCode", "taxScheme"})
    public static class TaxCategory {
        @XmlElement(name = "ID", namespace = Ns.CBC) public IdEsquema id;
        @XmlElement(name = "Percent", namespace = Ns.CBC) public String percent;
        @XmlElement(name = "TaxExemptionReasonCode", namespace = Ns.CBC) public Codigo taxExemptionReasonCode;
        @XmlElement(name = "TaxScheme", namespace = Ns.CAC) public TaxScheme taxScheme;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "name", "taxTypeCode"})
    public static class TaxScheme {
        @XmlElement(name = "ID", namespace = Ns.CBC) public IdEsquema id;
        @XmlElement(name = "Name", namespace = Ns.CBC) public String name;
        @XmlElement(name = "TaxTypeCode", namespace = Ns.CBC) public String taxTypeCode;
    }

    /** LegalMonetaryTotal (factura, boleta, nota de credito) o RequestedMonetaryTotal (nota de debito). */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"lineExtensionAmount", "taxInclusiveAmount", "payableAmount"})
    public static class TotalMonetario {
        @XmlElement(name = "LineExtensionAmount", namespace = Ns.CBC) public Monto lineExtensionAmount;
        @XmlElement(name = "TaxInclusiveAmount", namespace = Ns.CBC) public Monto taxInclusiveAmount;
        @XmlElement(name = "PayableAmount", namespace = Ns.CBC) public Monto payableAmount;
    }

    // ------------------------------------------------------------------ lineas

    /**
     * Una sola clase para InvoiceLine / CreditNoteLine / DebitNoteLine: solo uno de los tres
     * "Quantity" es no nulo; el nombre del elemento de linea lo fija la clase raiz.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "invoicedQuantity", "creditedQuantity", "debitedQuantity", "lineExtensionAmount",
            "pricingReference", "taxTotal", "item", "price"})
    public static class Linea {
        @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
        @XmlElement(name = "InvoicedQuantity", namespace = Ns.CBC) public Cantidad invoicedQuantity;
        @XmlElement(name = "CreditedQuantity", namespace = Ns.CBC) public Cantidad creditedQuantity;
        @XmlElement(name = "DebitedQuantity", namespace = Ns.CBC) public Cantidad debitedQuantity;
        @XmlElement(name = "LineExtensionAmount", namespace = Ns.CBC) public Monto lineExtensionAmount;
        @XmlElement(name = "PricingReference", namespace = Ns.CAC) public PricingReference pricingReference;
        @XmlElement(name = "TaxTotal", namespace = Ns.CAC) public TaxTotal taxTotal;
        @XmlElement(name = "Item", namespace = Ns.CAC) public Item item;
        @XmlElement(name = "Price", namespace = Ns.CAC) public Price price;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PricingReference {
        @XmlElement(name = "AlternativeConditionPrice", namespace = Ns.CAC)
        public List<AlternativeConditionPrice> alternativeConditionPrice = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"priceAmount", "priceTypeCode"})
    public static class AlternativeConditionPrice {
        @XmlElement(name = "PriceAmount", namespace = Ns.CBC) public Monto priceAmount;
        @XmlElement(name = "PriceTypeCode", namespace = Ns.CBC) public Codigo priceTypeCode;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"description", "sellersItemIdentification"})
    public static class Item {
        @XmlElement(name = "Description", namespace = Ns.CBC) public List<String> description = new ArrayList<>();
        @XmlElement(name = "SellersItemIdentification", namespace = Ns.CAC) public SellersItemIdentification sellersItemIdentification;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SellersItemIdentification {
        @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Price {
        @XmlElement(name = "PriceAmount", namespace = Ns.CBC) public Monto priceAmount;
    }

    // ------------------------------------------------------------------ notas de credito/debito

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"referenceId", "responseCode", "description"})
    public static class DiscrepancyResponse {
        @XmlElement(name = "ReferenceID", namespace = Ns.CBC) public String referenceId;
        @XmlElement(name = "ResponseCode", namespace = Ns.CBC) public Codigo responseCode;
        @XmlElement(name = "Description", namespace = Ns.CBC) public List<String> description = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BillingReference {
        @XmlElement(name = "InvoiceDocumentReference", namespace = Ns.CAC) public InvoiceDocumentReference invoiceDocumentReference;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"id", "documentTypeCode"})
    public static class InvoiceDocumentReference {
        @XmlElement(name = "ID", namespace = Ns.CBC) public String id;
        @XmlElement(name = "DocumentTypeCode", namespace = Ns.CBC) public Codigo documentTypeCode;
    }
}
