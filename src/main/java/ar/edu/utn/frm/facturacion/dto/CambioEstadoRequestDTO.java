package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuerpo del PATCH /api/facturas/{id}/estado (emitir o anular). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioEstadoRequestDTO {

    @NotBlank(message = "estado es obligatorio (EMITIDA | ANULADA)")
    private String estado;

    @Size(max = 500)
    private String observaciones;
}
