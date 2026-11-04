package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos de apoyo para los <select> del frontend. */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoRestController {

    private final CatalogoService catalogoService;

    public CatalogoRestController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/puntos-venta")
    public List<OpcionDTO> puntosVenta() { return catalogoService.puntosVenta(); }

    @GetMapping("/clientes")
    public List<OpcionDTO> clientes() { return catalogoService.clientes(); }

    @GetMapping("/condiciones-iva")
    public List<OpcionDTO> condicionesIva() { return catalogoService.condicionesIva(); }

    @GetMapping("/tipos-moneda")
    public List<OpcionDTO> tiposMoneda() { return catalogoService.tiposMoneda(); }

    @GetMapping("/lista-precio-articulos")
    public List<OpcionDTO> listaPrecioArticulos() { return catalogoService.listaPrecioArticulos(); }
}
