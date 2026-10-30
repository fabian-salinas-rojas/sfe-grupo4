package pe.sfe.core.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;

/** RF-06: cadena del QR del comprobante (campos separados por "|") y su imagen PNG. */
public final class QrSunat {

    private QrSunat() {
    }

    /** RUC | tipo | serie | correlativo | IGV | total | fecha (AAAA-MM-DD) | tipo doc. cliente | nro. doc. cliente | */
    public static String cadena(String ruc, String tipo, String serie, long correlativo, String igv, String total,
                                String fecha, String tipoDocCliente, String numDocCliente) {
        return String.join("|", ruc, tipo, serie, Long.toString(correlativo), igv, total, fecha,
                tipoDocCliente, numDocCliente) + "|";
    }

    public static byte[] png(String contenido, int lado) {
        try {
            BitMatrix m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, lado, lado,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < m.getWidth(); x++) {
                for (int y = 0; y < m.getHeight(); y++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
