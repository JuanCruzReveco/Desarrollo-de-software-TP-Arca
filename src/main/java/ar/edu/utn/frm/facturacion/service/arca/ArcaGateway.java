package ar.edu.utn.frm.facturacion.service.arca;

import java.time.LocalDate;
import java.util.Optional;

/** Contrato remoto de WSFEv1; la implementación de ejecución usa solo homologación. */
public interface ArcaGateway {
    long ultimoAutorizado(int puntoVenta, int tipo);
    Optional<Autorizacion> consultar(int puntoVenta, int tipo, long numero);
    Respuesta autorizar(ArcaComprobante comprobante, long numero);

    record Autorizacion(String cae, LocalDate vencimiento, int documentoTipo, long documentoNumero,
                        LocalDate fecha, java.math.BigDecimal total, java.math.BigDecimal neto,
                        java.math.BigDecimal iva, String moneda) {}
    record Respuesta(boolean aprobada, String cae, LocalDate vencimiento, String mensaje) {}
}
