package ar.edu.utn.frm.facturacion.model;

/**
 * Estados posibles de una factura de venta y sus transiciones válidas.
 *
 *   BORRADOR --emitir--> EMITIDA --anular--> ANULADA
 *
 * Regla de negocio (ARCA/AFIP): un comprobante emitido con CAE nunca se borra,
 * solo se anula. La eliminación física queda reservada a los borradores.
 */
public enum EstadoFactura {
    BORRADOR,
    EMITIDA,
    ANULADA;

    public boolean puedeTransicionarA(EstadoFactura destino) {
        return switch (this) {
            case BORRADOR -> destino == EMITIDA;
            case EMITIDA -> destino == ANULADA;
            case ANULADA -> false;
        };
    }
}
