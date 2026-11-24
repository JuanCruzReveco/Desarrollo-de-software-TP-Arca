package ar.edu.utn.frm.facturacion.service.arca;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Los valores fiscales se mantienen separados del id interno de la factura. */
public record ArcaComprobante(int puntoVenta, int tipo, LocalDate fecha,
                              int documentoTipo, long documentoNumero, int condicionIvaReceptor,
                              BigDecimal importeNeto, BigDecimal importeIva, BigDecimal importeTotal,
                              List<Alicuota> alicuotas) {
    public record Alicuota(int codigo, BigDecimal base, BigDecimal importe) {}
}
