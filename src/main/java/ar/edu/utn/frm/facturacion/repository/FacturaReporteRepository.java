package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;

import java.util.List;

/** Fragmento de repositorio con la consulta dinámica de reporte (implementación en ...Impl). */
public interface FacturaReporteRepository {
    List<FacturaReporteDTO> buscarReporte(FacturaFiltrosDTO filtros);
}
