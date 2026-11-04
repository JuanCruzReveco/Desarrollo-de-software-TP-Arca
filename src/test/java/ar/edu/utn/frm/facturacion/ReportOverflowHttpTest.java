package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportOverflowHttpTest {
    @Autowired MockMvc mvc;
    @Autowired DatosDePrueba datos;

    @BeforeEach
    void preparar() { datos.limpiarYCargar(); }

    @AfterEach
    void limpiarUsuario() { UsuarioActual.clear(); }

    @Test
    void desbordeMonetarioDevuelveConflictoSinPersistirFactura() throws Exception {
        String body = """
                {"puntoVentaId":%d,"condicionIvaId":%d,"tipoMonedaId":%d,
                 "detalles":[{"listaPrecioArticuloId":%d,"cantidad":1e308}]}
                """.formatted(datos.getPuntoVenta().getId(), datos.getCondicionIva().getId(),
                datos.getMoneda().getId(), datos.getLpaNotebook().getId());
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/facturas")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
