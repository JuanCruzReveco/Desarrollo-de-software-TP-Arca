package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de persistencia de la Etapa 1: agregados, claves foráneas, cascadas, eliminación de
 * huérfanos y restricciones de unicidad y nulidad, con datos que realmente las violan.
 *
 * Cada comprobación de lectura se hace en una transacción nueva y después de limpiar el contexto
 * de persistencia, para leer lo que quedó en la base y no lo que está en memoria.
 */
@SpringBootTest
@ActiveProfiles("test")
class PersistenciaModeloTest {

    @Autowired DatosDePrueba datos;
    @Autowired FacturaVentaRepository facturas;
    @Autowired PuntoVentaRepository puntosVenta;
    @Autowired ListaPrecioArticuloRepository listaPrecioArticulos;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager em;

    private TransactionTemplate tx;

    @BeforeEach
    void preparar() {
        datos.limpiarYCargar();
        tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cerrar() {
        UsuarioActual.clear();
    }

    // ---------- Fixtures ----------

    private FacturaVenta nuevaFactura(Cliente cliente) {
        FacturaVenta f = new FacturaVenta();
        f.setEstado(EstadoFactura.BORRADOR);
        f.setFechaEmision(LocalDate.of(2026, 11, 4));
        f.setCliente(cliente);
        f.setCondicionIva(datos.getCondicionIva());
        f.setTipoMoneda(datos.getMoneda());
        f.setPuntoVenta(datos.getPuntoVenta());
        return f;
    }

    private FacturaVentaDetalle detalle(ListaPrecioArticulo lpa, double cantidad, double alicuota) {
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setListaPrecioArticulo(lpa);
        d.setDescripcion(lpa.getArticulo().getDenominacion());
        d.setCantidad(cantidad);
        d.setPrecioUnitario(lpa.getPrecioVenta());
        d.calcularImportes(alicuota);
        return d;
    }

    /** Factura con dos renglones: notebook x2 (21 %) y lapicera x3 (10,5 %). Total 2751,5. */
    private Long guardarFacturaConDosDetalles(Cliente cliente) {
        return tx.execute(status -> {
            FacturaVenta f = nuevaFactura(cliente);
            f.agregarDetalle(detalle(datos.getLpaNotebook(), 2, 21.0));
            f.agregarDetalle(detalle(datos.getLpaLapicera(), 3, 10.5));
            f.recalcularImportes();
            em.persist(f);
            em.flush();
            em.clear();   // lo que se lee después viene de la base, no de la memoria
            return f.getId();
        });
    }

    private long contar(String jpql, Object... parametros) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i < parametros.length; i++) q.setParameter(i + 1, parametros[i]);
        return q.getSingleResult();
    }

    // ---------- Agregado FacturaVenta + detalles ----------

    @Test
    void facturaConDosDetallesSeGuardaEnCascadaYSeRecuperaDesdeLaBase() {
        Long id = guardarFacturaConDosDetalles(null);

        tx.executeWithoutResult(status -> {
            FacturaVenta f = em.find(FacturaVenta.class, id);
            assertThat(f.getDetalles()).hasSize(2);
            assertThat(f.getImporteTotal()).isEqualTo(2751.5);
            assertThat(f.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
            assertThat(f.getNumero()).as("un borrador no tiene número").isNull();
            // relación inversa: cada renglón apunta a su cabecera
            assertThat(f.getDetalles()).allSatisfy(d -> assertThat(d.getFactura().getId()).isEqualTo(id));
        });
    }

    @Test
    void laClaveForaneaEstaEnElDetalleYNoHayTablaIntermedia() {
        Long id = guardarFacturaConDosDetalles(null);

        tx.executeWithoutResult(status -> {
            Number enDetalle = (Number) em.createNativeQuery(
                    "select count(*) from factura_venta_detalle where factura_id = ?1")
                    .setParameter(1, id).getSingleResult();
            assertThat(enDetalle.longValue()).isEqualTo(2);

            // mappedBy = "factura": si faltara, Hibernate crearía una tabla intermedia
            Number tablasIntermedias = (Number) em.createNativeQuery(
                    "select count(*) from information_schema.tables "
                            + "where upper(table_name) like 'FACTURA_VENTA%DETALLES'")
                    .getSingleResult();
            assertThat(tablasIntermedias.longValue()).isZero();
        });
    }

    @Test
    void elClienteEsOpcionalYConsumidorFinalSeGuardaConClienteNulo() {
        Long sinCliente = guardarFacturaConDosDetalles(null);
        Long conCliente = guardarFacturaConDosDetalles(datos.getCliente());

        tx.executeWithoutResult(status -> {
            assertThat(em.find(FacturaVenta.class, sinCliente).getCliente()).isNull();
            assertThat(em.find(FacturaVenta.class, conCliente).getCliente().getId())
                    .isEqualTo(datos.getCliente().getId());
        });
    }

    @Test
    void eliminarUnBorradorBorraSusDetallesPeroConservaLosCatalogos() {
        Long id = guardarFacturaConDosDetalles(datos.getCliente());

        tx.executeWithoutResult(status -> em.remove(em.find(FacturaVenta.class, id)));

        tx.executeWithoutResult(status -> {
            assertThat(em.find(FacturaVenta.class, id)).isNull();
            assertThat(contar("select count(d) from FacturaVentaDetalle d")).as("detalles borrados en cascada").isZero();
            // los catálogos tienen existencia independiente: no se borran con la factura
            assertThat(em.find(Cliente.class, datos.getCliente().getId())).isNotNull();
            assertThat(em.find(PuntoVenta.class, datos.getPuntoVenta().getId())).isNotNull();
            assertThat(em.find(TipoMoneda.class, datos.getMoneda().getId())).isNotNull();
            assertThat(em.find(CondicionIva.class, datos.getCondicionIva().getId())).isNotNull();
            assertThat(em.find(ListaPrecioArticulo.class, datos.getLpaNotebook().getId())).isNotNull();
            assertThat(em.find(ListaPrecioArticulo.class, datos.getLpaLapicera().getId())).isNotNull();
        });
    }

    @Test
    void quitarUnDetalleDeLaColeccionLoBorraDeLaBaseConOrphanRemoval() {
        Long id = guardarFacturaConDosDetalles(null);

        tx.executeWithoutResult(status -> {
            FacturaVenta f = em.find(FacturaVenta.class, id);
            f.getDetalles().remove(0);
            em.flush();
        });

        tx.executeWithoutResult(status ->
                assertThat(contar("select count(d) from FacturaVentaDetalle d where d.factura.id = ?1", id)).isEqualTo(1));
    }

    @Test
    void vaciarLaColeccionGestionadaBorraTodosLosRenglonesSinReemplazarLaInstancia() {
        Long id = guardarFacturaConDosDetalles(null);

        tx.executeWithoutResult(status -> {
            FacturaVenta f = em.find(FacturaVenta.class, id);
            var coleccion = f.getDetalles();
            f.limpiarDetalles();
            assertThat(f.getDetalles()).as("misma instancia de la colección").isSameAs(coleccion);
            em.flush();
        });

        tx.executeWithoutResult(status ->
                assertThat(contar("select count(d) from FacturaVentaDetalle d where d.factura.id = ?1", id)).isZero());
    }

    // ---------- Restricciones: datos que realmente las violan ----------

    @Test
    void noSePuedeRepetirElNumeroDeUnPuntoDeVenta() {
        PuntoVenta repetido = PuntoVenta.builder().numero(datos.getPuntoVenta().getNumero()).descripcion("repetido").build();
        assertThatThrownBy(() -> puntosVenta.saveAndFlush(repetido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noSePuedeRepetirElNombreDeUsuario() {
        Usuario repetido = Usuario.builder().usuario("sistema").clave("x").nombre("Otro").apellido("Usuario").build();
        assertThatThrownBy(() -> usuarios.saveAndFlush(repetido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noSePuedeRepetirLaCombinacionListaArticulo() {
        ListaPrecioArticulo original = datos.getLpaNotebook();
        ListaPrecioArticulo repetido = ListaPrecioArticulo.builder()
                .listaPrecio(original.getListaPrecio()).articulo(original.getArticulo()).precioVenta(5).build();
        assertThatThrownBy(() -> listaPrecioArticulos.saveAndFlush(repetido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noSePuedeRepetirNumeroEnMismaSerieFiscal() {
        FacturaVenta primera = nuevaFactura(null);
        primera.setNumero(1L);
        primera.setTipoComprobanteArca(1);
        facturas.saveAndFlush(primera);

        FacturaVenta segunda = nuevaFactura(null);
        segunda.setNumero(1L);
        segunda.setTipoComprobanteArca(1);
        assertThatThrownBy(() -> facturas.saveAndFlush(segunda))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tiposDistintosTienenSeriesNumericasIndependientes() {
        FacturaVenta primera = nuevaFactura(null);
        primera.setNumero(1L);
        primera.setTipoComprobanteArca(1);
        facturas.saveAndFlush(primera);
        FacturaVenta segunda = nuevaFactura(null);
        segunda.setNumero(1L);
        segunda.setTipoComprobanteArca(6);
        facturas.saveAndFlush(segunda);
        assertThat(facturas.count()).isEqualTo(2);
    }

    @Test
    void variosBorradoresConNumeroNuloConviven() {
        // La restricción fiscal no impide varios borradores: el número nulo no compite.
        facturas.saveAndFlush(nuevaFactura(null));
        facturas.saveAndFlush(nuevaFactura(null));
        assertThat(facturas.count()).isEqualTo(2);
    }

    @Test
    void unaFacturaSinEstadoNoSePersiste() {
        FacturaVenta sinEstado = nuevaFactura(null);
        sinEstado.setEstado(null);
        assertThatThrownBy(() -> facturas.saveAndFlush(sinEstado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaFacturaSinPuntoDeVentaNoSePersiste() {
        FacturaVenta sinPuntoVenta = nuevaFactura(null);
        sinPuntoVenta.setPuntoVenta(null);
        assertThatThrownBy(() -> facturas.saveAndFlush(sinPuntoVenta))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elEstadoSeGuardaComoTextoYNoComoPosicion() {
        Long id = guardarFacturaConDosDetalles(null);

        tx.executeWithoutResult(status -> {
            Object estado = em.createNativeQuery("select estado from factura_venta where id = ?1")
                    .setParameter(1, id).getSingleResult();
            assertThat(estado).isEqualTo("BORRADOR");
        });
    }
}
