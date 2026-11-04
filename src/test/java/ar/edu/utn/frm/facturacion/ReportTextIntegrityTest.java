package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReportTextIntegrityTest {
    private final ReporteFacturasService reportes = new ReporteFacturasService();

    @Test
    void escapaSeparadoresParaMantenerUnaFilaYRestaurarLosTextos() {
        FacturaReporteDTO factura = new FacturaReporteDTO(1L, 1L, LocalDate.of(2026, 1, 1),
                "A\tB\nC", "IVA\rEspecial", "Central\\Norte\"", 10, 1L, "EMITIDA");
        factura.setCodigoMoneda("PES");
        String texto = new String(reportes.generarTextoTabulado(List.of(factura)).contenido(), StandardCharsets.UTF_8);
        assertThat(texto.lines().toList()).hasSize(2);
        String[] columnas = texto.lines().skip(1).findFirst().orElseThrow().split("\t", -1);
        assertThat(columnas).hasSize(9);
        assertThat(columnas[2]).isEqualTo("A\\tB\\nC");
        assertThat(columnas[3]).isEqualTo("IVA\\rEspecial");
        assertThat(columnas[4]).isEqualTo("Central\\\\Norte\"");
    }

    @Test
    void lectorRestauraEscapesLiteralesComillasYTodosLosCamposTextuales() {
        FacturaReporteDTO factura = new FacturaReporteDTO(1L, null, LocalDate.of(2026, 1, 1),
                "Literal\\t y \"comillas\"", "IVA\t\r\n", "Sucursal\\sur", 0, 0L, "BORRADOR\n");
        factura.setCodigoMoneda("PES\t");
        String texto = new String(reportes.generarTextoTabulado(List.of(factura, factura)).contenido(), StandardCharsets.UTF_8);
        List<String> filas = texto.lines().toList();
        assertThat(filas).hasSize(3);
        for (String fila : filas.subList(1, filas.size())) {
            String[] columnas = fila.split("\t", -1);
            assertThat(columnas).hasSize(9);
            assertThat(leerCampo(columnas[2])).isEqualTo("Literal\\t y \"comillas\"");
            assertThat(leerCampo(columnas[3])).isEqualTo("IVA\t\r\n");
            assertThat(leerCampo(columnas[4])).isEqualTo("Sucursal\\sur");
            assertThat(leerCampo(columnas[7])).isEqualTo("BORRADOR\n");
            assertThat(leerCampo(columnas[8])).isEqualTo("PES\t");
        }
    }

    // Lector del contrato TSV: interpreta escapes en una sola pasada, luego de separar columnas.
    private String leerCampo(String campo) {
        StringBuilder salida = new StringBuilder();
        for (int i = 0; i < campo.length(); i++) {
            char c = campo.charAt(i);
            if (c == '\\') {
                c = campo.charAt(++i);
                salida.append(switch (c) { case 't' -> '\t'; case 'r' -> '\r'; case 'n' -> '\n';
                    case '\\' -> '\\'; default -> throw new IllegalArgumentException("Escape inválido"); });
            } else salida.append(c);
        }
        return salida.toString();
    }
}
