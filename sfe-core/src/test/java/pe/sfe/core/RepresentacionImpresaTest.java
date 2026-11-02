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

    private static final Emisor EMISOR = new Emisor("20608945123", "DISTRIBUIDORA & LOGISTICA COMERCIAL DEL SUR S.A.C.", "LOGISTICA DEL SUR",
            "040101", "AV. VENEZUELA 1500", "AREQUIPA", "AREQUIPA", "AREQUIPA");

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
              <cbc:Note languageLocaleID="1000">ONCE CON 80/100 SOLES</cbc:Note>
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
        assertTrue(texto.contains("10.00"), "el subtotal de la nota de debito no salio en el PDF: " + texto);
        assertTrue(texto.contains("SON: ONCE CON 80/100 SOLES"), "falta el monto en letras en el PDF: " + texto);
        assertTrue(texto.contains("10.00"), "el subtotal de la nota de debito no salio en el PDF: " + texto);
    }

    // ------------------------------------------------------------ RF-06: QR nivel Q, emisor del XML, datos de la nota

    private static String xmlConProveedor(String raiz, String extra, String lineas) {
        return """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <%1$s xmlns="urn:oasis:names:specification:ubl:schema:xsd:%1$s-2"
                      xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                      xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:IssueDate>2026-10-29</cbc:IssueDate>
                  <cbc:DocumentCurrencyCode>PEN</cbc:DocumentCurrencyCode>
                  %2$s
                  <cac:AccountingSupplierParty><cac:Party>
                    <cac:PartyIdentification><cbc:ID schemeID="6">20608945123</cbc:ID></cac:PartyIdentification>
                    <cac:PartyName><cbc:Name>LOGISTICA DEL SUR</cbc:Name></cac:PartyName>
                    <cac:PartyLegalEntity><cbc:RegistrationName>DISTRIBUIDORA &amp; LOGISTICA COMERCIAL DEL SUR S.A.C.</cbc:RegistrationName>
                      <cac:RegistrationAddress><cbc:CityName>AREQUIPA</cbc:CityName><cbc:CountrySubentity>AREQUIPA</cbc:CountrySubentity>
                        <cbc:District>CAYMA</cbc:District><cac:AddressLine><cbc:Line>AV. EJERCITO 700</cbc:Line></cac:AddressLine>
                      </cac:RegistrationAddress></cac:PartyLegalEntity>
                  </cac:Party></cac:AccountingSupplierParty>
                  <cac:AccountingCustomerParty><cac:Party>
                    <cac:PartyIdentification><cbc:ID schemeID="6">20512345678</cbc:ID></cac:PartyIdentification>
                    <cac:PartyLegalEntity><cbc:RegistrationName>CLIENTE EJEMPLO SAC</cbc:RegistrationName></cac:PartyLegalEntity>
                  </cac:Party></cac:AccountingCustomerParty>
                  <cac:TaxTotal><cbc:TaxAmount currencyID="PEN">18.00</cbc:TaxAmount></cac:TaxTotal>
                  <cac:LegalMonetaryTotal>
                    <cbc:LineExtensionAmount currencyID="PEN">100.00</cbc:LineExtensionAmount>
                    <cbc:PayableAmount currencyID="PEN">118.00</cbc:PayableAmount>
                  </cac:LegalMonetaryTotal>
                  %3$s
                </%1$s>
                """.formatted(raiz, extra, lineas);
    }

    private static final String LINEA_FACTURA = """
            <cac:InvoiceLine><cbc:ID>1</cbc:ID><cbc:InvoicedQuantity unitCode="NIU">2</cbc:InvoicedQuantity>
              <cbc:LineExtensionAmount currencyID="PEN">100.00</cbc:LineExtensionAmount>
              <cac:Item><cbc:Description>Cuaderno A4</cbc:Description>
                <cac:SellersItemIdentification><cbc:ID>P001</cbc:ID></cac:SellersItemIdentification></cac:Item>
              <cac:Price><cbc:PriceAmount currencyID="PEN">50.00</cbc:PriceAmount></cac:Price></cac:InvoiceLine>""";

    private static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        String t = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        return t;
    }

    @Test
    void qr_usa_correccion_de_errores_nivel_Q() throws Exception {
        byte[] png = QrSunat.png("20608945123|01|F001|18|18.00|118.00|2026-10-29|6|20512345678|", 220);
        var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.RGBLuminanceSource(img.getWidth(), img.getHeight(), px)));
        var r = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap);
        assertEquals("20608945123|01|F001|18|18.00|118.00|2026-10-29|6|20512345678|", r.getText());
        assertEquals("Q", String.valueOf(r.getResultMetadata().get(com.google.zxing.ResultMetadataType.ERROR_CORRECTION_LEVEL)));
    }

    @Test
    void el_emisor_impreso_sale_del_xml_firmado_y_no_de_la_bd() throws Exception {
        Emisor distinto = new Emisor("20999999999", "RAZON DE BD S.A.", "NOMBRE BD", "040101", "CALLE BD 1", "LIMA", "LIMA", "LIMA");
        byte[] xml = xmlConProveedor("Invoice", "", LINEA_FACTURA).getBytes(StandardCharsets.ISO_8859_1);
        String t = texto(RepresentacionImpresa.generar(distinto, TipoComprobante.deCodigo("01"), "F001", 18, xml));
        assertTrue(t.contains("R.U.C. 20608945123"), t);
        assertTrue(t.contains("AV. EJERCITO 700"), t);
        assertTrue(!t.contains("20999999999") && !t.contains("RAZON DE BD"), "se imprimieron datos de la BD: " + t);
    }

    @Test
    void nota_de_credito_imprime_documento_que_modifica_y_motivo() throws Exception {
        String ref = """
                <cac:DiscrepancyResponse><cbc:ReferenceID>F001-3</cbc:ReferenceID><cbc:ResponseCode>01</cbc:ResponseCode>
                  <cbc:Description>Anulacion de la operacion</cbc:Description></cac:DiscrepancyResponse>
                <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>F001-3</cbc:ID>
                  <cbc:DocumentTypeCode>01</cbc:DocumentTypeCode></cac:InvoiceDocumentReference></cac:BillingReference>""";
        byte[] xml = xmlConProveedor("CreditNote", ref, LINEA_FACTURA.replace("Invoice", "CreditNote")
                .replace("InvoicedQuantity", "CreditedQuantity")).getBytes(StandardCharsets.ISO_8859_1);
        String t = texto(RepresentacionImpresa.generar(EMISOR, TipoComprobante.deCodigo("07"), "FC01", 2, xml));
        assertTrue(t.contains("Documento que modifica") && t.contains("Factura electr\u00f3nica F001-3"), t);
        assertTrue(t.contains("Motivo") && t.contains("01 - Anulacion de la operacion"), t);
    }

    @Test
    void una_factura_no_imprime_el_bloque_de_nota() throws Exception {
        byte[] xml = xmlConProveedor("Invoice", "", LINEA_FACTURA).getBytes(StandardCharsets.ISO_8859_1);
        String t = texto(RepresentacionImpresa.generar(EMISOR, TipoComprobante.deCodigo("01"), "F001", 18, xml));
        assertTrue(!t.contains("Documento que modifica"), t);
    }

    @Test
    void el_aviso_de_estado_se_imprime_cuando_se_pide() throws Exception {
        byte[] xml = xmlConProveedor("Invoice", "", LINEA_FACTURA).getBytes(StandardCharsets.ISO_8859_1);
        String t = texto(RepresentacionImpresa.generar(EMISOR, TipoComprobante.deCodigo("01"), "F001", 18, xml,
                "ESTADO ANTE SUNAT: PENDIENTE DE CONFIRMACION"));
        assertTrue(t.contains("PENDIENTE DE CONFIRMACION"), t);
    }
}
