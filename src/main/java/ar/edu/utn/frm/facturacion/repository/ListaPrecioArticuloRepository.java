package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    @EntityGraph(attributePaths = {"articulo", "listaPrecio"})
    List<ListaPrecioArticulo> findAllByOrderByArticulo_DenominacionAsc();

    @EntityGraph(attributePaths = {"articulo", "listaPrecio"})
    List<ListaPrecioArticulo> findByListaPrecio_IdOrderByArticulo_DenominacionAsc(Long listaPrecioId);
}
