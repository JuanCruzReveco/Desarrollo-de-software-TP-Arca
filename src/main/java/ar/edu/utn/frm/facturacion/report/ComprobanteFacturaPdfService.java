package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalleResponseDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Comprobante imprimible de una factura autorizada por ARCA en homologación. */
@Service
public class ComprobanteFacturaPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(FacturaDetalladaDTO factura) {
        if (factura == null || !("EMITIDA".equals(factura.getEstado()) || "ANULADA".equals(factura.getEstado()))
                || factura.getCae() == null || factura.getCae().isBlank()
                || factura.getCaeFechaVencimiento() == null || factura.getNumero() == null) {
            throw new ReglaNegocioException("La factura debe estar emitida y tener CAE para imprimir el comprobante");
        }
        if (!factura.isAutorizacionArca()) {
            throw new ReglaNegocioException("La factura no tiene autorización de ARCA en homologación");
        }
        try {
            BaseFont fuente = ReporteFacturasService.fuentePdf();
            validarTextos(factura, fuente);
            byte[] contenido = construir(factura, fuente);
            return paginar(contenido, fuente);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el comprobante PDF", e);
        }
    }

    private static byte[] construir(FacturaDetalladaDTO factura, BaseFont fuente) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 42, 42, 40, 60);
        PdfWriter.getInstance(documento, out);
        documento.open();

        Font titulo = new Font(fuente, 16, Font.BOLD);
        Font subtitulo = new Font(fuente, 11, Font.BOLD);
        Font normal = new Font(fuente, 9);
        Font cabecera = new Font(fuente, 8, Font.BOLD, Color.WHITE);
        Font dato = new Font(fuente, 8);

        documento.add(new Paragraph("Fecha de emisión: " + factura.getFechaEmision().format(FECHA), normal));
        String clase = switch (factura.getTipoComprobanteArca() == null ? 0 : factura.getTipoComprobanteArca()) {
            case 1 -> " A";
            case 6 -> " B";
            case 11 -> " C";
            default -> "";
        };
        boolean claseC = factura.getTipoComprobanteArca() != null && factura.getTipoComprobanteArca() == 11;
        documento.add(new Paragraph("FACTURA" + clase + " DE VENTA", titulo));
        if ("ANULADA".equals(factura.getEstado())) {
            documento.add(new Paragraph("ANULADA", new Font(fuente, 13, Font.BOLD, Color.RED)));
        }
        documento.add(new Paragraph("Factura N° " + numeroComprobante(factura), subtitulo));
        documento.add(new Paragraph("Punto de venta: " + texto(factura.getPuntoVentaDescripcion())
                + "    Estado: " + factura.getEstado(), normal));
        documento.add(new Paragraph("Cliente: " + texto(factura.getClienteDenominacion()), normal));
        documento.add(new Paragraph("CUIT/CUIL: " + texto(factura.getClienteCuitCuil())
                + "    Condición IVA: " + texto(factura.getCondicionIva()), normal));
        if (factura.getTipoComprobanteArca() != null && factura.getTipoComprobanteArca() == 1
                && factura.getCondicionIvaCodigoAfip() != null && factura.getCondicionIvaCodigoAfip() == 6) {
            documento.add(new Paragraph("El crédito fiscal discriminado en el presente comprobante, solo podrá ser computado a efectos del Régimen de Sostenimiento e Inclusión Fiscal para Pequeños Contribuyentes de la Ley Nº 27.618", normal));
        }
        documento.add(new Paragraph("Domicilio: " + texto(factura.getClienteDomicilio()), normal));
        documento.add(new Paragraph("Moneda: " + texto(factura.getCodigoMoneda()) + " - "
                + texto(factura.getTipoMoneda()), normal));
        documento.add(new Paragraph("CAE (homologación): " + factura.getCae(), normal));
        documento.add(new Paragraph("Vencimiento CAE: " + factura.getCaeFechaVencimiento().format(FECHA), normal));
        documento.add(new Paragraph(" "));

        PdfPTable tabla = new PdfPTable(claseC
                ? new float[]{1.05f, 3.3f, .8f, 1.15f, .85f, 1.2f}
                : new float[]{1.05f, 3.3f, .8f, 1.15f, .85f, 1f, 1.2f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String encabezado : claseC
                ? new String[]{"Código", "Descripción", "Cant.", "P. unit.", "Bonif. %", "Subtotal"}
                : new String[]{"Código", "Descripción", "Cant.", "P. unit.", "Bonif. %", "IVA", "Subtotal"}) {
            PdfPCell celda = new PdfPCell(new Phrase(encabezado, cabecera));
            celda.setBackgroundColor(new Color(40, 70, 110));
            celda.setPadding(4);
            tabla.addCell(celda);
        }
        for (FacturaDetalleResponseDTO detalle : factura.getDetalles()) {
            tabla.addCell(celda(texto(detalle.getArticuloCodigo()), dato, Element.ALIGN_LEFT));
            tabla.addCell(celda(texto(detalle.getDescripcion()), dato, Element.ALIGN_LEFT));
            tabla.addCell(celda(numero(detalle.getCantidad()), dato, Element.ALIGN_RIGHT));
            tabla.addCell(celda(numero(detalle.getPrecioUnitario()), dato, Element.ALIGN_RIGHT));
            tabla.addCell(celda(numero(detalle.getPorcentajeBonificacion()), dato, Element.ALIGN_RIGHT));
            if (!claseC) tabla.addCell(celda(numero(detalle.getImporteIva()), dato, Element.ALIGN_RIGHT));
            tabla.addCell(celda(numero(detalle.getImporteSubtotal()), dato, Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
        documento.add(new Paragraph(" "));
        Paragraph total = new Paragraph("Total: " + texto(factura.getCodigoMoneda()) + " "
                + numero(factura.getImporteTotal()), subtitulo);
        total.setAlignment(Element.ALIGN_RIGHT);
        documento.add(total);
        Paragraph saldo = new Paragraph("Cobrado: " + numero(factura.getImporteCobrado())
                + "    Saldo: " + numero(factura.getImporteSaldo()), normal);
        saldo.setAlignment(Element.ALIGN_RIGHT);
        documento.add(saldo);
        documento.close();
        return out.toByteArray();
    }

    private static byte[] paginar(byte[] original, BaseFont fuente) throws IOException, DocumentException {
        try (PdfReader reader = new PdfReader(original)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, out);
            Font pie = new Font(fuente, 8);
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                float centro = reader.getPageSize(pagina).getWidth() / 2;
                ColumnText.showTextAligned(stamper.getOverContent(pagina), Element.ALIGN_CENTER,
                        new Phrase("Página " + pagina + " de " + reader.getNumberOfPages(), pie), centro, 38, 0);
                ColumnText.showTextAligned(stamper.getOverContent(pagina), Element.ALIGN_CENTER,
                        new Phrase("Comprobante de prueba en homologación de ARCA. Sin validez fiscal.", pie), centro, 24, 0);
            }
            stamper.close();
            return out.toByteArray();
        }
    }

    private static PdfPCell celda(String valor, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(valor, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(4);
        return celda;
    }

    private static void validarTextos(FacturaDetalladaDTO factura, BaseFont fuente) {
        for (String valor : new String[]{factura.getClienteDenominacion(), factura.getClienteCuitCuil(),
                factura.getClienteDomicilio(), factura.getCondicionIva(), factura.getPuntoVentaDescripcion(),
                factura.getTipoMoneda(), factura.getCodigoMoneda()}) {
            ReporteFacturasService.validarTextoPdf(valor, fuente);
        }
        for (FacturaDetalleResponseDTO detalle : factura.getDetalles()) {
            ReporteFacturasService.validarTextoPdf(detalle.getArticuloCodigo(), fuente);
            ReporteFacturasService.validarTextoPdf(detalle.getDescripcion(), fuente);
        }
    }

    private static String numeroComprobante(FacturaDetalladaDTO factura) {
        int puntoVenta = factura.getPuntoVentaNumero() == null ? 0 : factura.getPuntoVentaNumero();
        return String.format(Locale.ROOT, "%05d-%08d", puntoVenta, factura.getNumero());
    }

    private static String numero(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor).replace('.', ',');
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }
}
