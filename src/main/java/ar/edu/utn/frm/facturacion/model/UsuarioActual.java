package ar.edu.utn.frm.facturacion.model;

/**
 * Guarda el usuario que ejecuta la operación en curso (uno por hilo/request).
 * Lo setea el interceptor HTTP (ver config.UsuarioActualInterceptor) y lo lee
 * AuditoriaApp en sus callbacks @PrePersist / @PreUpdate.
 *
 * En un sistema real este dato saldría del contexto de seguridad (Spring Security).
 */
public final class UsuarioActual {

    private static final ThreadLocal<Usuario> ACTUAL = new ThreadLocal<>();

    private UsuarioActual() {}

    public static void set(Usuario usuario) { ACTUAL.set(usuario); }

    public static Usuario get() { return ACTUAL.get(); }

    public static void clear() { ACTUAL.remove(); }
}
