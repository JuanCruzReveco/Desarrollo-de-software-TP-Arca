package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MonetaryOverflowTest {
    @Test
    void rechazaRenglonQueDesbordaDouble() {
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setCantidad(1e308);
        detalle.setPrecioUnitario(1000);
        assertThatThrownBy(() -> detalle.calcularImportes(21)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaSumaDeRenglonesQueDesbordaDouble() {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalleConSubtotal(1e308));
        factura.agregarDetalle(detalleConSubtotal(1e308));
        assertThatThrownBy(factura::recalcularImportes).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void totalGrandeFinitoNoSeSaturaEnLong() {
        FacturaVenta factura = new FacturaVenta();
        factura.agregarDetalle(detalleConSubtotal(1.21e17));
        factura.recalcularImportes();
        assertThat(factura.getImporteTotal()).isEqualTo(1.21e17);
        assertThat(factura.getImporteSaldo()).isEqualTo(1.21e17);
    }

    private FacturaVentaDetalle detalleConSubtotal(double subtotal) {
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setImporteSubtotal(subtotal);
        return detalle;
    }
}
