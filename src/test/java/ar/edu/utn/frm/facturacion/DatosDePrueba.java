package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crea el mínimo de catálogos que una factura necesita. */
@Component
public class DatosDePrueba {

    public Usuario sistema;
    public PuntoVenta puntoVenta;
    public TipoMoneda moneda;
    public CondicionIva condicionIva;
    public Cliente cliente;
    public ListaPrecioArticulo lpaNotebook;   // 1000 + 21% IVA
    public ListaPrecioArticulo lpaLapicera;   // 100 + 10.5% IVA

    public Usuario getSistema() { return sistema; }
    public PuntoVenta getPuntoVenta() { return puntoVenta; }
    public TipoMoneda getMoneda() { return moneda; }
    public CondicionIva getCondicionIva() { return condicionIva; }
    public Cliente getCliente() { return cliente; }
    public ListaPrecioArticulo getLpaNotebook() { return lpaNotebook; }
    public ListaPrecioArticulo getLpaLapicera() { return lpaLapicera; }
    private final UsuarioRepository usuarioRepo;
    private final PuntoVentaRepository puntoVentaRepo;
    private final TipoMonedaRepository tipoMonedaRepo;
    private final CondicionIvaRepository condicionIvaRepo;
    private final ClienteRepository clienteRepo;
    private final ArticuloRepository articuloRepo;
    private final ListaPrecioRepository listaPrecioRepo;
    private final ListaPrecioArticuloRepository lpaRepo;
    private final FacturaVentaRepository facturaRepo;

    public DatosDePrueba(UsuarioRepository usuarioRepo, PuntoVentaRepository puntoVentaRepo, TipoMonedaRepository tipoMonedaRepo,
                         CondicionIvaRepository condicionIvaRepo, ClienteRepository clienteRepo, ArticuloRepository articuloRepo,
                         ListaPrecioRepository listaPrecioRepo, ListaPrecioArticuloRepository lpaRepo, FacturaVentaRepository facturaRepo) {
        this.usuarioRepo = usuarioRepo;
        this.puntoVentaRepo = puntoVentaRepo;
        this.tipoMonedaRepo = tipoMonedaRepo;
        this.condicionIvaRepo = condicionIvaRepo;
        this.clienteRepo = clienteRepo;
        this.articuloRepo = articuloRepo;
        this.listaPrecioRepo = listaPrecioRepo;
        this.lpaRepo = lpaRepo;
        this.facturaRepo = facturaRepo;
    }

    @Transactional
    public void limpiarYCargar() {
        facturaRepo.deleteAll();
        lpaRepo.deleteAll();
        articuloRepo.deleteAll();
        listaPrecioRepo.deleteAll();
        clienteRepo.deleteAll();
        condicionIvaRepo.deleteAll();
        tipoMonedaRepo.deleteAll();
        puntoVentaRepo.deleteAll();
        usuarioRepo.deleteAll();
        usuarioRepo.flush(); // Completa las bajas antes de insertar el mismo nombre único.

        sistema = usuarioRepo.save(Usuario.builder().usuario("sistema").clave("x").nombre("Sistema").apellido("Test").build());
        UsuarioActual.set(sistema);

        puntoVenta = puntoVentaRepo.save(PuntoVenta.builder().numero(1).descripcion("Sucursal Central").build());
        moneda = tipoMonedaRepo.save(TipoMoneda.builder().codigoAfip("PES").denominacion("Pesos").simbolo("$").build());
        condicionIva = condicionIvaRepo.save(CondicionIva.builder().codigoAfip(1).denominacion("IVA Responsable Inscripto").build());
        cliente = clienteRepo.save(Cliente.builder().cuitCuil("30-71234567-8").denominacion("Empresa Tech S.A.")
                .contacto(Contacto.builder().email("a@b.com").build())
                .domicilio(Domicilio.builder().nombreCalle("Calle").numeroCalle("1").build()).build());

        ListaPrecio lista = listaPrecioRepo.save(ListaPrecio.builder().codigo("MIN").denominacion("Minorista").build());
        Articulo notebook = articuloRepo.save(Articulo.builder().codigo("NB").denominacion("Notebook").alicuotaIva(21.0).build());
        Articulo lapicera = articuloRepo.save(Articulo.builder().codigo("LP").denominacion("Lapicera").alicuotaIva(10.5).build());
        lpaNotebook = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(lista).articulo(notebook).precioVenta(1000).build());
        lpaLapicera = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(lista).articulo(lapicera).precioVenta(100).build());
    }
}


