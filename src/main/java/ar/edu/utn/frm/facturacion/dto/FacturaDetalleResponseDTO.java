package ar.edu.utn.frm.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDetalleResponseDTO {
    private Long id;
    private Long listaPrecioArticuloId;
    private String articuloCodigo;
    private String descripcion;
    private double cantidad;
    private double precioUnitario;
    private double porcentajeBonificacion;
    private double importeNeto;
    private double importeIva;
    private double importeSubtotal;
}
