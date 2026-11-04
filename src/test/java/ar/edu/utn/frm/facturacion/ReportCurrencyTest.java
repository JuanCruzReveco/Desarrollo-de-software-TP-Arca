package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.*;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReportCurrencyTest {
    @Autowired FacturaService facturas;
    @Autowired CatalogoService catalogos;
    @Autowired TipoMonedaRepository monedas;
    @Autowired DatosDePrueba datos;
    @Autowired ObjectMapper json;

    @BeforeEach
    void preparar() { datos.limpiarYCargar(); }

    @AfterEach
    void limpiarUsuario() { UsuarioActual.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"PES", "DOL"})
    void reporteYCatalogoConservanIdentidadDeLaMoneda(String codigo) {
        TipoMoneda moneda = "PES".equals(codigo) ? datos.getMoneda() : monedas.save(TipoMoneda.builder()
                .codigoAfip(codigo).denominacion("Dólar").simbolo("$").build());
        FacturaReporteDTO creada = facturas.crear(FacturaRequestDTO.builder()
                .puntoVentaId(datos.getPuntoVenta().getId()).condicionIvaId(datos.getCondicionIva().getId())
                .tipoMonedaId(moneda.getId())
                .detalles(List.of(FacturaDetalleRequestDTO.builder()
                        .listaPrecioArticuloId(datos.getLpaNotebook().getId()).cantidad(1.0).build())).build());

        assertThat(json.valueToTree(creada).path("codigoMoneda").asText()).isEqualTo(codigo);
        FacturaReporteDTO listada = facturas.buscarFacturasFiltradas(FacturaFiltrosDTO.builder().build()).get(0);
        assertThat(json.valueToTree(listada).path("codigoMoneda").asText()).isEqualTo(codigo);
        OpcionDTO opcion = catalogos.tiposMoneda().stream().filter(o -> o.getId().equals(moneda.getId()))
                .findFirst().orElseThrow();
        assertThat(json.valueToTree(opcion).path("codigoMoneda").asText()).isEqualTo(codigo);
    }
}
