package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.model.*;
import ar.edu.utn.frm.facturacion.repository.*;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AggregateAuditTest {
    @Autowired DatosDePrueba datos;
    @Autowired FacturaService service;
    @Autowired FacturaVentaRepository facturas;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach void preparar() { datos.limpiarYCargar(); }
    @AfterEach void cerrar() { UsuarioActual.clear(); }

    private FacturaRequestDTO request() {
        return FacturaRequestDTO.builder().puntoVentaId(datos.getPuntoVenta().getId())
            .condicionIvaId(datos.getCondicionIva().getId()).tipoMonedaId(datos.getMoneda().getId())
            .detalles(List.of(FacturaDetalleRequestDTO.builder()
                .listaPrecioArticuloId(datos.getLpaNotebook().getId()).cantidad(1.0).descripcion("antes").build())).build();
    }

    private record Audit(LocalDateTime fecha, Long usuario, Long creador) {}
    private Audit audit(Long id) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            FacturaVenta factura = facturas.findById(id).orElseThrow();
            return new Audit(factura.getFechaModificacion(), factura.getUsuarioModificacion().getId(),
                factura.getUsuarioCarga().getId());
        });
    }

    @Test void editarSoloDescripcionActualizaAuditoriaDeCabecera() {
        FacturaRequestDTO request = request();
        Long id = service.crear(request).getId();
        Audit antes = audit(id);
        Usuario editor = usuarios.save(Usuario.builder().usuario("editor").clave("x").nombre("Editor").apellido("Test").build());
        UsuarioActual.set(editor);
        request.getDetalles().get(0).setDescripcion("despues");
        service.actualizar(id, request);
        Audit despues = audit(id);
        assertThat(service.obtener(id).getDetalles().get(0).getDescripcion()).isEqualTo("despues");
        assertThat(despues.usuario()).isEqualTo(editor.getId());
        assertThat(despues.fecha()).isAfter(antes.fecha());
        assertThat(despues.creador()).isEqualTo(antes.creador());
    }

    @Test void editarCabeceraYReenviarSinCambiosRegistraCadaEscritura() {
        FacturaRequestDTO request = request();
        Long id = service.crear(request).getId();
        Audit inicial = audit(id);
        Usuario editor = usuarios.save(Usuario.builder().usuario("editor").clave("x").nombre("Editor").apellido("Test").build());
        UsuarioActual.set(editor);
        request.setObservaciones("nueva observacion");
        service.actualizar(id, request);
        Audit cabecera = audit(id);
        assertThat(cabecera.usuario()).isEqualTo(editor.getId());
        assertThat(cabecera.fecha()).isAfter(inicial.fecha());
        UsuarioActual.set(datos.getSistema());
        service.actualizar(id, request);
        Audit reenvio = audit(id);
        assertThat(reenvio.usuario()).isEqualTo(datos.getSistema().getId());
        assertThat(reenvio.fecha()).isAfter(cabecera.fecha());
        assertThat(reenvio.creador()).isEqualTo(inicial.creador());
        assertThat(service.obtener(id).getObservaciones()).isEqualTo("nueva observacion");
    }
}
