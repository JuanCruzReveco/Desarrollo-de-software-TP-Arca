package ar.edu.utn.frm.facturacion.exception;

/** Se traduce a HTTP 404 Not Found (ver ApiExceptionHandler). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " con id " + id + " no encontrado");
    }

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
