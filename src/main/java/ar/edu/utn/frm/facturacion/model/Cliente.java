package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Cliente extends AuditoriaApp {

    @Column(nullable = false, length = 13)
    private String cuitCuil;

    @Column(nullable = false)
    private String denominacion;

    /** Contacto y Domicilio viven y mueren con el cliente: cascada ALL. */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "domicilio_id", nullable = false)
    private Domicilio domicilio;
}
