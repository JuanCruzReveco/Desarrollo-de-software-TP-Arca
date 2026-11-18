package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FacturaServiceTest {

    @Autowired FacturaService service;
    @Autowired DatosDePrueba datos;

    @BeforeEach
    void setUp() { datos.limpiarYCargar(); }

    @AfterEach
    void tearDown() { UsuarioActual.clear(); }

    private FacturaRequestDTO requestBase() {
        return FacturaRequestDTO.builder()
                .clienteId(datos.getCliente().getId())
                .condicionIvaId(datos.getCondicionIva().getId())
                .tipoMonedaId(datos.getMoneda().getId())
                .puntoVentaId(datos.getPuntoVenta().getId())
                .detalles(List.of(
                        // 2 x 1000 con 10% bonif = 1800 neto + 378 IVA = 2178
                        FacturaDetalleRequestDTO.builder().listaPrecioArticuloId(datos.getLpaNotebook().getId()).cantidad(2.0).porcentajeBonificacion(10.0).build(),
                        // 3 x 100 = 300 neto + 31.5 IVA = 331.5
                        FacturaDetalleRequestDTO.builder().listaPrecioArticuloId(datos.getLpaLapicera().getId()).cantidad(3.0).build()))
                .build();
    }

    @Test
    void crear_calculaImportesYPersisteDetallesEnCascada() {
        FacturaReporteDTO creada = service.crear(requestBase());

        assertThat(creada.getId()).isNotNull();
        assertThat(creada.getEstado()).isEqualTo("BORRADOR");
        assertThat(creada.getNumeroFactura()).isNull();           // el número se asigna al emitir
        assertThat(creada.getCantidadItems()).isEqualTo(2);
        assertThat(creada.getImporteTotal()).isEqualTo(2509.5);
        assertThat(creada.getClienteDenominacion()).isEqualTo("Empresa Tech S.A.");

        FacturaDetalladaDTO detalle = service.obtener(creada.getId());
        assertThat(detalle.getDetalles()).hasSize(2);
        assertThat(detalle.getDetalles().get(0).getImporteNeto()).isEqualTo(1800.0);
        assertThat(detalle.getDetalles().get(0).getImporteIva()).isEqualTo(378.0);
        assertThat(detalle.getDetalles().get(1).getImporteIva()).isEqualTo(31.5);
    }

    @Test
    void crear_sinCliente_esConsumidorFinal() {
        FacturaRequestDTO req = requestBase();
        req.setClienteId(null);
        assertThat(service.crear(req).getClienteDenominacion()).isEqualTo("Consumidor Final");
    }

    @Test
    void crear_conIdInexistente_lanza404() {
        FacturaRequestDTO req = requestBase();
        req.setPuntoVentaId(9999L);
        assertThatThrownBy(() -> service.crear(req)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void emitir_asignaNumeroCorrelativoYCae() {
        Long id1 = service.crear(requestBase()).getId();
        Long id2 = service.crear(requestBase()).getId();

        FacturaReporteDTO e1 = service.cambiarEstado(id1, new CambioEstadoRequestDTO("EMITIDA", null));
        FacturaReporteDTO e2 = service.cambiarEstado(id2, new CambioEstadoRequestDTO("emitida", null));

        assertThat(e1.getNumeroFactura()).isEqualTo(1L);
        assertThat(e2.getNumeroFactura()).isEqualTo(2L);
        assertThat(service.obtener(id1).getCae()).hasSize(14);
    }

    @Test
    void anular_soloDesdeEmitida_yNoSePuedeBorrar() {
        Long id = service.crear(requestBase()).getId();

        assertThatThrownBy(() -> service.cambiarEstado(id, new CambioEstadoRequestDTO("ANULADA", null)))
                .isInstanceOf(ReglaNegocioException.class);     // BORRADOR -> ANULADA no es válido

        service.cambiarEstado(id, new CambioEstadoRequestDTO("EMITIDA", null));
        FacturaReporteDTO anulada = service.cambiarEstado(id, new CambioEstadoRequestDTO("ANULADA", "error de carga"));
        assertThat(anulada.getEstado()).isEqualTo("ANULADA");
        assertThat(service.obtener(id).getFechaAnulacion()).isNotNull();

        assertThatThrownBy(() -> service.eliminar(id)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void actualizar_reemplazaDetallesYRecalcula() {
        Long id = service.crear(requestBase()).getId();
        FacturaRequestDTO req = requestBase();
        req.setDetalles(List.of(FacturaDetalleRequestDTO.builder()
                .listaPrecioArticuloId(datos.getLpaLapicera().getId()).cantidad(1.0).build()));

        FacturaReporteDTO actualizada = service.actualizar(id, req);
        assertThat(actualizada.getCantidadItems()).isEqualTo(1);
        assertThat(actualizada.getImporteTotal()).isEqualTo(110.5);
    }

    @Test
    void eliminar_borrador_borraEnCascada() {
        Long id = service.crear(requestBase()).getId();
        service.eliminar(id);
        assertThatThrownBy(() -> service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void reporte_filtraPorEstadoYMonto() {
        Long id = service.crear(requestBase()).getId();
        service.cambiarEstado(id, new CambioEstadoRequestDTO("EMITIDA", null));
        service.crear(requestBase());   // borrador

        assertThat(service.buscarFacturasFiltradas(FacturaFiltrosDTO.builder().build())).hasSize(2);
        assertThat(service.buscarFacturasFiltradas(FacturaFiltrosDTO.builder().estado("EMITIDA").build())).hasSize(1);
        assertThat(service.buscarFacturasFiltradas(FacturaFiltrosDTO.builder().montoMinimo(99999.0).build())).isEmpty();
    }
}

