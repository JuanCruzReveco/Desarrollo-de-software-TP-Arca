package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.*;
import lombok.*;

/** Precio de venta de un artículo dentro de una lista de precios. */
@Entity
@Table(name = "lista_precio_articulo",
       uniqueConstraints = @UniqueConstraint(columnNames = {"lista_precio_id", "articulo_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ListaPrecioArticulo extends AuditoriaApp {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio listaPrecio;

    @Column(nullable = false)
    private double precioVenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
}
