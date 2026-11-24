package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.repository.*;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Carga datos de ejemplo al arrancar (solo si la base está vacía).
 * Equivale al "Main" del TP 1: instancia el grafo de objetos y persiste en cascada.
 */
@Component
@Profile("!test")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UsuarioRepository usuarioRepo;
    private final PuntoVentaRepository puntoVentaRepo;
    private final TipoMonedaRepository tipoMonedaRepo;
    private final CondicionIvaRepository condicionIvaRepo;
    private final RubroRepository rubroRepo;
    private final MarcaRepository marcaRepo;
    private final ArticuloRepository articuloRepo;
    private final ListaPrecioRepository listaPrecioRepo;
    private final ListaPrecioArticuloRepository lpaRepo;
    private final ClienteRepository clienteRepo;
    private final FacturaService facturaService;
    private final FacturacionProperties props;

    public DataLoader(UsuarioRepository usuarioRepo, PuntoVentaRepository puntoVentaRepo,
                      TipoMonedaRepository tipoMonedaRepo, CondicionIvaRepository condicionIvaRepo,
                      RubroRepository rubroRepo, MarcaRepository marcaRepo, ArticuloRepository articuloRepo,
                      ListaPrecioRepository listaPrecioRepo, ListaPrecioArticuloRepository lpaRepo,
                      ClienteRepository clienteRepo, FacturaService facturaService, FacturacionProperties props) {
        this.usuarioRepo = usuarioRepo;
        this.puntoVentaRepo = puntoVentaRepo;
        this.tipoMonedaRepo = tipoMonedaRepo;
        this.condicionIvaRepo = condicionIvaRepo;
        this.rubroRepo = rubroRepo;
        this.marcaRepo = marcaRepo;
        this.articuloRepo = articuloRepo;
        this.listaPrecioRepo = listaPrecioRepo;
        this.lpaRepo = lpaRepo;
        this.clienteRepo = clienteRepo;
        this.facturaService = facturaService;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepo.count() > 0) {
            log.info("La base ya tiene datos; se omite la carga inicial.");
            return;
        }
        log.info("Cargando datos de ejemplo...");

        // 1) Usuario de sistema: lo necesita la auditoría antes que nada
        Usuario sistema = usuarioRepo.save(Usuario.builder()
                .usuario(props.usuarioPorDefecto()).clave("secreto").nombre("Sistema").apellido("Facturación").build());
        Usuario operadorDemo = usuarioRepo.save(Usuario.builder()
                .usuario("operador-demo").clave("demo").nombre("Operador").apellido("Demo").build());
        UsuarioActual.set(sistema);
        try {
            // 2) Catálogos
            PuntoVenta pv1 = puntoVentaRepo.save(PuntoVenta.builder().numero(1).descripcion("Sucursal Central")
                    .tipoEmision("CAE").domicilioComercial("Domicilio de prueba 1").build());
            PuntoVenta pv2 = puntoVentaRepo.save(PuntoVenta.builder().numero(2).descripcion("Sucursal Godoy Cruz")
                    .tipoEmision("CAE").domicilioComercial("Domicilio de prueba 2").build());

            TipoMoneda pesos = tipoMonedaRepo.save(TipoMoneda.builder().codigoAfip("PES").denominacion("Pesos Argentinos").simbolo("$").build());
            tipoMonedaRepo.save(TipoMoneda.builder().codigoAfip("DOL").denominacion("Dólar Estadounidense").simbolo("U$S").build());

            CondicionIva ri = condicionIvaRepo.save(CondicionIva.builder().codigoAfip(1).denominacion("IVA Responsable Inscripto").build());
            CondicionIva cf = condicionIvaRepo.save(CondicionIva.builder().codigoAfip(5).denominacion("Consumidor Final").build());
            CondicionIva mono = condicionIvaRepo.save(CondicionIva.builder().codigoAfip(6).denominacion("Responsable Monotributo").build());

            Rubro tec = rubroRepo.save(Rubro.builder().codigo(10).denominacion("Tecnología").build());
            Rubro lib = rubroRepo.save(Rubro.builder().codigo(20).denominacion("Librería").build());
            Marca lenovo = marcaRepo.save(Marca.builder().codigo(1).denominacion("Lenovo").build());
            Marca logi = marcaRepo.save(Marca.builder().codigo(2).denominacion("Logitech").build());
            Marca bic = marcaRepo.save(Marca.builder().codigo(3).denominacion("BIC").build());

            Articulo notebook = articuloRepo.save(Articulo.builder().codigo("NB-001").denominacion("Notebook ThinkPad E14").rubro(tec).marca(lenovo).alicuotaIva(21.0).build());
            Articulo mouse = articuloRepo.save(Articulo.builder().codigo("MS-010").denominacion("Mouse inalámbrico M185").rubro(tec).marca(logi).alicuotaIva(21.0).build());
            Articulo teclado = articuloRepo.save(Articulo.builder().codigo("KB-020").denominacion("Teclado K120").rubro(tec).marca(logi).alicuotaIva(21.0).build());
            Articulo resma = articuloRepo.save(Articulo.builder().codigo("LB-100").denominacion("Resma A4 500 hojas").rubro(lib).marca(null).alicuotaIva(21.0).build());
            Articulo lapicera = articuloRepo.save(Articulo.builder().codigo("LB-200").denominacion("Lapicera azul x12").rubro(lib).marca(bic).alicuotaIva(10.5).build());

            ListaPrecio minorista = listaPrecioRepo.save(ListaPrecio.builder().codigo("MIN").denominacion("Minorista").build());
            ListaPrecio mayorista = listaPrecioRepo.save(ListaPrecio.builder().codigo("MAY").denominacion("Mayorista").build());

            ListaPrecioArticulo lpNotebook = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(minorista).articulo(notebook).precioVenta(950000).build());
            ListaPrecioArticulo lpMouse = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(minorista).articulo(mouse).precioVenta(18500).build());
            ListaPrecioArticulo lpTeclado = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(minorista).articulo(teclado).precioVenta(22000).build());
            ListaPrecioArticulo lpResma = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(minorista).articulo(resma).precioVenta(9800).build());
            ListaPrecioArticulo lpLapicera = lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(minorista).articulo(lapicera).precioVenta(6400).build());
            lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(mayorista).articulo(resma).precioVenta(8200).build());
            lpaRepo.save(ListaPrecioArticulo.builder().listaPrecio(mayorista).articulo(mouse).precioVenta(15900).build());

            // 3) Clientes: Contacto y Domicilio se insertan en cascada desde Cliente
            // Datos deliberadamente no verificables: deben reemplazarse antes de una emisión WSFEv1.
            Cliente tech = clienteRepo.save(Cliente.builder().cuitCuil("00-00000000-0").denominacion("Cliente de demostración A (sin validar)")
                    .contacto(Contacto.builder().email("demo-a@example.invalid").build())
                    .domicilio(Domicilio.builder().nombreCalle("Calle de prueba").numeroCalle("1").build()).build());
            Cliente utn = clienteRepo.save(Cliente.builder().cuitCuil("00-00000000-0").denominacion("Cliente de demostración B (sin validar)")
                    .contacto(Contacto.builder().email("demo-b@example.invalid").build())
                    .domicilio(Domicilio.builder().nombreCalle("Calle de prueba").numeroCalle("2").build()).build());
            Cliente juan = clienteRepo.save(Cliente.builder().cuitCuil("00-00000000-0").denominacion("Cliente de demostración C (sin validar)")
                    .contacto(Contacto.builder().email("demo-c@example.invalid").build())
                    .domicilio(Domicilio.builder().nombreCalle("Calle de prueba").numeroCalle("3").build()).build());

            // 4) Facturas de ejemplo: solo borradores. Ningún CAE se inventa al arrancar.
            long f1 = crear(null, cf, pesos, pv1, LocalDate.now(), "Venta mostrador",
                    item(lpMouse, 1, 0), item(lpTeclado, 1, 0));

            long f2 = crear(null, cf, pesos, pv1, LocalDate.now(), "Venta de demostración; Consumidor Final",
                    item(lpNotebook, 2, 5), item(lpMouse, 2, 0), item(lpTeclado, 2, 0), item(lpResma, 10, 10), item(lpLapicera, 5, 0));

            long f3 = crear(null, cf, pesos, pv2, LocalDate.now(), "Venta de demostración; Consumidor Final",
                    item(lpResma, 20, 15), item(lpLapicera, 10, 0));

            long f4 = crear(null, cf, pesos, pv2, LocalDate.now(), "Venta de demostración; Consumidor Final",
                    item(lpNotebook, 1, 0));

            crear(null, cf, pesos, pv1, LocalDate.now(), "Presupuesto pendiente de confirmar",
                    item(lpResma, 3, 0));   // queda en BORRADOR

            UsuarioActual.set(operadorDemo);
            crear(null, cf, pesos, pv1, LocalDate.now(), "Cargada por operador demo; Consumidor Final",
                    item(lpMouse, 4, 0));   // BORRADOR auditado por otro usuario

            log.info("Datos de ejemplo cargados: {} facturas.", 6);
        } finally {
            UsuarioActual.clear();
        }
    }

    private long crear(Cliente cliente, CondicionIva condicionIva, TipoMoneda moneda, PuntoVenta pv,
                       LocalDate fecha, String obs, FacturaDetalleRequestDTO... detalles) {
        FacturaRequestDTO req = FacturaRequestDTO.builder()
                .clienteId(cliente != null ? cliente.getId() : null)
                .condicionIvaId(condicionIva.getId())
                .tipoMonedaId(moneda.getId())
                .puntoVentaId(pv.getId())
                .fechaEmision(fecha)
                .observaciones(obs)
                .detalles(List.of(detalles))
                .build();
        return facturaService.crear(req).getId();
    }

    private static FacturaDetalleRequestDTO item(ListaPrecioArticulo lpa, double cantidad, double bonif) {
        return FacturaDetalleRequestDTO.builder()
                .listaPrecioArticuloId(lpa.getId()).cantidad(cantidad).porcentajeBonificacion(bonif).build();
    }

}
