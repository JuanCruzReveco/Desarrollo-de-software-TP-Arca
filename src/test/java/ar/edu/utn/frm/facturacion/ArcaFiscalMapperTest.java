package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.service.arca.ArcaComprobante;
import ar.edu.utn.frm.facturacion.service.arca.ArcaFiscalMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class ArcaFiscalMapperTest {
    @Test
    void tipoFiscalDependeDeEmisorYReceptor() {
        assertThat(ArcaFiscalMapper.tipoPara(1, 1)).isEqualTo(1);
        assertThat(ArcaFiscalMapper.tipoPara(1, 5)).isEqualTo(6);
        assertThat(ArcaFiscalMapper.tipoPara(6, 5)).isEqualTo(11);
        assertThat(ArcaFiscalMapper.tipoPara(4, 1)).isEqualTo(11);
    }

    @Test
    void facturaCNoDiscriminaIva() {
        FacturaVenta factura = factura("30-00000000-7", 5);
        factura.getDetalles().get(0).setImporteIva(0);
        factura.getDetalles().get(0).setImporteSubtotal(100);
        factura.setImporteTotal(100);
        ArcaComprobante comprobante = ArcaFiscalMapper.desde(factura, 11);
        assertThat(comprobante.importeIva()).isEqualByComparingTo("0");
        assertThat(comprobante.importeNeto()).isEqualByComparingTo("100");
        assertThat(comprobante.alicuotas()).isEmpty();
    }
    @Test
    void receptorIdentificadoFacturaBCierraImportes() {
        FacturaVenta factura = factura("30-00000000-7", 5);
        ArcaComprobante comprobante = ArcaFiscalMapper.desde(factura, 6);
        assertThat(comprobante.documentoTipo()).isEqualTo(80);
        assertThat(comprobante.documentoNumero()).isEqualTo(30000000007L);
        assertThat(comprobante.importeNeto()).isEqualByComparingTo("100.00");
        assertThat(comprobante.importeIva()).isEqualByComparingTo("21.00");
        assertThat(comprobante.importeTotal()).isEqualByComparingTo("121.00");
    }

    @Test
    void noAceptaCuitInvalidaComoDocumentoFiscal() {
        FacturaVenta factura = factura("20-00000000-0", 1);
        assertThatThrownBy(() -> ArcaFiscalMapper.desde(factura, 1))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("CUIT");
    }

    @Test
    void consumidorFinalAnonimoEnFacturaBUsaDocumentoSinIdentificar() {
        ArcaComprobante comprobante = ArcaFiscalMapper.desde(factura(null, 5), 6);
        assertThat(comprobante.documentoTipo()).isEqualTo(99);
        assertThat(comprobante.documentoNumero()).isZero();
    }

    @Test
    void consumidorFinalAnonimoEnFacturaCUsaDocumentoSinIdentificar() {
        FacturaVenta f = factura(null, 5);
        f.getDetalles().get(0).setImporteIva(0);
        f.getDetalles().get(0).setImporteSubtotal(100);
        f.setImporteTotal(100);
        ArcaComprobante comprobante = ArcaFiscalMapper.desde(f, 11);
        assertThat(comprobante.documentoTipo()).isEqualTo(99);
        assertThat(comprobante.documentoNumero()).isZero();
        assertThat(comprobante.importeIva()).isEqualByComparingTo("0");
    }

    @Test
    void consumidorFinalAnonimoDeDiezMillonesDebeIdentificarse() {
        FacturaVenta f = factura(null, 5);
        f.setImporteTotal(10_000_000);
        assertThatThrownBy(() -> ArcaFiscalMapper.desde(f, 11))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("documento válido");
    }

    private static FacturaVenta factura(String cuit, int condicion) {
        FacturaVenta f = new FacturaVenta();
        f.setPuntoVenta(PuntoVenta.builder().numero(2).build());
        f.setFechaEmision(LocalDate.now());
        f.setCondicionIva(CondicionIva.builder().codigoAfip(condicion).build());
        f.setTipoMoneda(TipoMoneda.builder().codigoAfip("PES").build());
        if (cuit != null) f.setCliente(Cliente.builder().cuitCuil(cuit).build());
        f.setImporteTotal(121);
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setImporteNeto(100);
        d.setImporteIva(21);
        d.setImporteSubtotal(121);
        d.setListaPrecioArticulo(ListaPrecioArticulo.builder()
                .articulo(Articulo.builder().alicuotaIva(21.0).build()).build());
        f.agregarDetalle(d);
        return f;
    }
}
