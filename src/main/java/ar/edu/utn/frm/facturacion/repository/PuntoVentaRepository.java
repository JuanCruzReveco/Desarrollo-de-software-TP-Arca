package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {
    /** Serializa las emisiones del mismo punto de venta hasta el commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PuntoVenta p WHERE p.id = :id")
    Optional<PuntoVenta> findParaNumerarById(@Param("id") Long id);
}
