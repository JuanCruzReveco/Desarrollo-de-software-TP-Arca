package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long>, FacturaReporteRepository {

    /** Trae la factura con sus relaciones para armar la respuesta detallada sin N+1. */
    @EntityGraph(attributePaths = {"cliente", "condicionIva", "tipoMoneda", "puntoVenta",
                                   "detalles", "detalles.listaPrecioArticulo", "detalles.listaPrecioArticulo.articulo"})
    Optional<FacturaVenta> findConDetallesById(Long id);

    /** Bloquea solo la cabecera; las relaciones se cargan dentro de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FacturaVenta f WHERE f.id = :id")
    Optional<FacturaVenta> findParaActualizarById(@Param("id") Long id);

    /** Próximo número correlativo del punto de venta. */
    @Query("SELECT COALESCE(MAX(f.numero), 0) + 1 FROM FacturaVenta f WHERE f.puntoVenta = :puntoVenta")
    Long proximoNumero(@Param("puntoVenta") PuntoVenta puntoVenta);
}
