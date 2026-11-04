package ar.edu.utn.frm.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Par id/etiqueta para poblar los <select> del frontend (catálogos). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpcionDTO {
    private Long id;
    private String etiqueta;
    /** Dato extra opcional (ej.: precio de venta en lista-precio-articulos). */
    private Double valor;
    private String codigoMoneda;

    public OpcionDTO(Long id, String etiqueta, Double valor) {
        this(id, etiqueta, valor, null);
    }
}
