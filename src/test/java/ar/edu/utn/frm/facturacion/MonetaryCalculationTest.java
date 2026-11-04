package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonetaryCalculationTest {
    @Test
    void subtotalSumaNetoEIvaRedondeados() {
        FacturaVentaDetalle detalle = detalle(0.025);
        detalle.calcularImportes(21);

        assertThat(detalle.getImporteNeto()).isEqualTo(0.03);
        assertThat(detalle.getImporteIva()).isEqualTo(0.01);
        assertThat(detalle.getImporteSubtotal()).isEqualTo(0.04);
    }

    @Test
    void mitadDecimalSeRedondeaHaciaArribaSinErrorBinario() {
        FacturaVentaDetalle detalle = detalle(1.005);
        detalle.calcularImportes(0);

        assertThat(detalle.getImporteNeto()).isEqualTo(1.01);
        assertThat(detalle.getImporteIva()).isZero();
        assertThat(detalle.getImporteSubtotal()).isEqualTo(1.01);
    }

    @Test
    void conservaCalculoConBonificacionYDistintasAlicuotas() {
        FacturaVentaDetalle notebook = detalle(1000);
        notebook.setCantidad(2);
        notebook.setPorcentajeBonificacion(10);
        notebook.calcularImportes(21);
        assertThat(notebook.getImporteNeto()).isEqualTo(1800);
        assertThat(notebook.getImporteIva()).isEqualTo(378);
        assertThat(notebook.getImporteSubtotal()).isEqualTo(2178);

        FacturaVentaDetalle lapicera = detalle(100);
        lapicera.setCantidad(3);
        lapicera.calcularImportes(10.5);
        assertThat(lapicera.getImporteNeto()).isEqualTo(300);
        assertThat(lapicera.getImporteIva()).isEqualTo(31.5);
        assertThat(lapicera.getImporteSubtotal()).isEqualTo(331.5);
    }

    private FacturaVentaDetalle detalle(double precio) {
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(precio);
        return detalle;
    }
}
