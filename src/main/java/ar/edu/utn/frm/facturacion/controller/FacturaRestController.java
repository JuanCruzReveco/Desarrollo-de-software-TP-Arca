package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.report.ComprobanteFacturaPdfService;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * API REST de facturas.
 *
 *   GET    /api/facturas?fechaDesde&fechaHasta&estado&montoMinimo   -> 200 lista de FacturaReporteDTO
 *   GET    /api/facturas/{id}                                        -> 200 FacturaDetalladaDTO | 404
 *   GET    /api/facturas/{id}/pdf                                    -> PDF de factura emitida/anulada | 404 | 409
 *   POST   /api/facturas                                             -> 201 Created + Location
 *   PUT    /api/facturas/{id}                                        -> 200 OK | 404 | 409
 *   PATCH  /api/facturas/{id}/estado                                 -> 200 OK | 404 | 409
 *   DELETE /api/facturas/{id}                                        -> 204 No Content | 404 | 409
 *   GET    /api/facturas/reporte?formato=pdf|xlsx|txt (+ filtros)    -> archivo descargable
 */
@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    private final FacturaService facturaService;
    private final ReporteFacturasService reporteService;
    private final ComprobanteFacturaPdfService comprobantePdfService;

    public FacturaRestController(FacturaService facturaService, ReporteFacturasService reporteService,
                                 ComprobanteFacturaPdfService comprobantePdfService) {
        this.facturaService = facturaService;
        this.reporteService = reporteService;
        this.comprobantePdfService = comprobantePdfService;
    }

    @GetMapping
    public List<FacturaReporteDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {
        return facturaService.buscarFacturasFiltradas(new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado, montoMinimo));
    }

    @GetMapping("/{id}")
    public FacturaDetalladaDTO obtener(@PathVariable Long id) {
        return facturaService.obtener(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        FacturaDetalladaDTO factura = facturaService.obtener(id);
        byte[] contenido = comprobantePdfService.generar(factura);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"factura-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@Valid @RequestBody FacturaRequestDTO request) {
        FacturaReporteDTO creada = facturaService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(location).body(creada);          // 201 Created
    }

    @PutMapping("/{id}")
    public FacturaReporteDTO actualizar(@PathVariable Long id, @Valid @RequestBody FacturaRequestDTO request) {
        return facturaService.actualizar(id, request);                  // 200 OK
    }

    @PatchMapping("/{id}/estado")
    public FacturaReporteDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequestDTO request) {
        return facturaService.cambiarEstado(id, request);               // 200 OK
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);                                    // 204 No Content
    }

    @GetMapping("/reporte")
    public ResponseEntity<byte[]> reporte(
            @RequestParam(defaultValue = "pdf") String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {
        List<FacturaReporteDTO> datos = facturaService.buscarFacturasFiltradas(
                new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado, montoMinimo));

        ReporteFacturasService.Archivo archivo = switch (formato.toLowerCase()) {
            case "pdf" -> reporteService.generarPdf(datos);
            case "xlsx", "excel" -> reporteService.generarExcel(datos);
            case "txt", "tsv" -> reporteService.generarTextoTabulado(datos);
            default -> throw new IllegalArgumentException("Formato no soportado: " + formato + " (pdf | xlsx | txt)");
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
                .contentType(MediaType.parseMediaType(archivo.mediaType()))
                .body(archivo.contenido());
    }
}
