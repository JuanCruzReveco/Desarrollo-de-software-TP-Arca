package ar.edu.utn.frm.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** Respuesta completa de GET /api/facturas/{id}: cabecera + renglones. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaDetalladaDTO {
    private Long id;
    private Long numero;
    private LocalDate fechaEmision;
    private String estado;
    private Long clienteId;
    private String clienteDenominacion;
    private String clienteCuitCuil;
    private String clienteDomicilio;
    private Long condicionIvaId;
    private String condicionIva;
    private Long puntoVentaId;
    private Integer puntoVentaNumero;
    private String puntoVentaDescripcion;
    private Long tipoMonedaId;
    private String codigoMoneda;
    private String tipoMoneda;
    private double importeTotal;
    private double importeCobrado;
    private double importeSaldo;
    private String cae;
    private LocalDate caeFechaVencimiento;
    private LocalDate fechaAnulacion;
    private String observaciones;
    private List<FacturaDetalleResponseDTO> detalles;
}
