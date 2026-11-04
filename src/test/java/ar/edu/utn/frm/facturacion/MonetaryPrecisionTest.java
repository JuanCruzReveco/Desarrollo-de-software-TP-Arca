package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MonetaryPrecisionTest {
    private FacturaVentaDetalle detalle(double subtotal) {
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setImporteSubtotal(subtotal);
        return detalle;
    }

    @Test void rechazaTotalQuePierdeCentavosAunqueSeaFinito() {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalle(11495000000000000.0));
        factura.agregarDetalle(detalle(0.71));
        assertThatThrownBy(factura::recalcularImportes).isInstanceOf(ReglaNegocioException.class);
    }

    @ParameterizedTest @ValueSource(doubles = {0.01, 0.07, 0.99})
    void rechazaCentavosNoRepresentablesEnElBorde(double centavos) {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalle(70368744177664.0));
        factura.agregarDetalle(detalle(centavos));
        assertThatThrownBy(factura::recalcularImportes).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void aceptaCentavosRepresentablesSinAlterarlos() {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalle(1000000000000.0));
        factura.agregarDetalle(detalle(0.71));
        factura.recalcularImportes();
        assertThat(BigDecimal.valueOf(factura.getImporteTotal())).isEqualByComparingTo("1000000000000.71");
        assertThat(BigDecimal.valueOf(factura.getImporteSaldo())).isEqualByComparingTo("1000000000000.71");
    }

    @Test void rechazaPerdidaDePrecisionEnImportesDeRenglon() {
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        // Neto decimal 9500000000000095: el double más cercano altera ese importe.
        detalle.setCantidad(10000000000.0001);
        detalle.setPrecioUnitario(950000);
        assertThatThrownBy(() -> detalle.calcularImportes(21)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void rechazaSaldoQuePierdeCentavos() {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalle(11495000000000000.0));
        factura.setImporteCobrado(0.71);
        assertThatThrownBy(factura::recalcularImportes).isInstanceOf(ReglaNegocioException.class);
    }
}
