package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.config.ArcaProperties;
import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Catálogos de apoyo para los <select> del frontend. */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoRestController {

    private final CatalogoService catalogoService;
    private final ArcaProperties arcaProperties;

    public CatalogoRestController(CatalogoService catalogoService, ArcaProperties arcaProperties) {
        this.catalogoService = catalogoService;
        this.arcaProperties = arcaProperties;
    }

    @GetMapping("/configuracion-fiscal")
    public Map<String, Object> configuracionFiscal() {
        Integer emisor = arcaProperties.condicionIvaEmisor();
        return Map.of("emisorCondicionIva", emisor == null ? 0 : emisor,
                "tiposPermitidos", emisor != null && emisor == 1 ? List.of(1, 6)
                        : emisor != null && (emisor == 4 || emisor == 6) ? List.of(11) : List.of());
    }

    @GetMapping("/puntos-venta")
    public List<OpcionDTO> puntosVenta() { return catalogoService.puntosVenta(); }

    @GetMapping("/clientes")
    public List<OpcionDTO> clientes() { return catalogoService.clientes(); }

    @GetMapping("/condiciones-iva")
    public List<OpcionDTO> condicionesIva() { return catalogoService.condicionesIva(); }

    @GetMapping("/tipos-moneda")
    public List<OpcionDTO> tiposMoneda() { return catalogoService.tiposMoneda(); }

    @GetMapping("/listas-precio")
    public List<OpcionDTO> listasPrecio() { return catalogoService.listasPrecio(); }

    @GetMapping("/lista-precio-articulos")
    public List<OpcionDTO> listaPrecioArticulos(@RequestParam(required = false) Long listaPrecioId) {
        return catalogoService.listaPrecioArticulos(listaPrecioId);
    }
}
