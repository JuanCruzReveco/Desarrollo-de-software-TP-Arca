package ar.edu.utn.frm.facturacion.exception;

/** Violación de una regla de negocio. Se traduce a HTTP 409 Conflict. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
