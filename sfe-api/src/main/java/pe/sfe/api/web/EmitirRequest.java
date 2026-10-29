package pe.sfe.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import pe.sfe.core.domain.Adquirente;
import pe.sfe.core.domain.ItemVenta;
import pe.sfe.core.domain.MotivoNota;
import pe.sfe.core.domain.ReferenciaDocumento;
import pe.sfe.core.domain.SolicitudEmision;
import pe.sfe.core.domain.TipoComprobante;

/**
 * Cuerpo de POST /api/v1/comprobantes. La caja NO envia serie, correlativo, fecha ni totales: los asigna/calcula el
 * servidor (RN-04 y numeracion sin saltos). Las reglas de negocio finas (RN-01..03) las aplica sfe-core.
 */
public record EmitirRequest(
        @NotBlank @Size(max = 64) String idVentaExterno,
        @NotBlank @Pattern(regexp = "01|03|07|08", message = "tipo debe ser 01, 03, 07 u 08") String tipo,
        @NotBlank @Pattern(regexp = "PEN|USD", message = "moneda debe ser PEN o USD") String moneda,
        @Valid AdquirenteDto adquirente,
        @NotEmpty @Size(max = 500) List<@Valid ItemDto> items,
        @Valid ReferenciaDto referencia,
        @Valid MotivoDto motivo) {

    public record AdquirenteDto(
            @NotBlank @Size(max = 2) String tipoDocumento,
            @NotBlank @Size(max = 15) String numeroDocumento,
            @Size(max = 200) String denominacion,
            @Size(max = 200) String direccion) {
    }

    public record ItemDto(
            @Size(max = 30) String codigo,
            @NotBlank @Size(max = 250) String descripcion,
            @NotBlank @Size(max = 3) String unidadMedida,
            @NotNull @Positive @Digits(integer = 10, fraction = 10) BigDecimal cantidad,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 10) BigDecimal valorUnitario,
            @NotBlank @Size(max = 2) String afectacionIgv) {
    }

    public record ReferenciaDto(
            @NotBlank @Pattern(regexp = "01|03") String tipo,
            @NotBlank @Size(max = 4) String serie,
            @Positive long correlativo) {
    }

    public record MotivoDto(@NotBlank @Size(max = 2) String codigo, @NotBlank @Size(max = 250) String descripcion) {
    }

    public SolicitudEmision aSolicitud() {
        TipoComprobante t = TipoComprobante.deCodigo(tipo);
        Adquirente a = adquirente == null
                ? Adquirente.consumidorFinal() // boleta de consumidor final; ReglasNegocio exige datos cuando corresponde
                : new Adquirente(adquirente.tipoDocumento(), adquirente.numeroDocumento(), adquirente.denominacion(),
                        adquirente.direccion());
        return new SolicitudEmision(
                idVentaExterno, t, moneda, a,
                items.stream().map(i -> new ItemVenta(i.codigo(), i.descripcion(), i.unidadMedida(), i.cantidad(),
                        i.valorUnitario(), i.afectacionIgv())).toList(),
                referencia == null ? null
                        : new ReferenciaDocumento(TipoComprobante.deCodigo(referencia.tipo()), referencia.serie(),
                                referencia.correlativo()),
                motivo == null ? null : new MotivoNota(motivo.codigo(), motivo.descripcion()));
    }
}
