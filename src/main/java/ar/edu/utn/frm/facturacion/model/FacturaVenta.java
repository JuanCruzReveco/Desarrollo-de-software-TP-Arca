package ar.edu.utn.frm.facturacion.model;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de la factura de venta. Es la raíz del agregado: los detalles se persisten,
 * actualizan y eliminan en cascada a través de ella (CascadeType.ALL + orphanRemoval).
 */
@Entity
@Table(name = "factura_venta",
       uniqueConstraints = @UniqueConstraint(columnNames = {"punto_venta_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"detalles"})
@EqualsAndHashCode(callSuper = true, exclude = {"detalles"})
public class FacturaVenta extends AuditoriaApp {

    /** Número correlativo por punto de venta. Se asigna al emitir. */
    private Long numero;

    @Column(nullable = false)
    private LocalDate fechaEmision;

    /** Nulo => Consumidor Final. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    private double importeCobrado;
    private double importeSaldo;

    @Column(nullable = false)
    private double importeTotal;

    @Column(length = 14)
    private String cae;
    private LocalDate caeFechaVencimiento;
    private String resultadoAfip;
    private String motivoRechazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFactura estado;

    private LocalDate fechaAnulacion;

    @Column(length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    // ---- Métodos de dominio: mantienen la relación bidireccional consistente ----

    public void agregarDetalle(FacturaVentaDetalle detalle) {
        detalle.setFactura(this);
        detalles.add(detalle);
    }

    /** Vacía la colección: con orphanRemoval, Hibernate borra los renglones huérfanos al hacer flush. */
    public void limpiarDetalles() {
        detalles.clear();
    }

    /** Recalcula el total a partir de los subtotales de los ítems. */
    public void recalcularImportes() {
        BigDecimal total = detalles.stream().map(d -> decimalFinito(d.getImporteSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.importeTotal = importeFinito(total);
        this.importeSaldo = importeFinito(total.subtract(decimalFinito(importeCobrado)));
    }

    static double redondear(double valor) {
        return importeFinito(decimalFinito(valor));
    }

    static BigDecimal decimalFinito(double valor) {
        if (!Double.isFinite(valor)) {
            throw new ReglaNegocioException("El importe debe ser un número finito");
        }
        return BigDecimal.valueOf(valor);
    }

    static double importeFinito(BigDecimal valor) {
        BigDecimal redondeado = valor.setScale(2, RoundingMode.HALF_UP);
        double importe = redondeado.doubleValue();
        if (!Double.isFinite(importe)) {
            throw new ReglaNegocioException("El importe excede el rango monetario soportado");
        }
        if (BigDecimal.valueOf(importe).compareTo(redondeado) != 0) {
            throw new ReglaNegocioException("El importe excede la precisión monetaria soportada; se perderían centavos");
        }
        return importe;
    }
}
