package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
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
class ValidationFacturaTest {
    @Autowired MockMvc mvc;
    @Autowired DatosDePrueba datos;
    @BeforeEach void cargar() { datos.limpiarYCargar(); }
    @AfterEach void cerrar() { UsuarioActual.clear(); }
    @ParameterizedTest @ValueSource(strings = {"[null]", "[null,null]"})
    void post_rechazaRenglonesNulosCon400(String detalles) throws Exception {
        String body = """
            {"puntoVentaId":%d,"condicionIvaId":%d,"tipoMonedaId":%d,"detalles":%s}
            """.formatted(datos.getPuntoVenta().getId(), datos.getCondicionIva().getId(),
                datos.getMoneda().getId(), detalles);
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isNotEmpty());
        mvc.perform(get("/api/facturas")).andExpect(jsonPath("$.length()").value(0));
    }
}
