package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "articulo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Articulo extends AuditoriaApp {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubro_id")
    private Rubro rubro;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    /**
     * Alícuota de IVA del artículo (21, 10.5, 27, 0). Agregada al modelo del TP para que
     * el servicio pueda calcular el IVA de cada renglón. Nula => se usa facturacion.iva-por-defecto.
     */
    private Double alicuotaIva;
}
