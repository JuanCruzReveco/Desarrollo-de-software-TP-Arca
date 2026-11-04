package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "tipo_moneda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TipoMoneda extends AuditoriaApp {

    @Column(nullable = false, length = 10)
    private String codigoAfip;

    @Column(nullable = false)
    private String denominacion;

    @Column(nullable = false, length = 5)
    private String simbolo;
}
