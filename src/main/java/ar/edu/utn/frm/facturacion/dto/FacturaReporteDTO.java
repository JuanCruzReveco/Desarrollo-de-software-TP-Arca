package ar.edu.utn.frm.facturacion.dto;

import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Proyección de solo lectura para el reporte ejecutivo de ventas.
 * Se construye directamente desde JPQL con "SELECT new ...(...)", por lo que
 * el ORDEN y los TIPOS del constructor deben coincidir con la consulta.
 *
 * Respecto del enunciado se agregan "id" y "estado": el frontend los necesita
 * para anular / eliminar una fila y para mostrar su estado.
 */
@Getter
@Setter
@NoArgsConstructor
public class FacturaReporteDTO {
    private Long id;
    private Long numeroFactura;
    private LocalDate fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private String codigoMoneda;

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision, String clienteDenominacion,
                             String condicionIva, String puntoVentaDescripcion, double importeTotal,
                             long cantidadItems, String estado) {
        this.id = id;
        this.numeroFactura = numeroFactura;
        this.fechaEmision = fechaEmision;
        this.clienteDenominacion = clienteDenominacion;
        this.condicionIva = condicionIva;
        this.puntoVentaDescripcion = puntoVentaDescripcion;
        this.importeTotal = importeTotal;
        this.cantidadItems = cantidadItems;
        this.estado = estado;
    }

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision, String clienteDenominacion,
                             String condicionIva, String puntoVentaDescripcion, Double importeTotal,
                             Long cantidadItems, EstadoFactura estado, String codigoMoneda) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
                importeTotal, cantidadItems, estado);
        this.codigoMoneda = codigoMoneda;
    }

    /** Constructor usado por la expresión "SELECT new" de JPQL (recibe el enum directamente). */
    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision, String clienteDenominacion,
                             String condicionIva, String puntoVentaDescripcion, Double importeTotal,
                             Long cantidadItems, EstadoFactura estado) {
        this(id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion,
             importeTotal != null ? importeTotal : 0.0, cantidadItems != null ? cantidadItems : 0L,
             estado != null ? estado.name() : null);
    }
}
