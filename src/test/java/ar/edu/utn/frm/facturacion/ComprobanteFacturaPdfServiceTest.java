package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalleResponseDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.report.ComprobanteFacturaPdfService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComprobanteFacturaPdfServiceTest {

    private final ComprobanteFacturaPdfService comprobantes = new ComprobanteFacturaPdfService();

    @Test
    void facturaEmitidaIncluyeDatosRealesCaeVencimientoYFechaDiaPrimero() throws Exception {
        try (PdfReader reader = new PdfReader(comprobantes.generar(factura(List.of(detalle(1)))))) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(texto).contains("Fecha de emisión", "19/11/2026", "00001-00000042");
            assertThat(texto).doesNotContain("2026-11-19");
            assertThat(texto).contains("Cliente de prueba", "Documento de prueba", "Domicilio de prueba", "IVA Responsable Inscripto");
            assertThat(texto).contains("NB", "Notebook", "1210,00", "Pesos");
            assertThat(texto).contains("12345678901234", "29/11/2026", "CAE (homologación)", "Sin validez fiscal");
            assertThat(texto).doesNotContain("simulado", "Simulado");
            assertThat(texto).contains("Página 1 de 1");
        }
    }

    @Test
    void noImprimeComoAutorizadaUnaFacturaLegadaSinConfirmacionDeArca() {
        FacturaDetalladaDTO legada = factura(List.of(detalle(1)));
        legada.setAutorizacionArca(false);
        assertThatThrownBy(() -> comprobantes.generar(legada))
                .hasMessageContaining("autorización de ARCA");
    }

    @Test
    void facturaCIndicaClaseYSinColumnaIva() throws Exception {
        FacturaDetalladaDTO claseC = factura(List.of(new FacturaDetalleResponseDTO(
                1L, 10L, "NB", "Producto de prueba", 1, 1000, 0, 1000, 0, 1000)));
        claseC.setTipoComprobanteArca(11);
        claseC.setCondicionIva("Consumidor Final");
        claseC.setImporteTotal(1000);
        try (PdfReader reader = new PdfReader(comprobantes.generar(claseC))) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(texto).contains("FACTURA C DE VENTA", "Subtotal", "1000,00");
            assertThat(texto.split("IVA", -1).length - 1).isEqualTo(1); // solo "Condición IVA"
        }
    }

    @Test
    void facturaAParaMonotributistaIncluyeLeyendaFiscal() throws Exception {
        FacturaDetalladaDTO claseA = factura(List.of(detalle(1)));
        claseA.setTipoComprobanteArca(1);
        claseA.setCondicionIvaCodigoAfip(6);
        try (PdfReader reader = new PdfReader(comprobantes.generar(claseA))) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(texto).contains("FACTURA A DE VENTA", "Ley Nº 27.618");
        }
    }

    @Test
    void facturaLargaTieneCabecerasYNumeroRealEnCadaPagina() throws Exception {
        List<FacturaDetalleResponseDTO> detalles = IntStream.rangeClosed(1, 100).mapToObj(this::detalle).toList();
        try (PdfReader reader = new PdfReader(comprobantes.generar(factura(detalles)))) {
            int paginas = reader.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);
            for (int pagina = 1; pagina <= paginas; pagina++) {
                String texto = new PdfTextExtractor(reader).getTextFromPage(pagina);
                assertThat(texto).as("página %s", pagina)
                        .contains("Página " + pagina + " de " + paginas, "Código", "Descripción", "Subtotal");
            }
            assertThat(new PdfTextExtractor(reader).getTextFromPage(paginas)).contains("Total", "121000,00", "Notebook 100");
        }
    }

    private FacturaDetalladaDTO factura(List<FacturaDetalleResponseDTO> detalles) {
        return FacturaDetalladaDTO.builder()
                .id(7L).numero(42L).fechaEmision(LocalDate.of(2026, 11, 19)).estado("EMITIDA")
                .clienteDenominacion("Cliente de prueba").clienteCuitCuil("Documento de prueba")
                .clienteDomicilio("Domicilio de prueba").condicionIva("IVA Responsable Inscripto")
                .puntoVentaNumero(1).puntoVentaDescripcion("Sucursal Central")
                .codigoMoneda("PES").tipoMoneda("Pesos")
                .importeTotal(detalles.size() * 1210.0).importeSaldo(detalles.size() * 1210.0)
                .cae("12345678901234").caeFechaVencimiento(LocalDate.of(2026, 11, 29))
                .autorizacionArca(true)
                .detalles(detalles).build();
    }

    private FacturaDetalleResponseDTO detalle(int numero) {
        return new FacturaDetalleResponseDTO((long) numero, 10L, "NB", "Notebook " + numero,
                1, 1000, 0, 1000, 210, 1210);
    }
}
