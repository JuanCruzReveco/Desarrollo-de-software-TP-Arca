package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Renglón de la factura en el request. El precio unitario NO viaja desde el cliente:
 * el servicio lo toma de ListaPrecioArticulo (regla de negocio del lado del servidor).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaDetalleRequestDTO {

    @NotNull(message = "listaPrecioArticuloId es obligatorio")
    private Long listaPrecioArticuloId;

    @NotNull
    @Positive(message = "La cantidad debe ser mayor a cero")
    private Double cantidad;

    @DecimalMin(value = "0.0") @DecimalMax(value = "100.0")
    private Double porcentajeBonificacion;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String descripcion;
}
