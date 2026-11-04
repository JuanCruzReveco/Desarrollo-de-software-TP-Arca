package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.repository.*;
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
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepo;

    public CatalogoService(PuntoVentaRepository puntoVentaRepo, ClienteRepository clienteRepo,
                           CondicionIvaRepository condicionIvaRepo, TipoMonedaRepository tipoMonedaRepo,
                           ListaPrecioArticuloRepository listaPrecioArticuloRepo) {
        this.puntoVentaRepo = puntoVentaRepo;
        this.clienteRepo = clienteRepo;
        this.condicionIvaRepo = condicionIvaRepo;
        this.tipoMonedaRepo = tipoMonedaRepo;
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
                .map(c -> new OpcionDTO(c.getId(), c.getDenominacion(), null)).toList();
    }

    public List<OpcionDTO> tiposMoneda() {
        return tipoMonedaRepo.findAll().stream()
                .map(t -> new OpcionDTO(t.getId(), t.getDenominacion() + " (" + t.getSimbolo() + ")", null,
                        t.getCodigoAfip())).toList();
    }

    public List<OpcionDTO> listaPrecioArticulos() {
        return listaPrecioArticuloRepo.findAllByOrderByArticulo_DenominacionAsc().stream()
                .map(l -> new OpcionDTO(l.getId(),
                        l.getArticulo().getCodigo() + " - " + l.getArticulo().getDenominacion()
                                + " [" + l.getListaPrecio().getDenominacion() + "]",
                        l.getPrecioVenta())).toList();
    }
}
