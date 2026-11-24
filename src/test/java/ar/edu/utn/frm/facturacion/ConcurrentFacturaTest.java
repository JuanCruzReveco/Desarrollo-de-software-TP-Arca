package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentFacturaTest {
    @Autowired FacturaService service;
    @Autowired DatosDePrueba datos;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    ExecutorService executor;

    @BeforeEach void cargar() { datos.limpiarYCargar(); executor = Executors.newFixedThreadPool(6); }
    @AfterEach void cerrar() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        UsuarioActual.clear();
    }
    FacturaRequestDTO request() {
        return FacturaRequestDTO.builder().clienteId(datos.getCliente().getId()).puntoVentaId(datos.getPuntoVenta().getId())
            .condicionIvaId(datos.getCondicionIva().getId()).tipoMonedaId(datos.getMoneda().getId())
            .detalles(List.of(FacturaDetalleRequestDTO.builder()
                .listaPrecioArticuloId(datos.getLpaNotebook().getId()).cantidad(1.0).build())).build();
    }
    <T> Future<T> ejecutar(Callable<T> action, CountDownLatch started) {
        return executor.submit(() -> {
            UsuarioActual.set(datos.getSistema());
            try { started.countDown(); return action.call(); }
            finally { UsuarioActual.clear(); }
        });
    }
    @Test void emision_esperaReservaDelPuntoVenta() throws Exception {
        Long id = service.crear(request()).getId();
        List<Future<FacturaReporteDTO>> futures = new ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.find(PuntoVenta.class, datos.getPuntoVenta().getId(), LockModeType.PESSIMISTIC_WRITE);
            CountDownLatch started = new CountDownLatch(1);
            Future<FacturaReporteDTO> future = ejecutar(() -> service.cambiarEstado(id,
                new CambioEstadoRequestDTO("EMITIDA", null)), started);
            futures.add(future);
            assertThatCode(() -> assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()).doesNotThrowAnyException();
            assertThatThrownBy(() -> future.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        });
        assertThat(futures.get(0).get(10, TimeUnit.SECONDS).getNumeroFactura()).isEqualTo(1L);
    }
    @ParameterizedTest @ValueSource(ints = {2, 6})
    void emisionesSimultaneas_tienenNumerosDistintosYConsecutivos(int cantidad) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) ids.add(service.crear(request()).getId());
        CountDownLatch ready = new CountDownLatch(cantidad);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FacturaReporteDTO>> futures = new ArrayList<>();
        for (Long id : ids) futures.add(ejecutar(() -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.cambiarEstado(id, new CambioEstadoRequestDTO("EMITIDA", null));
        }, ready));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Long> numeros = new ArrayList<>();
        List<Long> paraReintentar = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try { numeros.add(futures.get(i).get(10, TimeUnit.SECONDS).getNumeroFactura()); }
            catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ReglaNegocioException.class);
                paraReintentar.add(ids.get(i));
            }
        }
        assertThat(numeros).isNotEmpty();
        for (Long id : paraReintentar) {
            numeros.add(service.cambiarEstado(id, new CambioEstadoRequestDTO("EMITIDA", null)).getNumeroFactura());
        }
        assertThat(numeros).doesNotHaveDuplicates().hasSize(cantidad).contains(1L, (long) cantidad);
    }
    @ParameterizedTest @ValueSource(strings = {"actualizar", "eliminar"})
    void escrituraConcurrente_noAlteraFacturaQueSeEstaEmitiendo(String operacion) throws Exception {
        Long id = service.crear(request()).getId();
        List<Future<Void>> futures = new ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.cambiarEstado(id, new CambioEstadoRequestDTO("EMITIDA", null));
            em.flush();
            em.find(FacturaVenta.class, id, LockModeType.PESSIMISTIC_WRITE);
            CountDownLatch started = new CountDownLatch(1);
            Future<Void> future = ejecutar(() -> {
                if (operacion.equals("actualizar")) service.actualizar(id, request());
                else service.eliminar(id);
                return null;
            }, started);
            futures.add(future);
            assertThatCode(() -> assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()).doesNotThrowAnyException();
            assertThatThrownBy(() -> future.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        });
        assertThatThrownBy(() -> futures.get(0).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ReglaNegocioException.class);
        assertThat(service.obtener(id).getEstado()).isEqualTo("EMITIDA");
        assertThat(service.obtener(id).getDetalles()).hasSize(1);
    }
}
