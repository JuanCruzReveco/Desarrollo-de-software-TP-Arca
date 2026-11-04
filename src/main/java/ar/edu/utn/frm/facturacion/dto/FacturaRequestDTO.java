package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de entrada para POST / PUT. Viaja con IDs de las relaciones (nunca entidades anidadas):
 * desacopla el contrato de la API del modelo de base de datos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaRequestDTO {

    /** Nulo => Consumidor Final. */
    private Long clienteId;

    @NotNull(message = "condicionIvaId es obligatorio")
    private Long condicionIvaId;

    @NotNull(message = "puntoVentaId es obligatorio")
    private Long puntoVentaId;

    @NotNull(message = "tipoMonedaId es obligatorio")
    private Long tipoMonedaId;

    /** Nula => hoy. */
    private LocalDate fechaEmision;

    @Size(max = 500)
    private String observaciones;

    @NotEmpty(message = "La factura debe tener al menos un detalle")
    @Valid
    private List<@NotNull(message = "El detalle no puede ser nulo") FacturaDetalleRequestDTO> detalles;
}
