package ar.edu.utn.frm.facturacion.service.arca;

import ar.edu.utn.frm.facturacion.config.ArcaProperties;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reserva el número en BD, consulta WSFEv1 y confirma solo un CAE aprobado. */
@Service
public class ArcaEmisionService {
    public static final String APROBADA = "ARCA_HOMOLOGACION_APROBADA";
    private static final String EN_CURSO = "ARCA_EN_CURSO";
    private static final String INCIERTA = "ARCA_INCIERTA";
    private static final String RECHAZADA = "ARCA_RECHAZADA";
    private static final List<String> OCUPA_NUMERO = List.of(EN_CURSO, INCIERTA, APROBADA);
    private static final List<String> PENDIENTES = List.of(EN_CURSO, INCIERTA);

    private final FacturaVentaRepository facturas;
    private final PuntoVentaRepository puntos;
    private final ArcaGateway gateway;
    private final ArcaProperties properties;
    private final TransactionTemplate transactions;

    public ArcaEmisionService(FacturaVentaRepository facturas, PuntoVentaRepository puntos,
                              ArcaGateway gateway, ArcaProperties properties,
                              PlatformTransactionManager manager) {
        this.facturas = facturas;
        this.puntos = puntos;
        this.gateway = gateway;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    public void emitir(Long id, String observaciones) {
        if (properties.condicionIvaEmisor() == null ||
                (properties.condicionIvaEmisor() != 1 && properties.condicionIvaEmisor() != 4 && properties.condicionIvaEmisor() != 6)) {
            throw new ReglaNegocioException("Configurá ARCA_EMISOR_CONDICION_IVA con la condición real de la CUIT emisora: 1, 4 o 6");
        }
        Vista vista = transactions.execute(status -> {
            FacturaVenta f = facturas.findConDetallesById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
            if (f.getEstado() == EstadoFactura.EMITIDA && APROBADA.equals(f.getResultadoAfip())) return new Vista(true, null);
            if (f.getEstado() != EstadoFactura.BORRADOR) throw new ReglaNegocioException("Solo se emite un BORRADOR o se recupera una emisión aprobada");
            ArcaComprobante c = ArcaFiscalMapper.desde(f, tipo(f));
            return new Vista(false, c);
        });
        if (vista.yaEmitida()) return; // doble clic: respuesta idempotente

        // Consulta remota fuera de toda transacción: no se bloquea BD durante la red.
        long ultimo;
        try { ultimo = gateway.ultimoAutorizado(vista.comprobante().puntoVenta(), vista.comprobante().tipo()); }
        catch (ArcaConexionException e) { throw new ReglaNegocioException("No se pudo consultar la numeración de ARCA; no se reservó ningún comprobante"); }
        Reserva reserva = transactions.execute(status -> reservar(id, vista.comprobante(), ultimo, observaciones));
        if (reserva.yaEmitida()) return;

        try {
            Optional<ArcaGateway.Autorizacion> existente = gateway.consultar(
                    reserva.comprobante().puntoVenta(), reserva.comprobante().tipo(), reserva.numero());
            if (existente.isPresent()) {
                ArcaGateway.Autorizacion autorizacion = existente.get();
                if (!coincide(reserva.comprobante(), autorizacion)) {
                    marcarIncierta(id, reserva.intentoId());
                    throw new ReglaNegocioException("ARCA ya tiene ese número con datos diferentes; se requiere revisión manual");
                }
                confirmar(id, reserva.numero(), autorizacion.cae(), autorizacion.vencimiento(), reserva.intentoId());
                return;
            }

            long ultimoActual = gateway.ultimoAutorizado(reserva.comprobante().puntoVenta(), reserva.comprobante().tipo());
            if (ultimoActual != reserva.numero() - 1) {
                marcarIncierta(id, reserva.intentoId());
                throw new ReglaNegocioException("La numeración de ARCA cambió; se requiere conciliación antes de reintentar");
            }
            ArcaGateway.Respuesta respuesta = gateway.autorizar(reserva.comprobante(), reserva.numero());
            if (respuesta.aprobada()) {
                if (respuesta.cae() == null || !respuesta.cae().matches("[0-9]{14}") || respuesta.vencimiento() == null) {
                    throw new ArcaConexionException("ARCA devolvió aprobación incompleta; consultar antes de reintentar");
                }
                confirmar(id, reserva.numero(), respuesta.cae(), respuesta.vencimiento(), reserva.intentoId());
            } else {
                rechazar(id, respuesta.mensaje(), reserva.intentoId());
                throw new ReglaNegocioException("ARCA rechazó la factura: " + mensajeSeguro(respuesta.mensaje()));
            }
        } catch (ArcaConexionException e) {
            marcarIncierta(id, reserva.intentoId());
            throw new ReglaNegocioException("No se confirmó la emisión con ARCA. El borrador conserva el número reservado; al reintentar se consultará el comprobante");
        }
    }

    private Reserva reservar(Long id, ArcaComprobante esperado, long ultimo, String observaciones) {
        FacturaVenta f = facturas.findParaActualizarById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
        PuntoVenta pv = puntos.findParaNumerarById(f.getPuntoVenta().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("PuntoVenta", f.getPuntoVenta().getId()));
        if (f.getEstado() == EstadoFactura.EMITIDA && APROBADA.equals(f.getResultadoAfip())) return new Reserva(true, 0, esperado, null);
        if (f.getEstado() != EstadoFactura.BORRADOR) throw new ReglaNegocioException("La factura ya no es un borrador");
        int tipo = tipo(f);
        if (pendiente(f.getResultadoAfip()) && f.getTipoComprobanteArca() != null
                && f.getTipoComprobanteArca() != tipo) {
            throw new ReglaNegocioException("Cambió el tipo de comprobante configurado; no se puede conciliar esta reserva");
        }
        for (FacturaVenta pendiente : facturas.pendientesArca(pv, tipo, PENDIENTES)) {
            if (!pendiente.getId().equals(id)) {
                throw new ReglaNegocioException("Hay otra emisión pendiente en este punto de venta; primero hay que conciliarla");
            }
        }
        ArcaComprobante actual = ArcaFiscalMapper.desde(f, tipo);
        if (!actual.equals(esperado)) throw new ReglaNegocioException("La factura cambió mientras se preparaba la emisión");
        if (EN_CURSO.equals(f.getResultadoAfip()) && f.getEmisionUltimoIntento() != null
                && f.getEmisionUltimoIntento().isAfter(Instant.now().minusSeconds(90))) {
            throw new ReglaNegocioException("La emisión ya está en curso; esperá el resultado antes de reintentar");
        }
        long numero;
        if (f.getNumero() != null && pendiente(f.getResultadoAfip())) {
            numero = f.getNumero();
        } else {
            long local = facturas.ultimoNumeroArcaLocal(pv, tipo, OCUPA_NUMERO);
            numero = Math.max(ultimo, local) + 1;
            if (facturas.existsByPuntoVentaAndTipoComprobanteArcaAndNumero(pv, tipo, numero)) {
                throw new ReglaNegocioException("Un registro legado ocupa el próximo número fiscal; hay que migrar la base antes de emitir");
            }
            f.setNumero(numero);
        }
        f.setResultadoAfip(EN_CURSO);
        String intentoId = UUID.randomUUID().toString();
        f.setEmisionIntentoId(intentoId);
        f.setTipoComprobanteArca(tipo);
        f.setEmisionUltimoIntento(Instant.now());
        f.setMotivoRechazo(null);
        if (observaciones != null && !observaciones.isBlank()) f.setObservaciones(observaciones);
        facturas.saveAndFlush(f); // la reserva existe antes de cualquier FECAESolicitar
        return new Reserva(false, numero, actual, intentoId);
    }

    private void confirmar(Long id, long numero, String cae, java.time.LocalDate vencimiento, String intentoId) {
        transactions.executeWithoutResult(status -> {
            FacturaVenta f = facturas.findParaActualizarById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
            if (f.getEstado() == EstadoFactura.EMITIDA && APROBADA.equals(f.getResultadoAfip())
                    && numero == f.getNumero() && cae.equals(f.getCae())) return;
            if (!intentoId.equals(f.getEmisionIntentoId())) throw new ReglaNegocioException("Otro intento está conciliando esta factura");
            if (f.getNumero() == null || f.getNumero() != numero) throw new ReglaNegocioException("Cambió la reserva del comprobante");
            f.setCae(cae);
            f.setCaeFechaVencimiento(vencimiento);
            f.setResultadoAfip(APROBADA);
            f.setMotivoRechazo(null);
            f.setEstado(EstadoFactura.EMITIDA);
        });
    }

    private void rechazar(Long id, String motivo, String intentoId) {
        transactions.executeWithoutResult(status -> {
            FacturaVenta f = facturas.findParaActualizarById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
            if (f.getEstado() != EstadoFactura.BORRADOR || !intentoId.equals(f.getEmisionIntentoId())) return;
            f.setResultadoAfip(RECHAZADA);
            f.setMotivoRechazo(mensajeSeguro(motivo));
            f.setNumero(null);
            f.setCae(null);
            f.setCaeFechaVencimiento(null);
        });
    }

    private void marcarIncierta(Long id, String intentoId) {
        transactions.executeWithoutResult(status -> {
            FacturaVenta f = facturas.findParaActualizarById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
            if (f.getEstado() == EstadoFactura.BORRADOR && intentoId.equals(f.getEmisionIntentoId())) f.setResultadoAfip(INCIERTA);
        });
    }

    private static boolean coincide(ArcaComprobante c, ArcaGateway.Autorizacion a) {
        return a.cae() != null && a.cae().matches("[0-9]{14}") && a.vencimiento() != null
                && c.documentoTipo() == a.documentoTipo() && c.documentoNumero() == a.documentoNumero()
                && c.fecha().equals(a.fecha()) && c.importeTotal().compareTo(a.total()) == 0
                && c.importeNeto().compareTo(a.neto()) == 0 && c.importeIva().compareTo(a.iva()) == 0
                && "PES".equals(a.moneda());
    }

    private static String mensajeSeguro(String mensaje) {
        return mensaje == null || mensaje.isBlank() ? "sin detalle" : mensaje.substring(0, Math.min(mensaje.length(), 500));
    }

    private int tipo(FacturaVenta factura) {
        return ArcaFiscalMapper.tipoPara(properties.condicionIvaEmisor(), factura.getCondicionIva().getCodigoAfip());
    }

    private record Vista(boolean yaEmitida, ArcaComprobante comprobante) {}
    private static boolean pendiente(String resultado) { return EN_CURSO.equals(resultado) || INCIERTA.equals(resultado); }

    private record Reserva(boolean yaEmitida, long numero, ArcaComprobante comprobante, String intentoId) {}
}
