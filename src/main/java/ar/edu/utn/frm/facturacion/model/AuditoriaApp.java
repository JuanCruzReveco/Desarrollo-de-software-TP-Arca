package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Superclase mapeada con los datos de auditoría.
 *
 * Los campos obligatorios (fechaAlta, fechaModificacion, usuarioCarga, usuarioModificacion)
 * se completan automáticamente mediante callbacks del ciclo de vida JPA
 * (@PrePersist / @PreUpdate). El usuario se toma de {@link UsuarioActual}, que el
 * backend carga por request a partir del header HTTP "X-Usuario".
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditoriaApp extends EntityId {

    @Column(nullable = false, updatable = false)
    protected LocalDateTime fechaAlta;

    protected LocalDateTime fechaBaja;

    @Column(nullable = false)
    protected LocalDateTime fechaModificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_carga_id", nullable = false, updatable = false)
    protected Usuario usuarioCarga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_baja_id")
    protected Usuario usuarioBaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_modificacion_id", nullable = false)
    protected Usuario usuarioModificacion;

    @PrePersist
    protected void onPrePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaAlta == null) fechaAlta = ahora;
        fechaModificacion = ahora;
        Usuario actual = UsuarioActual.get();
        if (usuarioCarga == null) usuarioCarga = actual;
        if (usuarioModificacion == null) usuarioModificacion = actual;
    }

    @PreUpdate
    protected void onPreUpdate() {
        registrarModificacion();
    }

    /** Registra una escritura del agregado incluso si solo cambian sus entidades hijas. */
    public void registrarModificacion() {
        fechaModificacion = LocalDateTime.now();
        Usuario actual = UsuarioActual.get();
        if (actual != null) usuarioModificacion = actual;
    }

    /** Baja lógica: marca fecha y usuario de baja sin borrar la fila. */
    public void darDeBaja() {
        this.fechaBaja = LocalDateTime.now();
        this.usuarioBaja = UsuarioActual.get();
    }
}
