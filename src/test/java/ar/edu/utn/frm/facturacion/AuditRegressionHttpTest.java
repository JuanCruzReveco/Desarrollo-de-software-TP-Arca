package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditRegressionHttpTest {
    @Autowired MockMvc mvc;
    @Autowired DatosDePrueba datos;
    @Autowired ObjectMapper mapper;

    @BeforeEach void preparar() { datos.limpiarYCargar(); }
    @AfterEach void cerrar() { UsuarioActual.clear(); }

    private String body(String descripcion) throws Exception {
        return """
            {"puntoVentaId":%d,"condicionIvaId":%d,"tipoMonedaId":%d,
             "detalles":[{"listaPrecioArticuloId":%d,"cantidad":1,"descripcion":%s}]}
            """.formatted(datos.getPuntoVenta().getId(), datos.getCondicionIva().getId(),
                datos.getMoneda().getId(), datos.getLpaNotebook().getId(), mapper.writeValueAsString(descripcion));
    }

    @Test void postDescripcionExcesivaDevuelve400SinPersistir() throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(body("x".repeat(256))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores['detalles[0].descripcion']").isNotEmpty());
        mvc.perform(get("/api/facturas")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void jsonTruncadoDevuelveProblemDetail() throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content("{\"detalles\":"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").isNotEmpty()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void filtroMontoNoFinitoDevuelve400() throws Exception {
        mvc.perform(get("/api/facturas").param("montoMinimo", "NaN"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void postYPutAceptan255YPutRechaza256SinCambiosParciales() throws Exception {
        String inicial = "a".repeat(255);
        String respuesta = mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(body(inicial)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(respuesta).get("id").asLong();
        mvc.perform(put("/api/facturas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("b".repeat(255))))
            .andExpect(status().isOk());
        mvc.perform(put("/api/facturas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("c".repeat(256))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores['detalles[0].descripcion']").isNotEmpty());
        mvc.perform(get("/api/facturas/{id}", id)).andExpect(jsonPath("$.detalles[0].descripcion").value("b".repeat(255)));
        mvc.perform(get("/api/facturas")).andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest @ValueSource(strings = {"", "{\"puntoVentaId\":\"no-es-un-id\"}"})
    void cuerpoVacioOTipoIncompatibleDevuelveProblemDetail(String contenido) throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(contenido))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Request inválido"))
            .andExpect(jsonPath("$.detail").value("El cuerpo debe contener JSON válido con los tipos de datos esperados"));
    }

    @ParameterizedTest @ValueSource(strings = {"Infinity", "-Infinity"})
    void reporteRechazaMontosInfinitos(String monto) throws Exception {
        mvc.perform(get("/api/facturas/reporte").param("formato", "txt").param("montoMinimo", monto))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/facturas", "/api/facturas/reporte"})
    void rechazaRangoDeFechasInvertido(String ruta) throws Exception {
        mvc.perform(get(ruta).param("fechaDesde", "2026-10-30").param("fechaHasta", "2026-10-29"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void rangoDeUnDiaYMontoFinitoSiguenAceptados() throws Exception {
        mvc.perform(get("/api/facturas").param("fechaDesde", "2026-10-30").param("fechaHasta", "2026-10-30")
            .param("montoMinimo", "0.71")).andExpect(status().isOk());
        mvc.perform(get("/api/facturas/reporte").param("formato", "txt").param("montoMinimo", "0"))
            .andExpect(status().isOk());
    }

    @Test void perdidaDeCentavosDevuelve409SinPersistir() throws Exception {
        String contenido = """
            {"puntoVentaId":%d,"condicionIvaId":%d,"tipoMonedaId":%d,
             "detalles":[{"listaPrecioArticuloId":%d,"cantidad":9500000000000},
                          {"listaPrecioArticuloId":%d,"cantidad":0.0064}]}
            """.formatted(datos.getPuntoVenta().getId(), datos.getCondicionIva().getId(), datos.getMoneda().getId(),
                datos.getLpaNotebook().getId(), datos.getLpaLapicera().getId());
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(contenido))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(
                "El importe excede la precisión monetaria soportada; se perderían centavos"));
        mvc.perform(get("/api/facturas")).andExpect(jsonPath("$.length()").value(0));
    }
}
