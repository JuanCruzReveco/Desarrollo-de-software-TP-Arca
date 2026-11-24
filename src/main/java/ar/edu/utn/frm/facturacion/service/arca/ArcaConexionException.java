package ar.edu.utn.frm.facturacion.service.arca;

/** Resultado remoto desconocido; requiere consulta antes de cualquier nuevo intento. */
public class ArcaConexionException extends RuntimeException {
    public ArcaConexionException(String message, Throwable cause) { super(message, cause); }
    public ArcaConexionException(String message) { super(message); }
}
