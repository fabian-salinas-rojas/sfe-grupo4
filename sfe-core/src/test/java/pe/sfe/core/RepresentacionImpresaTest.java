package pe.sfe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.TipoComprobante;
import pe.sfe.core.pdf.QrSunat;
import pe.sfe.core.pdf.RepresentacionImpresa;

/** RF-06: QR con el formato de SUNAT y PDF generado desde un XML UBL. */
class RepresentacionImpresaTest {

    private static final Emisor EMISOR = new Emisor("20608945123", "COMERCIAL ANDINA SAC", "ANDINA MARKET",
            "150101", "AV. AREQUIPA 1234 - OF. 5", "LIMA", "LIMA", "LIMA");

    private static final String XML = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:IssueDate>2026-10-29</cbc:IssueDate>
              <cbc:DocumentCurrencyCode>PEN</cbc:DocumentCurrencyCode>
              <cac:AccountingCustomerParty><cac:Party>
                <cac:PartyIdentification><cbc:ID schemeID="6">20512345678</cbc:ID></cac:PartyIdentification>
                <cac:PartyLegalEntity><cbc:RegistrationName>CLIENTE EJEMPLO SAC</cbc:RegistrationName></cac:PartyLegalEntity>
              </cac:Party></cac:AccountingCustomerParty>
              <cac:TaxTotal><cbc:TaxAmount currencyID="PEN">18.00</cbc:TaxAmount></cac:TaxTotal>
              <cac:LegalMonetaryTotal>
                <cbc:LineExtensionAmount currencyID="PEN">100.00</cbc:LineExtensionAmount>
                <cbc:PayableAmount currencyID="PEN">118.00</cbc:PayableAmount>
              </cac:LegalMonetaryTotal>
              <cac:InvoiceLine>
                <cbc:ID>1</cbc:ID>
                <cbc:InvoicedQuantity unitCode="NIU">2</cbc:InvoicedQuantity>
                <cbc:LineExtensionAmount currencyID="PEN">100.00</cbc:LineExtensionAmount>
                <cac:Item><cbc:Description>Cuaderno A4</cbc:Description>
                  <cac:SellersItemIdentification><cbc:ID>P001</cbc:ID></cac:SellersItemIdentification></cac:Item>
                <cac:Price><cbc:PriceAmount currencyID="PEN">50.00</cbc:PriceAmount></cac:Price>
              </cac:InvoiceLine>
            </Invoice>
            """;

    @Test
    void qr_sigue_el_formato_de_nueve_campos_con_barra_final() {
        assertEquals("20608945123|01|F001|18|18.00|118.00|2026-10-29|6|20512345678|",
                QrSunat.cadena("20608945123", "01", "F001", 18, "18.00", "118.00", "2026-10-29", "6", "20512345678"));
    }

    @Test
    void qr_se_genera_como_png_valido() {
        byte[] png = QrSunat.png("20608945123|01|F001|18|18.00|118.00|2026-10-29|6|20512345678|", 200);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void genera_un_pdf_a_partir_del_xml() {
        byte[] pdf = RepresentacionImpresa.generar(EMISOR, TipoComprobante.deCodigo("01"), "F001", 18,
                XML.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 1500, "el PDF parece vacio: " + pdf.length + " bytes");
    }

    private static final String XML_NOTA_DEBITO = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <DebitNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2"
                       xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                       xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:IssueDate>2026-10-30</cbc:IssueDate>
              <cbc:DocumentCurrencyCode>PEN</cbc:DocumentCurrencyCode>
              <cac:AccountingCustomerParty><cac:Party>
                <cac:PartyIdentification><cbc:ID schemeID="6">20512345678</cbc:ID></cac:PartyIdentification>
                <cac:PartyLegalEntity><cbc:RegistrationName>CLIENTE EJEMPLO SAC</cbc:RegistrationName></cac:PartyLegalEntity>
              </cac:Party></cac:AccountingCustomerParty>
              <cac:TaxTotal><cbc:TaxAmount currencyID="PEN">1.80</cbc:TaxAmount></cac:TaxTotal>
              <cac:RequestedMonetaryTotal>
                <cbc:LineExtensionAmount currencyID="PEN">10.00</cbc:LineExtensionAmount>
                <cbc:PayableAmount currencyID="PEN">11.80</cbc:PayableAmount>
              </cac:RequestedMonetaryTotal>
              <cac:DebitNoteLine>
                <cbc:ID>1</cbc:ID>
                <cbc:DebitedQuantity unitCode="ZZ">1</cbc:DebitedQuantity>
                <cbc:LineExtensionAmount currencyID="PEN">10.00</cbc:LineExtensionAmount>
                <cac:Item><cbc:Description>Ajuste por aumento en el valor</cbc:Description>
                  <cac:SellersItemIdentification><cbc:ID>P001</cbc:ID></cac:SellersItemIdentification></cac:Item>
                <cac:Price><cbc:PriceAmount currencyID="PEN">10.00</cbc:PriceAmount></cac:Price>
              </cac:DebitNoteLine>
            </DebitNote>
            """;

    @Test
    void nota_de_debito_toma_los_totales_de_RequestedMonetaryTotal() throws Exception {
        byte[] pdf = RepresentacionImpresa.generar(EMISOR, TipoComprobante.deCodigo("08"), "FD01", 2,
                XML_NOTA_DEBITO.getBytes(StandardCharsets.ISO_8859_1));
        PdfReader reader = new PdfReader(pdf);
        String texto = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertTrue(texto.contains("11.80"), "el total de la nota de debito no salio en el PDF: " + texto);
        assertTrue(texto.contains("10.00"), "el subtotal de la nota de debito no salio en el PDF: " + texto);
    }
}