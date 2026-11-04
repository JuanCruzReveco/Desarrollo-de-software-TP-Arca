package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Renglón de la factura. Lado "muchos" de la relación bidireccional con FacturaVenta
 * (es el dueño de la FK factura_id).
 */
@Entity
@Table(name = "factura_venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"factura"})
@EqualsAndHashCode(callSuper = true, exclude = {"factura"})
public class FacturaVentaDetalle extends EntityId {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaVenta factura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_precio_articulo_id", nullable = false)
    private ListaPrecioArticulo listaPrecioArticulo;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private double cantidad;

    @Column(nullable = false)
    private double precioUnitario;

    private double porcentajeBonificacion;
    private double importeNeto;
    private double importeIva;

    @Column(nullable = false)
    private double importeSubtotal;

    /**
     * Calcula neto, IVA y subtotal del renglón.
     *   neto     = cantidad * precioUnitario * (1 - bonificación%)
     *   iva      = neto * alícuota%
     *   subtotal = neto + iva
     */
    public void calcularImportes(double alicuotaIva) {
        BigDecimal neto = FacturaVenta.decimalFinito(cantidad).multiply(FacturaVenta.decimalFinito(precioUnitario))
                .multiply(BigDecimal.ONE.subtract(FacturaVenta.decimalFinito(porcentajeBonificacion).movePointLeft(2)));
        BigDecimal iva = neto.multiply(FacturaVenta.decimalFinito(alicuotaIva).movePointLeft(2))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netoRedondeado = neto.setScale(2, RoundingMode.HALF_UP);
        this.importeNeto = FacturaVenta.importeFinito(netoRedondeado);
        this.importeIva = FacturaVenta.importeFinito(iva);
        this.importeSubtotal = FacturaVenta.importeFinito(netoRedondeado.add(iva));
    }
}
