package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigDecimal;

/**
 * Genera el reporte ejecutivo de ventas a partir de la proyección FacturaReporteDTO
 * en tres formatos: PDF (OpenPDF), Excel (Apache POI) y texto separado por tabulaciones.
 */
@Service
public class ReporteFacturasService {

    public record Archivo(String nombre, String mediaType, byte[] contenido) {}

    private static final String[] CABECERAS = {
            "N°", "Fecha", "Cliente", "Condición IVA", "Punto de venta", "Ítems", "Total", "Estado", "Moneda"};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ------------------------------ PDF ------------------------------
    public Archivo generarPdf(List<FacturaReporteDTO> datos) {
        try {
            return construirPdf(datos);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private Archivo construirPdf(List<FacturaReporteDTO> datos) throws DocumentException {
        BaseFont fuente = fuentePdf();
        for (FacturaReporteDTO factura : datos) {
            validarTextoPdf(factura.getClienteDenominacion(), fuente);
            validarTextoPdf(factura.getCondicionIva(), fuente);
            validarTextoPdf(factura.getPuntoVentaDescripcion(), fuente);
            validarTextoPdf(factura.getEstado(), fuente);
            validarTextoPdf(moneda(factura), fuente);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titulo = new Font(fuente, 16, Font.BOLD);
        Font normal = new Font(fuente, 9);
        Font negrita = new Font(fuente, 9, Font.BOLD, Color.WHITE);

        doc.add(new Paragraph("Reporte ejecutivo de ventas", titulo));
        doc.add(new Paragraph("Generado el " + LocalDate.now().format(FECHA) + " · " + datos.size() + " comprobantes", normal));
        doc.add(new Paragraph(" "));

        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 1.5f, 3.5f, 3f, 2.5f, 1f, 1.8f, 1.5f, 1.2f});
        tabla.setWidthPercentage(100);
        for (String c : CABECERAS) {
            PdfPCell celda = new PdfPCell(new Phrase(c, negrita));
            celda.setBackgroundColor(new Color(40, 70, 110));
            celda.setPadding(5);
            tabla.addCell(celda);
        }
        tabla.setHeaderRows(1);
        if (datos.isEmpty()) {
            PdfPCell vacio = celda("Sin facturas para los filtros seleccionados", normal, Element.ALIGN_CENTER);
            vacio.setColspan(CABECERAS.length);
            tabla.addCell(vacio);
        }
        for (FacturaReporteDTO f : datos) {
            tabla.addCell(celda(f.getNumeroFactura() != null ? String.valueOf(f.getNumeroFactura()) : "-", normal, Element.ALIGN_RIGHT));
            tabla.addCell(celda(f.getFechaEmision().format(FECHA), normal, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.getClienteDenominacion(), normal, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.getCondicionIva(), normal, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.getPuntoVentaDescripcion(), normal, Element.ALIGN_LEFT));
            tabla.addCell(celda(String.valueOf(f.getCantidadItems()), normal, Element.ALIGN_RIGHT));
            tabla.addCell(celda(String.format(Locale.ROOT, "%.2f", f.getImporteTotal()), normal, Element.ALIGN_RIGHT));
            tabla.addCell(celda(f.getEstado(), normal, Element.ALIGN_CENTER));
            tabla.addCell(celda(moneda(f), normal, Element.ALIGN_CENTER));
        }
        doc.add(tabla);
        doc.add(new Paragraph(" "));
        for (Map.Entry<String, BigDecimal> total : totalesFacturados(datos).entrySet()) {
            Paragraph pie = new Paragraph(String.format(Locale.ROOT, "Total facturado %s: %.2f",
                    total.getKey(), total.getValue()), new Font(fuente, 11, Font.BOLD));
            pie.setAlignment(Element.ALIGN_RIGHT);
            doc.add(pie);
        }
        doc.close();
        return new Archivo("reporte-facturas.pdf", "application/pdf", out.toByteArray());
    }

    private static PdfPCell celda(String texto, Font font, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, font));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(4);
        return c;
    }

    private static BaseFont fuentePdf() throws DocumentException {
        String recurso = "/fonts/NotoSansCJKsc-Regular.otf";
        try (InputStream stream = ReporteFacturasService.class.getResourceAsStream(recurso)) {
            if (stream == null) throw new IOException("No se encontró la fuente PDF incluida");
            return BaseFont.createFont(recurso, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, stream.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la fuente PDF", e);
        }
    }

    private static void validarTextoPdf(String texto, BaseFont fuente) {
        if (texto == null) return;
        texto.codePoints().forEach(codigo -> {
            if (codigo != '\n' && codigo != '\r' && codigo != '\t' && !fuente.charExists(codigo)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "La fuente PDF no admite el carácter U+%04X. Exporte en TXT o Excel para conservar el texto.", codigo));
            }
        });
    }

    // ------------------------------ EXCEL ------------------------------
    public Archivo generarExcel(List<FacturaReporteDTO> datos) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Facturas");

            CellStyle estiloCabecera = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            estiloCabecera.setFont(f);
            CellStyle estiloFecha = wb.createCellStyle();
            estiloFecha.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle estiloMoneda = wb.createCellStyle();
            estiloMoneda.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

            Row cab = hoja.createRow(0);
            for (int i = 0; i < CABECERAS.length; i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(CABECERAS[i]);
                c.setCellStyle(estiloCabecera);
            }
            int fila = 1;
            for (FacturaReporteDTO d : datos) {
                Row r = hoja.createRow(fila++);
                if (d.getNumeroFactura() != null) r.createCell(0).setCellValue(d.getNumeroFactura());
                Cell fecha = r.createCell(1);
                fecha.setCellValue(d.getFechaEmision());
                fecha.setCellStyle(estiloFecha);
                r.createCell(2).setCellValue(d.getClienteDenominacion());
                r.createCell(3).setCellValue(d.getCondicionIva());
                r.createCell(4).setCellValue(d.getPuntoVentaDescripcion());
                r.createCell(5).setCellValue(d.getCantidadItems());
                Cell total = r.createCell(6);
                total.setCellValue(d.getImporteTotal());
                total.setCellStyle(estiloMoneda);
                r.createCell(7).setCellValue(d.getEstado());
                r.createCell(8).setCellValue(moneda(d));
            }
            // Las fórmulas conservan el criterio de emisión y separan las monedas.
            int ultimaFilaDatos = fila;
            for (String moneda : totalesFacturados(datos).keySet()) {
                Row r = hoja.createRow(++fila);
                r.createCell(5).setCellValue("Total facturado");
                Cell c = r.createCell(6);
                c.setCellFormula("SUMIFS(G2:G" + ultimaFilaDatos + ",H2:H" + ultimaFilaDatos
                        + ",\"EMITIDA\",I2:I" + ultimaFilaDatos + ",I" + (fila + 1) + ")");
                c.setCellStyle(estiloMoneda);
                r.createCell(8).setCellValue(moneda);
            }
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.setForceFormulaRecalculation(true);
            for (int i = 0; i < CABECERAS.length; i++) hoja.autoSizeColumn(i);

            wb.write(out);
            return new Archivo("reporte-facturas.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------ TXT (tabulado) ------------------------------
    public Archivo generarTextoTabulado(List<FacturaReporteDTO> datos) {
        StringBuilder sb = new StringBuilder(String.join("\t", CABECERAS)).append('\n');
        for (FacturaReporteDTO d : datos) {
            sb.append(d.getNumeroFactura() != null ? d.getNumeroFactura() : "").append('\t')
              .append(d.getFechaEmision()).append('\t')
              .append(escaparTabulado(d.getClienteDenominacion())).append('\t')
              .append(escaparTabulado(d.getCondicionIva())).append('\t')
              .append(escaparTabulado(d.getPuntoVentaDescripcion())).append('\t')
              .append(d.getCantidadItems()).append('\t')
              .append(String.format(java.util.Locale.ROOT, "%.2f", d.getImporteTotal())).append('\t')
              .append(escaparTabulado(d.getEstado())).append('\t')
              .append(escaparTabulado(moneda(d))).append('\n');
        }
        return new Archivo("reporte-facturas.txt", "text/tab-separated-values; charset=UTF-8",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** TSV UTF-8: una fila física por factura; escapes reversibles de barra, TAB, CR y LF. */
    private static String escaparTabulado(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\")
                .replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String moneda(FacturaReporteDTO factura) {
        return factura.getCodigoMoneda() != null ? factura.getCodigoMoneda() : "SIN MONEDA";
    }

    private static Map<String, BigDecimal> totalesFacturados(List<FacturaReporteDTO> datos) {
        Map<String, BigDecimal> totales = new TreeMap<>();
        for (FacturaReporteDTO factura : datos) {
            String moneda = moneda(factura);
            totales.putIfAbsent(moneda, BigDecimal.ZERO);
            if ("EMITIDA".equals(factura.getEstado())) {
                totales.merge(moneda, BigDecimal.valueOf(factura.getImporteTotal()), BigDecimal::add);
            }
        }
        return totales;
    }
}
