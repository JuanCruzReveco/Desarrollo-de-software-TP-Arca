package ar.edu.utn.frm.facturacion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long listaPrecioId;

    public OpcionDTO(Long id, String etiqueta, Double valor) {
        this(id, etiqueta, valor, null, null);
    }

    public OpcionDTO(Long id, String etiqueta, Double valor, String codigoMoneda) {
        this(id, etiqueta, valor, codigoMoneda, null);
    }
}
