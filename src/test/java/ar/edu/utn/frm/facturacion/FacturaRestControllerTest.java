package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FacturaRestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired DatosDePrueba datos;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { datos.limpiarYCargar(); }

    @AfterEach
    void tearDown() { UsuarioActual.clear(); }

    private String bodyFactura() {
        return """
            {
              "clienteId": %d, "condicionIvaId": %d, "puntoVentaId": %d, "tipoMonedaId": %d,
              "observaciones": "desde test",
              "detalles": [ { "listaPrecioArticuloId": %d, "cantidad": 2, "porcentajeBonificacion": 0 } ]
            }
            """.formatted(datos.getCliente().getId(), datos.getCondicionIva().getId(), datos.getPuntoVenta().getId(),
                          datos.getMoneda().getId(), datos.getLpaNotebook().getId());
    }

    @Test
    void cicloCompleto_post_get_patch_delete() throws Exception {
        MvcResult post = mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(bodyFactura()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.importeTotal").value(2420.0))
                .andExpect(jsonPath("$.cantidadItems").value(1))
                .andReturn();
        String location = post.getResponse().getHeader("Location");

        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles", hasSize(1)))
                .andExpect(jsonPath("$.clienteDenominacion").value("Cliente de prueba"));

        mvc.perform(get("/api/facturas").param("estado", "BORRADOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fechaEmision", matchesPattern("\\d{4}-\\d{2}-\\d{2}")));

        mvc.perform(patch(location + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EMITIDA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroFactura").value(1));

        // Emitida: no se puede borrar -> 409
        mvc.perform(delete(location)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Regla de negocio violada"));

        mvc.perform(patch(location + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ANULADA\",\"observaciones\":\"error\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("nota de crédito")));
    }

    @Test
    void delete_borrador_devuelve204_yLuego404() throws Exception {
        String location = mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(bodyFactura()))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void post_invalido_devuelve400ConErrores() throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.puntoVentaId").exists())
                .andExpect(jsonPath("$.errores.detalles").exists());
    }

    @Test
    void reportes_descargables() throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(bodyFactura()));
        mvc.perform(get("/api/facturas/reporte").param("formato", "pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/facturas/reporte").param("formato", "xlsx"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("reporte-facturas.xlsx")));
        mvc.perform(get("/api/facturas/reporte").param("formato", "txt"))
                .andExpect(status().isOk()).andExpect(content().string(startsWith("N°\tFecha")));
    }

    @Test
    void catalogos_disponibles() throws Exception {
        mvc.perform(get("/api/catalogos/lista-precio-articulos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].valor").isNumber());
    }

    @Test
    void pdfDeFacturaExigeCaeYConservaElAutorizado() throws Exception {
        String location = mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(bodyFactura()))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location + "/pdf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("CAE")));

        mvc.perform(patch(location + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EMITIDA\"}"))
                .andExpect(status().isOk());
        JsonNode emitida = objectMapper.readTree(mvc.perform(get(location)).andReturn().getResponse().getContentAsString());

        byte[] pdf = mvc.perform(get(location + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PdfReader reader = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(texto).contains(emitida.get("cae").asText(), "Cliente de prueba", "30-00000000-7",
                    "Calle 1", "00001-00000001");
            assertThat(texto).contains("Fecha de emisión", "Vencimiento CAE", "Página 1 de 1",
                    LocalDate.parse(emitida.get("caeFechaVencimiento").asText())
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }

        mvc.perform(patch(location + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ANULADA\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get(location + "/pdf")).andExpect(status().isOk());
    }

    @Test
    void pdfDeFacturaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/facturas/999999/pdf")).andExpect(status().isNotFound());
    }
}

