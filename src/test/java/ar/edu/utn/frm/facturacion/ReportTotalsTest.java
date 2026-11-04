package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTotalsTest {
    private final ReporteFacturasService reportes = new ReporteFacturasService();

    @Test
    void pdfYExcelTotalizanSoloEmitidasPorMoneda() throws Exception {
        List<FacturaReporteDTO> datos = List.of(
                factura("EMITIDA", "PES", 100), factura("BORRADOR", "PES", 700),
                factura("ANULADA", "PES", 900), factura("EMITIDA", "DOL", 20),
                factura("EMITIDA", "DOL", 5));

        assertTotales(datos, "PES", 100, "100.00");
        assertTotales(datos, "DOL", 25, "25.00");
    }

    @Test
    void comprobantesSinEmitirNoAportanAlTotalFacturado() throws Exception {
        List<FacturaReporteDTO> datos = List.of(
                factura("BORRADOR", "PES", 700), factura("ANULADA", "PES", 900));

        assertTotales(datos, "PES", 0, "0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PES", "DOL"})
    void textoTabuladoIdentificaMonedaDeCadaImporte(String moneda) {
        String texto = new String(reportes.generarTextoTabulado(List.of(factura("EMITIDA", moneda, 25)))
                .contenido(), StandardCharsets.UTF_8);
        assertThat(texto.lines().findFirst().orElseThrow()).endsWith("\tMoneda");
        assertThat(texto.lines().skip(1).findFirst().orElseThrow()).endsWith("\t" + moneda);
    }

    private void assertTotales(List<FacturaReporteDTO> datos, String moneda,
                              double esperado, String textoImporte) throws Exception {
        try (PdfReader reader = new PdfReader(reportes.generarPdf(datos).contenido())) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(texto).contains("Total facturado " + moneda + ": " + textoImporte);
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(reportes.generarExcel(datos).contenido()))) {
            Sheet hoja = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row total = null;
            for (Row row : hoja) {
                if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.STRING
                        && "Total facturado".equals(row.getCell(5).getStringCellValue())
                        && moneda.equals(row.getCell(8).getStringCellValue())) {
                    total = row;
                }
            }
            assertThat(total).as("Total facturado de %s", moneda).isNotNull();
            assertThat(evaluator.evaluate(total.getCell(6)).getNumberValue()).isEqualTo(esperado);
        }
    }

    private FacturaReporteDTO factura(String estado, String moneda, double total) {
        FacturaReporteDTO factura = new FacturaReporteDTO(1L, 1L, LocalDate.of(2026, 1, 1),
                "Cliente", "IVA", "Central", total, 1L, estado);
        factura.setCodigoMoneda(moneda);
        return factura;
    }
}
