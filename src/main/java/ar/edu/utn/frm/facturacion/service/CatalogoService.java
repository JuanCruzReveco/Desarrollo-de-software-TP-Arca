package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Catálogos de solo lectura para que el frontend arme sus combos. */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final PuntoVentaRepository puntoVentaRepo;
    private final ClienteRepository clienteRepo;
    private final CondicionIvaRepository condicionIvaRepo;
    private final TipoMonedaRepository tipoMonedaRepo;
    private final ListaPrecioRepository listaPrecioRepo;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepo;

    public CatalogoService(PuntoVentaRepository puntoVentaRepo, ClienteRepository clienteRepo,
                           CondicionIvaRepository condicionIvaRepo, TipoMonedaRepository tipoMonedaRepo,
                           ListaPrecioRepository listaPrecioRepo,
                           ListaPrecioArticuloRepository listaPrecioArticuloRepo) {
        this.puntoVentaRepo = puntoVentaRepo;
        this.clienteRepo = clienteRepo;
        this.condicionIvaRepo = condicionIvaRepo;
        this.tipoMonedaRepo = tipoMonedaRepo;
        this.listaPrecioRepo = listaPrecioRepo;
        this.listaPrecioArticuloRepo = listaPrecioArticuloRepo;
    }

    public List<OpcionDTO> puntosVenta() {
        return puntoVentaRepo.findAll().stream()
                .map(p -> new OpcionDTO(p.getId(), String.format("%04d - %s", p.getNumero(), p.getDescripcion()), null)).toList();
    }

    public List<OpcionDTO> clientes() {
        return clienteRepo.findAll().stream()
                .map(c -> new OpcionDTO(c.getId(), c.getDenominacion() + " (" + c.getCuitCuil() + ")", null)).toList();
    }

    public List<OpcionDTO> condicionesIva() {
        return condicionIvaRepo.findAll().stream()
                .map(c -> new OpcionDTO(c.getId(), c.getDenominacion(), (double) c.getCodigoAfip())).toList();
    }

    public List<OpcionDTO> tiposMoneda() {
        return tipoMonedaRepo.findAll().stream()
                .map(t -> new OpcionDTO(t.getId(), t.getDenominacion() + " (" + t.getSimbolo() + ")", null,
                        t.getCodigoAfip())).toList();
    }

    public List<OpcionDTO> listasPrecio() {
        return listaPrecioRepo.findAll(Sort.by("denominacion")).stream()
                .map(l -> new OpcionDTO(l.getId(), l.getDenominacion(), null)).toList();
    }

    public List<OpcionDTO> listaPrecioArticulos() {
        return listaPrecioArticulos(null);
    }

    public List<OpcionDTO> listaPrecioArticulos(Long listaPrecioId) {
        var articulos = listaPrecioId == null
                ? listaPrecioArticuloRepo.findAllByOrderByArticulo_DenominacionAsc()
                : listaPrecioArticuloRepo.findByListaPrecio_IdOrderByArticulo_DenominacionAsc(listaPrecioId);
        return articulos.stream()
                .map(l -> new OpcionDTO(l.getId(),
                        l.getArticulo().getCodigo() + " - " + l.getArticulo().getDenominacion()
                                + " [" + l.getListaPrecio().getDenominacion() + "]",
                        l.getPrecioVenta(), null, l.getListaPrecio().getId())).toList();
    }
}
