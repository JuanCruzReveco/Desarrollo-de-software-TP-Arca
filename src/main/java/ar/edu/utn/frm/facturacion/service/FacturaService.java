package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.config.FacturacionProperties;
import ar.edu.utn.frm.facturacion.config.ArcaProperties;
import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.repository.*;
import ar.edu.utn.frm.facturacion.service.arca.ArcaEmisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * Lógica de negocio de facturación. Todas las operaciones de escritura son @Transactional:
 * si algo falla a mitad de camino, no queda nada a medias en la base.
 *
 * Ciclo de vida:  POST crea en BORRADOR -> PATCH estado=EMITIDA asigna número y CAE
 *                 -> PATCH estado=ANULADA anula.  PUT y DELETE solo sobre BORRADOR.
 */
@Service
@Transactional(readOnly = true)
public class FacturaService {

    private final FacturaVentaRepository facturaRepo;
    private final ClienteRepository clienteRepo;
    private final CondicionIvaRepository condicionIvaRepo;
    private final TipoMonedaRepository tipoMonedaRepo;
    private final PuntoVentaRepository puntoVentaRepo;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepo;
    private final FacturacionProperties props;
    private final ArcaProperties arcaProperties;
    private final ArcaEmisionService arcaEmision;
    private final TransactionTemplate transactions;

    public FacturaService(FacturaVentaRepository facturaRepo, ClienteRepository clienteRepo,
                          CondicionIvaRepository condicionIvaRepo, TipoMonedaRepository tipoMonedaRepo,
                          PuntoVentaRepository puntoVentaRepo, ListaPrecioArticuloRepository listaPrecioArticuloRepo,
                          FacturacionProperties props, ArcaProperties arcaProperties, ArcaEmisionService arcaEmision,
                          PlatformTransactionManager manager) {
        this.facturaRepo = facturaRepo;
        this.clienteRepo = clienteRepo;
        this.condicionIvaRepo = condicionIvaRepo;
        this.tipoMonedaRepo = tipoMonedaRepo;
        this.puntoVentaRepo = puntoVentaRepo;
        this.listaPrecioArticuloRepo = listaPrecioArticuloRepo;
        this.props = props;
        this.arcaProperties = arcaProperties;
        this.arcaEmision = arcaEmision;
        this.transactions = new TransactionTemplate(manager);
    }

    // ======================= LECTURA =======================

    public List<FacturaReporteDTO> buscarFacturasFiltradas(FacturaFiltrosDTO filtros) {
        if (filtros.getMontoMinimo() != null && !Double.isFinite(filtros.getMontoMinimo())) {
            throw new IllegalArgumentException("El monto mínimo debe ser un número finito");
        }
        if (filtros.getFechaDesde() != null && filtros.getFechaHasta() != null
                && filtros.getFechaDesde().isAfter(filtros.getFechaHasta())) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        }
        return facturaRepo.buscarReporte(filtros);
    }

    public FacturaDetalladaDTO obtener(Long id) {
        return toDetallada(buscarConDetalles(id));
    }

    public FacturaReporteDTO obtenerReporte(Long id) {
        return toReporte(buscarConDetalles(id));
    }

    // ======================= ESCRITURA =======================

    /** Crea la factura (en BORRADOR) y persiste sus detalles en cascada con un solo save(). */
    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO request) {
        FacturaVenta factura = new FacturaVenta();
        factura.setEstado(EstadoFactura.BORRADOR);
        aplicarRequest(factura, request);
        FacturaVenta guardada = facturaRepo.save(factura);   // cascada: inserta cabecera + detalles
        return toReporte(guardada);
    }

    /** Reemplaza cabecera e ítems. Solo permitido mientras la factura es un borrador. */
    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO request) {
        FacturaVenta factura = buscarParaActualizar(id);
        exigirBorrador(factura, "modificar");
        aplicarRequest(factura, request);
        factura.registrarModificacion();
        return toReporte(facturaRepo.save(factura));
    }

    /** La emisión reserva en una transacción y llama a ARCA fuera de ella. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FacturaReporteDTO cambiarEstado(Long id, CambioEstadoRequestDTO request) {
        EstadoFactura destino = parsearEstado(request.getEstado());
        if (destino == EstadoFactura.EMITIDA) {
            arcaEmision.emitir(id, request.getObservaciones());
            return obtenerReporte(id);
        }
        return transactions.execute(status -> {
            FacturaVenta factura = buscarParaActualizar(id);
            if (!factura.getEstado().puedeTransicionarA(destino)) {
                throw new ReglaNegocioException("No se puede pasar de " + factura.getEstado() + " a " + destino);
            }
            if (destino != EstadoFactura.ANULADA) throw new ReglaNegocioException("Transición no soportada: " + destino);
            if (factura.getCae() != null && !factura.getCae().isBlank()) {
                throw new ReglaNegocioException("Un CAE autorizado no se anula con un cambio de estado local; requiere una nota de crédito fiscal");
            }
            anular(factura);
            if (request.getObservaciones() != null && !request.getObservaciones().isBlank()) {
                factura.setObservaciones(request.getObservaciones());
            }
            return toReporte(factura);
        });
    }

    /**
     * Eliminación física. Un comprobante emitido (con CAE) nunca se borra: se anula.
     * Por eso solo se permite sobre borradores; los detalles se borran en cascada.
     */
    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = buscarParaActualizar(id);
        exigirBorrador(factura, "eliminar");
        facturaRepo.delete(factura);
    }

    // ======================= REGLAS INTERNAS =======================

    private void aplicarRequest(FacturaVenta factura, FacturaRequestDTO request) {
        factura.setPuntoVenta(puntoVentaRepo.findById(request.getPuntoVentaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("PuntoVenta", request.getPuntoVentaId())));
        factura.setCondicionIva(condicionIvaRepo.findById(request.getCondicionIvaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CondicionIva", request.getCondicionIvaId())));
        factura.setTipoMoneda(tipoMonedaRepo.findById(request.getTipoMonedaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("TipoMoneda", request.getTipoMonedaId())));
        factura.setCliente(request.getClienteId() == null ? null :
                clienteRepo.findById(request.getClienteId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.getClienteId())));
        factura.setFechaEmision(request.getFechaEmision() != null ? request.getFechaEmision() : LocalDate.now());
        factura.setObservaciones(request.getObservaciones());

        factura.limpiarDetalles();   // orphanRemoval elimina los renglones viejos en el UPDATE
        for (FacturaDetalleRequestDTO d : request.getDetalles()) {
            ListaPrecioArticulo lpa = listaPrecioArticuloRepo.findById(d.getListaPrecioArticuloId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("ListaPrecioArticulo", d.getListaPrecioArticuloId()));
            Articulo articulo = lpa.getArticulo();

            FacturaVentaDetalle detalle = new FacturaVentaDetalle();
            detalle.setListaPrecioArticulo(lpa);
            detalle.setDescripcion(d.getDescripcion() != null ? d.getDescripcion() : articulo.getDenominacion());
            detalle.setCantidad(d.getCantidad());
            detalle.setPrecioUnitario(lpa.getPrecioVenta());          // el precio lo fija el servidor
            detalle.setPorcentajeBonificacion(d.getPorcentajeBonificacion() != null ? d.getPorcentajeBonificacion() : 0.0);
            double alicuota = arcaProperties.condicionIvaEmisor() != null
                    && (arcaProperties.condicionIvaEmisor() == 4 || arcaProperties.condicionIvaEmisor() == 6)
                    ? 0 : articulo.getAlicuotaIva() != null ? articulo.getAlicuotaIva() : props.ivaPorDefecto();
            detalle.calcularImportes(alicuota);
            factura.agregarDetalle(detalle);
        }
        factura.recalcularImportes();
    }

    private void anular(FacturaVenta factura) {
        factura.setEstado(EstadoFactura.ANULADA);
        factura.setFechaAnulacion(LocalDate.now());
        factura.darDeBaja();
    }

    private void exigirBorrador(FacturaVenta factura, String accion) {
        if (factura.getEstado() != EstadoFactura.BORRADOR) {
            throw new ReglaNegocioException("Solo se puede " + accion + " una factura en estado BORRADOR " +
                    "(actual: " + factura.getEstado() + "). Un comprobante emitido se anula, no se borra.");
        }
        if ("ARCA_EN_CURSO".equals(factura.getResultadoAfip()) || "ARCA_INCIERTA".equals(factura.getResultadoAfip())) {
            throw new ReglaNegocioException("No se puede " + accion + " mientras la emisión de ARCA está pendiente de conciliación");
        }
    }

    private static EstadoFactura parsearEstado(String valor) {
        try {
            return EstadoFactura.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Estado inválido: " + valor + ". Valores: BORRADOR, EMITIDA, ANULADA");
        }
    }

    private FacturaVenta buscarConDetalles(Long id) {
        return facturaRepo.findConDetallesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
    }

    private FacturaVenta buscarParaActualizar(Long id) {
        return facturaRepo.findParaActualizarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
    }

    // ======================= MAPEO A DTOs =======================

    private static FacturaReporteDTO toReporte(FacturaVenta f) {
        FacturaReporteDTO reporte = new FacturaReporteDTO(
                f.getId(), f.getNumero(), f.getFechaEmision(),
                f.getCliente() != null ? f.getCliente().getDenominacion() : "Consumidor Final",
                f.getCondicionIva().getDenominacion(),
                f.getPuntoVenta().getDescripcion(),
                f.getImporteTotal(),
                f.getDetalles().size(),
                f.getEstado().name());
        reporte.setCodigoMoneda(f.getTipoMoneda().getCodigoAfip());
        reporte.setAutorizacionArca(ArcaEmisionService.APROBADA.equals(f.getResultadoAfip()));
        return reporte;
    }

    private static FacturaDetalladaDTO toDetallada(FacturaVenta f) {
        List<FacturaDetalleResponseDTO> detalles = f.getDetalles().stream().map(d -> new FacturaDetalleResponseDTO(
                d.getId(), d.getListaPrecioArticulo().getId(), d.getListaPrecioArticulo().getArticulo().getCodigo(),
                d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(), d.getPorcentajeBonificacion(),
                d.getImporteNeto(), d.getImporteIva(), d.getImporteSubtotal())).toList();
        return FacturaDetalladaDTO.builder()
                .id(f.getId()).numero(f.getNumero()).tipoComprobanteArca(f.getTipoComprobanteArca())
                .fechaEmision(f.getFechaEmision()).estado(f.getEstado().name())
                .clienteId(f.getCliente() != null ? f.getCliente().getId() : null)
                .clienteDenominacion(f.getCliente() != null ? f.getCliente().getDenominacion() : "Consumidor Final")
                .clienteCuitCuil(f.getCliente() != null ? f.getCliente().getCuitCuil() : null)
                .clienteDomicilio(domicilioCliente(f.getCliente()))
                .condicionIvaId(f.getCondicionIva().getId()).condicionIvaCodigoAfip(f.getCondicionIva().getCodigoAfip())
                .condicionIva(f.getCondicionIva().getDenominacion())
                .puntoVentaId(f.getPuntoVenta().getId()).puntoVentaNumero(f.getPuntoVenta().getNumero())
                .puntoVentaDescripcion(f.getPuntoVenta().getDescripcion())
                .tipoMonedaId(f.getTipoMoneda().getId()).codigoMoneda(f.getTipoMoneda().getCodigoAfip())
                .tipoMoneda(f.getTipoMoneda().getDenominacion())
                .importeTotal(f.getImporteTotal()).importeCobrado(f.getImporteCobrado()).importeSaldo(f.getImporteSaldo())
                .cae(f.getCae()).autorizacionArca(ArcaEmisionService.APROBADA.equals(f.getResultadoAfip()))
                .caeFechaVencimiento(f.getCaeFechaVencimiento()).fechaAnulacion(f.getFechaAnulacion())
                .observaciones(f.getObservaciones())
                .detalles(detalles)
                .build();
    }

    private static String domicilioCliente(Cliente cliente) {
        if (cliente == null || cliente.getDomicilio() == null) return null;
        Domicilio domicilio = cliente.getDomicilio();
        String calle = domicilio.getNombreCalle() == null ? "" : domicilio.getNombreCalle();
        String numero = domicilio.getNumeroCalle() == null ? "" : domicilio.getNumeroCalle();
        return (calle + " " + numero).trim();
    }
}
