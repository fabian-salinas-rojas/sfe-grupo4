package pe.sfe.core.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import pe.sfe.core.domain.Emisor;
import pe.sfe.core.domain.TipoComprobante;

/**
 * RF-06: representacion impresa (PDF A4 con QR) generada A PARTIR DEL XML FIRMADO que ya esta guardado.
 * Asi el PDF nunca difiere de lo que se envio a SUNAT. Si un dato falta en el XML, se imprime vacio (no falla).
 */
public final class RepresentacionImpresa {

    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";

    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA, 13, Font.BOLD);
    private static final Font CAJA = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.BOLD);
    private static final Font PEQUENO = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);

    private record Linea(String codigo, String descripcion, String unidad, String cantidad, String vUnit, String importe) {
    }

    private RepresentacionImpresa() {
    }

    public static byte[] generar(Emisor emisor, TipoComprobante tipo, String serie, long correlativo, byte[] xmlFirmado) {
        return generar(emisor, tipo, serie, correlativo, xmlFirmado, null);
    }

    /**
     * @param emisorDeRespaldo datos del emisor en BD: solo se usan para lo que el XML firmado no trae. Lo impreso y el QR
     *                         salen del XML firmado, que es lo que SUNAT recibio.
     * @param aviso            texto opcional que se imprime bajo la cabecera (por ejemplo, comprobante aun sin CDR); null = nada.
     */
    public static byte[] generar(Emisor emisorDeRespaldo, TipoComprobante tipo, String serie, long correlativo,
                                 byte[] xmlFirmado, String aviso) {
        org.w3c.dom.Document xml = parsear(xmlFirmado);
        Element raiz = xml.getDocumentElement();
        Emisor emisor = emisorDelXml(raiz, emisorDeRespaldo);

        String numero = serie + "-" + correlativo;
        String fecha = txt(raiz, "IssueDate");
        String moneda = txt(raiz, "DocumentCurrencyCode");

        Element cliId = hijo(desc(raiz, "AccountingCustomerParty/Party/PartyIdentification"), CBC, "ID");
        String cliTipoDoc = cliId == null ? "" : cliId.getAttribute("schemeID");
        String cliNumDoc = cliId == null ? "" : cliId.getTextContent().trim();
        String cliNombre = txt(desc(raiz, "AccountingCustomerParty/Party/PartyLegalEntity"), "RegistrationName");

        String igv = dinero(txt(desc(raiz, "TaxTotal"), "TaxAmount"));
        Element mon = desc(raiz, "LegalMonetaryTotal");
        if (mon == null) {
            mon = desc(raiz, "RequestedMonetaryTotal"); // UBL 2.1: la nota de debito usa este nodo en vez de LegalMonetaryTotal
        }
        String subtotal = dinero(txt(mon, "LineExtensionAmount"));
        String total = dinero(txt(mon, "PayableAmount"));

        List<Linea> lineas = new ArrayList<>();
        for (Node n = raiz.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element l && CAC.equals(l.getNamespaceURI()) && l.getLocalName().endsWith("Line")) {
                Element cant = null;
                for (Node c = l.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c instanceof Element e && CBC.equals(e.getNamespaceURI()) && e.getLocalName().endsWith("Quantity")) {
                        cant = e;
                        break;
                    }
                }
                Element item = desc(l, "Item");
                lineas.add(new Linea(
                        txt(desc(l, "Item/SellersItemIdentification"), "ID"),
                        txt(item, "Description"),
                        cant == null ? "" : cant.getAttribute("unitCode"),
                        cant == null ? "" : cantidad(cant.getTextContent()),
                        precio(txt(desc(l, "Price"), "PriceAmount")),
                        dinero(txt(l, "LineExtensionAmount"))));
            }
        }

        // Leyenda 1000 (monto en letras): en UBL 2.1 va como <cbc:Note languageLocaleID="1000">
        String letras = "";
        NodeList notas = xml.getElementsByTagNameNS(CBC, "Note");
        for (int i = 0; i < notas.getLength(); i++) {
            Element n = (Element) notas.item(i);
            if ("1000".equals(n.getAttribute("languageLocaleID"))) {
                letras = n.getTextContent().trim();
                break;
            }
        }
        // Notas de credito/debito: documento que modifican y motivo (UBL: BillingReference y DiscrepancyResponse)
        String docModificado = "";
        String motivo = "";
        if ("07".equals(tipo.codigo()) || "08".equals(tipo.codigo())) {
            Element ref = desc(raiz, "BillingReference/InvoiceDocumentReference");
            String refNum = txt(ref, "ID");
            String refTipo = txt(ref, "DocumentTypeCode");
            if (!refNum.isBlank()) {
                docModificado = (refTipo.isBlank() ? "Documento" : nombreTipoDoc(refTipo)) + " " + refNum;
            }
            Element dr = desc(raiz, "DiscrepancyResponse");
            String codMotivo = txt(dr, "ResponseCode");
            String desMotivo = txt(dr, "Description");
            motivo = (codMotivo.isBlank() ? "" : codMotivo + " - ") + desMotivo;
            if (docModificado.isBlank()) {
                docModificado = txt(dr, "ReferenceID");
            }
        }
        NodeList dv = xml.getElementsByTagNameNS(DS, "DigestValue");
        String hash = dv.getLength() > 0 ? dv.item(0).getTextContent().trim() : "";

        String qr = QrSunat.cadena(emisor.ruc().trim(), tipo.codigo(), serie, correlativo, igv, total, fecha,
                cliTipoDoc, cliNumDoc);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Cabecera: emisor a la izquierda, caja con RUC / tipo / numero a la derecha
            PdfPTable cab = new PdfPTable(new float[] {60, 40});
            cab.setWidthPercentage(100);
            PdfPCell izq = sinBorde();
            izq.addElement(new Paragraph(emisor.razonSocial(), TITULO));
            if (emisor.nombreComercial() != null && !emisor.nombreComercial().isBlank()) {
                izq.addElement(new Paragraph(emisor.nombreComercial(), NORMAL));
            }
            izq.addElement(new Paragraph(direccionCompleta(emisor), NORMAL));
            cab.addCell(izq);
            PdfPCell der = new PdfPCell();
            der.setBorder(Rectangle.BOX);
            der.setPadding(8);
            der.addElement(centrado("R.U.C. " + emisor.ruc().trim(), CAJA));
            der.addElement(centrado(titulo(tipo.codigo()), CAJA));
            der.addElement(centrado(numero, CAJA));
            cab.addCell(der);
            doc.add(cab);

            // Cliente
            PdfPTable cli = new PdfPTable(new float[] {27, 73});
            cli.setWidthPercentage(100);
            cli.setSpacingBefore(12);
            fila(cli, "Cliente:", cliNombre);
            fila(cli, nombreDoc(cliTipoDoc) + ":", cliNumDoc);
            fila(cli, "Fecha de emisi\u00f3n:", fecha);
            fila(cli, "Moneda:", moneda);
            if (!docModificado.isBlank()) {
                fila(cli, "Documento que modifica:", docModificado);
                fila(cli, "Motivo:", motivo);
            }
            doc.add(cli);
            if (aviso != null && !aviso.isBlank()) {
                Paragraph av = new Paragraph(aviso, NEGRITA);
                av.setSpacingBefore(8);
                doc.add(av);
            }

            // Items
            PdfPTable it = new PdfPTable(new float[] {12, 40, 8, 10, 15, 15});
            it.setWidthPercentage(100);
            it.setSpacingBefore(12);
            for (String h : new String[] {"C\u00f3digo", "Descripci\u00f3n", "Unid.", "Cant.", "V. unit.", "Importe"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, NEGRITA));
                c.setBackgroundColor(new Color(225, 225, 225));
                c.setPadding(4);
                it.addCell(c);
            }
            for (Linea l : lineas) {
                it.addCell(celda(l.codigo(), PdfPCell.ALIGN_LEFT));
                it.addCell(celda(l.descripcion(), PdfPCell.ALIGN_LEFT));
                it.addCell(celda(l.unidad(), PdfPCell.ALIGN_CENTER));
                it.addCell(celda(l.cantidad(), PdfPCell.ALIGN_RIGHT));
                it.addCell(celda(l.vUnit(), PdfPCell.ALIGN_RIGHT));
                it.addCell(celda(l.importe(), PdfPCell.ALIGN_RIGHT));
            }
            doc.add(it);

            // Totales
            PdfPTable tot = new PdfPTable(new float[] {60, 40});
            tot.setWidthPercentage(45);
            tot.setHorizontalAlignment(PdfPTable.ALIGN_RIGHT);
            tot.setSpacingBefore(8);
            totalFila(tot, "Subtotal (sin IGV)", subtotal, NORMAL);
            totalFila(tot, "IGV", igv, NORMAL);
            totalFila(tot, "Importe total (" + moneda + ")", total, NEGRITA);
            doc.add(tot);

            if (!letras.isBlank()) {
                Paragraph son = new Paragraph("SON: " + letras, NEGRITA);
                son.setSpacingBefore(10);
                doc.add(son);
            }

            // Pie: QR + leyenda
            PdfPTable pie = new PdfPTable(new float[] {22, 78});
            pie.setWidthPercentage(100);
            pie.setSpacingBefore(16);
            Image img = Image.getInstance(QrSunat.png(qr, 220));
            img.scaleToFit(100, 100);
            PdfPCell cq = new PdfPCell(img, false);
            cq.setBorder(Rectangle.NO_BORDER);
            pie.addCell(cq);
            PdfPCell ct = sinBorde();
            ct.addElement(new Paragraph("Representaci\u00f3n impresa del comprobante electr\u00f3nico.", PEQUENO));
            ct.addElement(new Paragraph("Puede consultar su validez en el portal de SUNAT.", PEQUENO));
            if (!hash.isBlank()) {
                ct.addElement(new Paragraph("Resumen (firma): " + hash, PEQUENO));
            }
            pie.addCell(ct);
            doc.add(pie);

            doc.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ XML

    private static org.w3c.dom.Document parsear(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("No se pudo leer el XML firmado", e);
        }
    }

    private static Element hijo(Element padre, String ns, String local) {
        if (padre == null) {
            return null;
        }
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && local.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    /** Baja por hijos cac:, separados por "/". Devuelve null si algun tramo no existe. */
    private static Element desc(Element base, String cadena) {
        Element e = base;
        for (String parte : cadena.split("/")) {
            e = hijo(e, CAC, parte);
            if (e == null) {
                return null;
            }
        }
        return e;
    }

    private static String txt(Element padre, String cbc) {
        Element e = hijo(padre, CBC, cbc);
        return e == null ? "" : e.getTextContent().trim();
    }

    // ------------------------------------------------------------------ emisor

    /**
     * Emisor tal como quedo en el XML firmado (cac:AccountingSupplierParty). Lo que el XML no trae se toma del respaldo,
     * de modo que el PDF y el QR nunca difieran de lo que SUNAT recibio aunque la ficha del emisor cambie despues.
     */
    private static Emisor emisorDelXml(Element raiz, Emisor respaldo) {
        Element party = desc(raiz, "AccountingSupplierParty/Party");
        if (party == null) {
            return respaldo;
        }
        String ruc = txt(desc(party, "PartyIdentification"), "ID");
        Element ple = desc(party, "PartyLegalEntity");
        String razon = txt(ple, "RegistrationName");
        String comercial = txt(desc(party, "PartyName"), "Name");
        if (comercial.equals(razon)) {
            comercial = ""; // el generador repite la razon social cuando no hay nombre comercial
        }
        Element dir = desc(ple, "RegistrationAddress");
        String linea = txt(desc(dir, "AddressLine"), "Line");
        return new Emisor(
                preferir(ruc, respaldo == null ? null : respaldo.ruc()),
                preferir(razon, respaldo == null ? null : respaldo.razonSocial()),
                comercial.isBlank() ? (respaldo == null ? null : respaldo.nombreComercial()) : comercial,
                respaldo == null ? null : respaldo.ubigeo(),
                preferir(linea, respaldo == null ? null : respaldo.direccion()),
                preferir(txt(dir, "CountrySubentity"), respaldo == null ? null : respaldo.departamento()),
                preferir(txt(dir, "CityName"), respaldo == null ? null : respaldo.provincia()),
                preferir(txt(dir, "District"), respaldo == null ? null : respaldo.distrito()));
    }

    private static String preferir(String delXml, String respaldo) {
        return delXml != null && !delXml.isBlank() ? delXml.trim() : (respaldo == null ? "" : respaldo.trim());
    }

    /** "Av. X - Distrito, Provincia, Departamento" omitiendo los tramos vacios. */
    private static String direccionCompleta(Emisor e) {
        StringBuilder sb = new StringBuilder(e.direccion() == null ? "" : e.direccion().trim());
        String zona = String.join(", ", java.util.stream.Stream.of(e.distrito(), e.provincia(), e.departamento())
                .filter(x -> x != null && !x.isBlank()).map(String::trim).toList());
        if (!zona.isBlank()) {
            sb.append(sb.length() > 0 ? " - " : "").append(zona);
        }
        return sb.toString();
    }

    private static String nombreTipoDoc(String codigo) {
        return switch (codigo) {
            case "01" -> "Factura electr\u00f3nica";
            case "03" -> "Boleta de venta electr\u00f3nica";
            default -> "Documento " + codigo;
        };
    }

    // ------------------------------------------------------------------ formato

    private static String dinero(String s) {
        if (s == null || s.isBlank()) {
            return "0.00";
        }
        try {
            return new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static String precio(String s) {
        try {
            BigDecimal b = new BigDecimal(s.trim()).stripTrailingZeros();
            return (b.scale() < 2 ? b.setScale(2) : b).toPlainString();
        } catch (RuntimeException e) {
            return s;
        }
    }

    private static String cantidad(String s) {
        try {
            return new BigDecimal(s.trim()).stripTrailingZeros().toPlainString();
        } catch (RuntimeException e) {
            return s;
        }
    }

    private static String titulo(String codigo) {
        return switch (codigo) {
            case "01" -> "FACTURA ELECTR\u00d3NICA";
            case "03" -> "BOLETA DE VENTA ELECTR\u00d3NICA";
            case "07" -> "NOTA DE CR\u00c9DITO ELECTR\u00d3NICA";
            case "08" -> "NOTA DE D\u00c9BITO ELECTR\u00d3NICA";
            default -> "COMPROBANTE ELECTR\u00d3NICO";
        };
    }

    private static String nombreDoc(String codigo) {
        return switch (codigo) {
            case "6" -> "RUC";
            case "1" -> "DNI";
            case "4" -> "Carnet de extranjer\u00eda";
            case "7" -> "Pasaporte";
            default -> "Documento";
        };
    }

    // ------------------------------------------------------------------ celdas

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Paragraph centrado(String s, Font f) {
        Paragraph p = new Paragraph(s, f);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        return p;
    }

    private static PdfPCell celda(String s, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(s, NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(4);
        return c;
    }

    private static void fila(PdfPTable t, String etiqueta, String valor) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, NEGRITA));
        a.setBorder(Rectangle.NO_BORDER);
        PdfPCell b = new PdfPCell(new Phrase(valor, NORMAL));
        b.setBorder(Rectangle.NO_BORDER);
        t.addCell(a);
        t.addCell(b);
    }

    private static void totalFila(PdfPTable t, String etiqueta, String valor, Font f) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, f));
        a.setBorder(Rectangle.NO_BORDER);
        PdfPCell b = new PdfPCell(new Phrase(valor, f));
        b.setBorder(Rectangle.NO_BORDER);
        b.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }
}