package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportPdfIntegrityTest {
    private final ReporteFacturasService reportes = new ReporteFacturasService();

    @Test
    void conservaNombreChinoEnPdf() throws Exception {
        try (PdfReader reader = new PdfReader(reportes.generarPdf(List.of(factura("李华"))).contenido())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("李华");
        }
    }

    @Test
    void conservaAcentosYOtrosAlfabetosEnPdf() throws Exception {
        try (PdfReader reader = new PdfReader(reportes.generarPdf(List.of(factura("José Muñoz 東京 서울"))).contenido())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("José Muñoz 東京 서울");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cliente 🦄", "Cliente \u0378"})
    void rechazaGlifosNoCubiertosEnLugarDeOmitirlos(String nombre) {
        assertThatThrownBy(() -> reportes.generarPdf(List.of(factura(nombre))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("U+");
    }

    @Test
    void repiteCabecerasEnTodasLasPaginasConDatos() throws Exception {
        List<FacturaReporteDTO> datos = IntStream.range(0, 100).mapToObj(i -> factura("Cliente " + i)).toList();
        try (PdfReader reader = new PdfReader(reportes.generarPdf(datos).contenido())) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                String texto = new PdfTextExtractor(reader).getTextFromPage(pagina);
                if (texto.contains("Central")) {
                    assertThat(texto).as("Cabecera de página %s", pagina).contains("Fecha", "Cliente", "Moneda", "Estado");
                }
            }
        }
    }

    @Test
    void conservaCabeceraEnReporteVacio() throws Exception {
        try (PdfReader reader = new PdfReader(reportes.generarPdf(List.of()).contenido())) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Fecha", "Cliente", "Moneda", "Estado");
        }
    }

    private FacturaReporteDTO factura(String cliente) {
        FacturaReporteDTO factura = new FacturaReporteDTO(1L, 1L, LocalDate.of(2026, 1, 1),
                cliente, "IVA", "Central", 10, 1L, "EMITIDA");
        factura.setCodigoMoneda("PES");
        return factura;
    }
}
