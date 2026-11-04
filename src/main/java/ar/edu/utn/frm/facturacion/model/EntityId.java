package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclase mapeada: aporta la clave primaria a todas las entidades.
 * No genera tabla propia; sus columnas se "copian" en cada tabla hija.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class EntityId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
}
