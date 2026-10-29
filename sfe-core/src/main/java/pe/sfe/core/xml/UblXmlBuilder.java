package pe.sfe.core.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import org.w3c.dom.Document;
import pe.sfe.core.domain.Adquirente;
import pe.sfe.core.domain.CalculadoraTotales;
import pe.sfe.core.domain.DocumentoVenta;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.LineaCalculada;
import pe.sfe.core.domain.Totales;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.sign.XmlSigner;
import pe.sfe.core.text.MontoEnLetras;
import pe.sfe.core.text.TextSanitizer;
import pe.sfe.core.xml.ubl.Ubl;
import pe.sfe.core.xml.ubl.UblDocumentBase;
import pe.sfe.core.xml.ubl.creditnote.CreditNoteDoc;
import pe.sfe.core.xml.ubl.debitnote.DebitNoteDoc;
import pe.sfe.core.xml.ubl.invoice.InvoiceDoc;

/**
 * DocumentoVenta -> DOM UBL 2.1 SIN FIRMAR, exclusivamente con JAXB (cero concatenacion de strings).
 * Todo texto libre pasa por {@link TextSanitizer#latin1} antes de entrar al modelo.
 * El nodo ext:ExtensionContent queda vacio: ahi inserta la firma {@link XmlSigner}.
 */
public final class UblXmlBuilder {

    private static final String CAT01 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01";
    private static final String CAT05 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo05";
    private static final String CAT06 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06";
    private static final String CAT07 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07";
    private static final String CAT09 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo09";
    private static final String CAT10 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo10";
    private static final String CAT16 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<TipoComprobante, JAXBContext> contextos = new EnumMap<>(TipoComprobante.class);

    public UblXmlBuilder() {
        try {
            JAXBContext invoice = JAXBContext.newInstance(InvoiceDoc.class);
            contextos.put(TipoComprobante.FACTURA, invoice);
            contextos.put(TipoComprobante.BOLETA, invoice);
            contextos.put(TipoComprobante.NOTA_CREDITO, JAXBContext.newInstance(CreditNoteDoc.class));
            contextos.put(TipoComprobante.NOTA_DEBITO, JAXBContext.newInstance(DebitNoteDoc.class));
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo crear el JAXBContext de UBL", e);
        }
    }

    public Document build(DocumentoVenta d, Totales t) {
        UblDocumentBase raiz = switch (d.tipo()) {
            case FACTURA, BOLETA -> invoice(d, t);
            case NOTA_CREDITO -> creditNote(d, t);
            case NOTA_DEBITO -> debitNote(d, t);
        };
        try {
            Document dom = SecureXml.newDocument();
            Marshaller m = contextos.get(d.tipo()).createMarshaller();
            m.marshal(raiz, dom);
            return dom;
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo generar el XML UBL", e);
        }
    }

    // ------------------------------------------------------------------ raices

    private InvoiceDoc invoice(DocumentoVenta d, Totales t) {
        InvoiceDoc doc = new InvoiceDoc();
        cabecera(doc, d, t);
        doc.invoiceTypeCode = Ubl.Codigo.of(d.tipo().codigo(), "Tipo de Documento", CAT01);
        doc.invoiceTypeCode.listID = "0101"; // Catalogo 51: venta interna
        if (d.tipo() == TipoComprobante.FACTURA) {
            Ubl.PaymentTerms pt = new Ubl.PaymentTerms();
            pt.id = "FormaPago";
            pt.paymentMeansId = "Contado";
            doc.paymentTerms.add(pt);
        }
        doc.legalMonetaryTotal = totalMonetario(d, t);
        for (LineaCalculada l : t.lineas()) {
            Ubl.Linea linea = linea(d, l);
            linea.invoicedQuantity = cantidad(l);
            doc.lines.add(linea);
        }
        return doc;
    }

    private CreditNoteDoc creditNote(DocumentoVenta d, Totales t) {
        CreditNoteDoc doc = new CreditNoteDoc();
        cabecera(doc, d, t);
        referenciaNota(doc, d, CAT09, "Tipo de nota de credito");
        doc.legalMonetaryTotal = totalMonetario(d, t);
        for (LineaCalculada l : t.lineas()) {
            Ubl.Linea linea = linea(d, l);
            linea.creditedQuantity = cantidad(l);
            doc.lines.add(linea);
        }
        return doc;
    }

    private DebitNoteDoc debitNote(DocumentoVenta d, Totales t) {
        DebitNoteDoc doc = new DebitNoteDoc();
        cabecera(doc, d, t);
        referenciaNota(doc, d, CAT10, "Tipo de nota de debito");
        doc.requestedMonetaryTotal = totalMonetario(d, t);
        for (LineaCalculada l : t.lineas()) {
            Ubl.Linea linea = linea(d, l);
            linea.debitedQuantity = cantidad(l);
            doc.lines.add(linea);
        }
        return doc;
    }

    // ------------------------------------------------------------------ bloques comunes

    private void cabecera(UblDocumentBase b, DocumentoVenta d, Totales t) {
        b.ublExtensions = new Ubl.UblExtensions();
        b.ublExtensions.extensions.add(new Ubl.UblExtension());
        b.ublVersionId = "2.1";
        b.customizationId = "2.0";
        b.id = d.numero();
        b.issueDate = d.fechaEmision().toString();
        b.issueTime = d.horaEmision().format(HORA);
        b.notes.add(Ubl.NotaTexto.of(MontoEnLetras.convertir(t.importeTotal(), d.moneda()), "1000"));
        b.documentCurrencyCode = d.moneda();
        b.signature = firma(d.emisor());
        b.accountingSupplierParty = proveedor(d.emisor());
        b.accountingCustomerParty = cliente(d.adquirente());
        b.taxTotal.add(taxTotal(d, t));
    }

    private void referenciaNota(UblDocumentBase b, DocumentoVenta d, String listUri, String listName) {
        Ubl.DiscrepancyResponse dr = new Ubl.DiscrepancyResponse();
        dr.referenceId = d.referencia().numero();
        dr.responseCode = Ubl.Codigo.of(d.motivo().codigo(), listName, listUri);
        dr.description.add(TextSanitizer.latin1(d.motivo().descripcion()));
        b.discrepancyResponse.add(dr);

        Ubl.InvoiceDocumentReference ref = new Ubl.InvoiceDocumentReference();
        ref.id = d.referencia().numero();
        ref.documentTypeCode = Ubl.Codigo.of(d.referencia().tipo().codigo(), "Tipo de Documento", CAT01);
        Ubl.BillingReference br = new Ubl.BillingReference();
        br.invoiceDocumentReference = ref;
        b.billingReference.add(br);
    }

    private Ubl.Firma firma(Emisor e) {
        Ubl.Firma f = new Ubl.Firma();
        f.id = XmlSigner.UBL_SIGNATURE_ID;
        Ubl.PartyIdentification pi = new Ubl.PartyIdentification();
        pi.id = Ubl.IdEsquema.of(e.ruc(), null, null, null, null);
        Ubl.PartyName pn = new Ubl.PartyName();
        pn.name = TextSanitizer.latin1(e.razonSocial());
        f.signatoryParty = new Ubl.Firmante();
        f.signatoryParty.partyIdentification = pi;
        f.signatoryParty.partyName = pn;
        Ubl.ExternalReference er = new Ubl.ExternalReference();
        er.uri = "#" + XmlSigner.SIGNATURE_ID;
        f.digitalSignatureAttachment = new Ubl.FirmaAdjunta();
        f.digitalSignatureAttachment.externalReference = er;
        return f;
    }

    private Ubl.ParteContable proveedor(Emisor e) {
        Ubl.Party p = new Ubl.Party();
        Ubl.PartyIdentification pi = new Ubl.PartyIdentification();
        pi.id = idDocumento(e.ruc(), "6");
        p.partyIdentification.add(pi);
        Ubl.PartyName pn = new Ubl.PartyName();
        String comercial = e.nombreComercial() == null || e.nombreComercial().isBlank() ? e.razonSocial() : e.nombreComercial();
        pn.name = TextSanitizer.latin1(comercial);
        p.partyName.add(pn);

        Ubl.Direccion dir = new Ubl.Direccion();
        dir.id = Ubl.IdEsquema.of(e.ubigeo(), null, "Ubigeos", "PE:INEI", null);
        dir.addressTypeCode = "0000"; // codigo de establecimiento anexo: 0000 = domicilio fiscal
        dir.cityName = TextSanitizer.latin1(e.provincia());
        dir.countrySubentity = TextSanitizer.latin1(e.departamento());
        dir.district = TextSanitizer.latin1(e.distrito());
        dir.addressLine.add(linea(e.direccion()));
        dir.country = pais();
        Ubl.PartyLegalEntity ple = new Ubl.PartyLegalEntity();
        ple.registrationName = TextSanitizer.latin1(e.razonSocial());
        ple.registrationAddress = dir;
        p.partyLegalEntity.add(ple);

        Ubl.ParteContable pc = new Ubl.ParteContable();
        pc.party = p;
        return pc;
    }

    private Ubl.ParteContable cliente(Adquirente a) {
        Ubl.Party p = new Ubl.Party();
        Ubl.PartyIdentification pi = new Ubl.PartyIdentification();
        pi.id = idDocumento(a.numeroDocumento(), a.tipoDocumento());
        p.partyIdentification.add(pi);
        Ubl.PartyLegalEntity ple = new Ubl.PartyLegalEntity();
        ple.registrationName = TextSanitizer.latin1(a.denominacion());
        if (a.direccion() != null && !a.direccion().isBlank()) {
            Ubl.Direccion dir = new Ubl.Direccion();
            dir.addressLine.add(linea(a.direccion()));
            dir.country = pais();
            ple.registrationAddress = dir;
        }
        p.partyLegalEntity.add(ple);
        Ubl.ParteContable pc = new Ubl.ParteContable();
        pc.party = p;
        return pc;
    }

    private static Ubl.IdEsquema idDocumento(String numero, String tipo) {
        return Ubl.IdEsquema.of(TextSanitizer.latin1(numero), tipo, "Documento de Identidad", "PE:SUNAT", CAT06);
    }

    private static Ubl.AddressLine linea(String texto) {
        Ubl.AddressLine l = new Ubl.AddressLine();
        l.line = TextSanitizer.latin1(texto);
        return l;
    }

    private static Ubl.Country pais() {
        Ubl.Country c = new Ubl.Country();
        c.identificationCode = "PE";
        return c;
    }

    // ------------------------------------------------------------------ tributos y totales

    private Ubl.TaxTotal taxTotal(DocumentoVenta d, Totales t) {
        String m = d.moneda();
        Ubl.TaxTotal tt = new Ubl.TaxTotal();
        tt.taxAmount = Ubl.Monto.of(fmt(t.igv()), m);
        boolean alguno = false;
        if (t.gravadas().signum() > 0) {
            tt.taxSubtotal.add(subtotal(m, t.gravadas(), t.igv(), "S", null, null, "1000", "IGV", "VAT"));
            alguno = true;
        }
        if (t.exoneradas().signum() > 0) {
            tt.taxSubtotal.add(subtotal(m, t.exoneradas(), BigDecimal.ZERO, "E", null, null, "9997", "EXO", "VAT"));
            alguno = true;
        }
        if (t.inafectas().signum() > 0) {
            tt.taxSubtotal.add(subtotal(m, t.inafectas(), BigDecimal.ZERO, "O", null, null, "9998", "INA", "FRE"));
            alguno = true;
        }
        if (t.exportacion().signum() > 0) {
            tt.taxSubtotal.add(subtotal(m, t.exportacion(), BigDecimal.ZERO, "G", null, null, "9995", "EXP", "FRE"));
            alguno = true;
        }
        if (!alguno) {
            tt.taxSubtotal.add(subtotal(m, BigDecimal.ZERO, BigDecimal.ZERO, "S", null, null, "1000", "IGV", "VAT"));
        }
        return tt;
    }

    private static Ubl.TaxSubtotal subtotal(String moneda, BigDecimal base, BigDecimal impuesto, String categoria,
                                            String porcentaje, String afectacion, String codTributo, String nombre,
                                            String tipoTributo) {
        Ubl.TaxSubtotal s = new Ubl.TaxSubtotal();
        s.taxableAmount = Ubl.Monto.of(fmt(base), moneda);
        s.taxAmount = Ubl.Monto.of(fmt(impuesto), moneda);
        Ubl.TaxCategory c = new Ubl.TaxCategory();
        c.id = Ubl.IdEsquema.of(categoria, "UN/ECE 5305", "Tax Category Identifier",
                "United Nations Economic Commission for Europe", null);
        c.percent = porcentaje;
        if (afectacion != null) {
            c.taxExemptionReasonCode = Ubl.Codigo.of(afectacion, "Afectacion del IGV", CAT07);
        }
        Ubl.TaxScheme ts = new Ubl.TaxScheme();
        ts.id = Ubl.IdEsquema.of(codTributo, "UN/ECE 5153", "Codigo de tributos", "PE:SUNAT", CAT05);
        ts.name = nombre;
        ts.taxTypeCode = tipoTributo;
        c.taxScheme = ts;
        s.taxCategory = c;
        return s;
    }

    private static Ubl.TotalMonetario totalMonetario(DocumentoVenta d, Totales t) {
        Ubl.TotalMonetario tm = new Ubl.TotalMonetario();
        tm.lineExtensionAmount = Ubl.Monto.of(fmt(t.valorVenta()), d.moneda());
        tm.taxInclusiveAmount = Ubl.Monto.of(fmt(t.importeTotal()), d.moneda());
        tm.payableAmount = Ubl.Monto.of(fmt(t.importeTotal()), d.moneda());
        return tm;
    }

    // ------------------------------------------------------------------ lineas

    private Ubl.Linea linea(DocumentoVenta d, LineaCalculada l) {
        String m = d.moneda();
        var it = l.item();
        Ubl.Linea x = new Ubl.Linea();
        x.id = String.valueOf(l.numero());
        x.lineExtensionAmount = Ubl.Monto.of(fmt(l.valorVenta()), m);

        Ubl.AlternativeConditionPrice acp = new Ubl.AlternativeConditionPrice();
        acp.priceAmount = Ubl.Monto.of(fmt(l.precioUnitarioConIgv()), m);
        acp.priceTypeCode = Ubl.Codigo.of("01", "Tipo de Precio", CAT16);
        x.pricingReference = new Ubl.PricingReference();
        x.pricingReference.alternativeConditionPrice.add(acp);

        boolean gravado = "10".equals(it.afectacionIgv());
        Ubl.TaxTotal tt = new Ubl.TaxTotal();
        tt.taxAmount = Ubl.Monto.of(fmt(l.igv()), m);
        String cat = switch (it.afectacionIgv()) { case "10" -> "S"; case "20" -> "E"; case "30" -> "O"; default -> "G"; };
        String cod = switch (it.afectacionIgv()) { case "10" -> "1000"; case "20" -> "9997"; case "30" -> "9998"; default -> "9995"; };
        String nom = switch (it.afectacionIgv()) { case "10" -> "IGV"; case "20" -> "EXO"; case "30" -> "INA"; default -> "EXP"; };
        String tip = switch (it.afectacionIgv()) { case "10", "20" -> "VAT"; default -> "FRE"; };
        tt.taxSubtotal.add(subtotal(m, l.valorVenta(), l.igv(), cat, gravado ? "18.00" : "0.00", it.afectacionIgv(), cod, nom, tip));
        x.taxTotal = tt;

        x.item = new Ubl.Item();
        x.item.description.add(TextSanitizer.latin1(it.descripcion()));
        if (it.codigo() != null && !it.codigo().isBlank()) {
            x.item.sellersItemIdentification = new Ubl.SellersItemIdentification();
            x.item.sellersItemIdentification.id = TextSanitizer.latin1(it.codigo());
        }
        x.price = new Ubl.Price();
        x.price.priceAmount = Ubl.Monto.of(fmtPrecio(it.valorUnitario()), m);
        return x;
    }

    private static Ubl.Cantidad cantidad(LineaCalculada l) {
        return Ubl.Cantidad.of(fmtCantidad(l.item().cantidad()), l.item().unidadMedida());
    }

    // ------------------------------------------------------------------ formato numerico

    static String fmt(BigDecimal v) {
        return CalculadoraTotales.money(v).toPlainString();
    }

    /** Precio unitario: minimo 2 y maximo 10 decimales, sin ceros de relleno. */
    static String fmtPrecio(BigDecimal v) {
        BigDecimal r = v.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        return (r.scale() < 2 ? r.setScale(2) : r).toPlainString();
    }

    static String fmtCantidad(BigDecimal v) {
        return v.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
