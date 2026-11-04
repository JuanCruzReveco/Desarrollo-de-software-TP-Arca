package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "punto_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PuntoVenta extends AuditoriaApp {

    @Column(nullable = false, unique = true)
    private int numero;

    private String descripcion;
    private String tipoEmision;
    private String domicilioComercial;
}
