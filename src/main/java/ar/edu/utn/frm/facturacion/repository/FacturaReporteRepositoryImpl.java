package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta JPQL dinámica con expresión de constructor ("SELECT new ...").
 *
 * Spring Data detecta esta clase por convención de nombre (<Fragmento>Impl) y la
 * "mezcla" dentro de FacturaVentaRepository.
 *
 * Diferencias con el ejemplo del enunciado:
 *  - LEFT JOIN f.detalles: una factura sin renglones también aparece (con 0 ítems).
 *  - Se proyecta id y estado para que el frontend pueda operar sobre cada fila.
 *  - El estado es un enum: la DTO tiene un constructor que recibe EstadoFactura.
 */
public class FacturaReporteRepositoryImpl implements FacturaReporteRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<FacturaReporteDTO> buscarReporte(FacturaFiltrosDTO filtros) {
        StringBuilder jpql = new StringBuilder(
            "SELECT new ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO(" +
            "   f.id, f.numero, f.fechaEmision, " +
            "   COALESCE(c.denominacion, 'Consumidor Final'), " +
            "   ci.denominacion, pv.descripcion, f.importeTotal, COUNT(d), f.estado, tm.codigoAfip" +
            ") " +
            "FROM FacturaVenta f " +
            "LEFT JOIN f.cliente c " +
            "JOIN f.condicionIva ci " +
            "JOIN f.puntoVenta pv " +
            "JOIN f.tipoMoneda tm " +
            "LEFT JOIN f.detalles d " +
            "WHERE 1=1 ");

        Map<String, Object> params = new HashMap<>();

        if (filtros.getFechaDesde() != null) {
            jpql.append("AND f.fechaEmision >= :fechaDesde ");
            params.put("fechaDesde", filtros.getFechaDesde());
        }
        if (filtros.getFechaHasta() != null) {
            jpql.append("AND f.fechaEmision <= :fechaHasta ");
            params.put("fechaHasta", filtros.getFechaHasta());
        }
        if (filtros.getEstado() != null && !filtros.getEstado().isBlank()) {
            jpql.append("AND f.estado = :estado ");
            params.put("estado", EstadoFactura.valueOf(filtros.getEstado().trim().toUpperCase()));
        }
        if (filtros.getMontoMinimo() != null) {
            jpql.append("AND f.importeTotal >= :montoMinimo ");
            params.put("montoMinimo", filtros.getMontoMinimo());
        }

        jpql.append("GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, ci.denominacion, ")
            .append("pv.descripcion, f.importeTotal, f.estado, tm.codigoAfip ")
            .append("ORDER BY f.fechaEmision DESC, f.id DESC");

        TypedQuery<FacturaReporteDTO> query = em.createQuery(jpql.toString(), FacturaReporteDTO.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }
}
