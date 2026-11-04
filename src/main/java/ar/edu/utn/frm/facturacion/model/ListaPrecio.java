package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "lista_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ListaPrecio extends AuditoriaApp {

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;
}
