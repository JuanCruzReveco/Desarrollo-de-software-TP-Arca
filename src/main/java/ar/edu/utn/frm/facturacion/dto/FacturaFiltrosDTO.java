package ar.edu.utn.frm.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Filtros opcionales del GET /api/facturas. Todos pueden ser nulos. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaFiltrosDTO {
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private String estado;
    private Double montoMinimo;
}
