package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoRestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired DatosDePrueba datos;
    @Autowired ListaPrecioRepository listaPrecioRepo;
    @Autowired ListaPrecioArticuloRepository listaPrecioArticuloRepo;

    @BeforeEach
    void setUp() { datos.limpiarYCargar(); }

    @AfterEach
    void tearDown() { UsuarioActual.clear(); }

    @Test
    void exponeListasDePrecioParaSeleccionar() throws Exception {
        Long listaId = listaPrecioRepo.findAll().get(0).getId();

        mvc.perform(get("/api/catalogos/listas-precio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(listaId))
                .andExpect(jsonPath("$[0].etiqueta").value("Minorista"));
    }

    @Test
    void filtraArticulosPorListaDePrecio() throws Exception {
        ListaPrecioArticulo precioMayorista = crearPrecioMayorista();
        ListaPrecio mayorista = precioMayorista.getListaPrecio();

        mvc.perform(get("/api/catalogos/lista-precio-articulos")
                        .param("listaPrecioId", mayorista.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(precioMayorista.getId()))
                .andExpect(jsonPath("$[0].listaPrecioId").value(mayorista.getId()))
                .andExpect(jsonPath("$[0].valor").value(800));
    }

    @Test
    void exponeTodasLasListasDisponibles() throws Exception {
        listaPrecioRepo.save(ListaPrecio.builder().codigo("MAY").denominacion("Mayorista").build());

        mvc.perform(get("/api/catalogos/listas-precio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].etiqueta", contains("Mayorista", "Minorista")));
    }

    @Test
    void sinFiltroConservaArticulosDeTodasLasListas() throws Exception {
        ListaPrecioArticulo precioMayorista = crearPrecioMayorista();

        mvc.perform(get("/api/catalogos/lista-precio-articulos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        datos.getLpaNotebook().getId().intValue(),
                        datos.getLpaLapicera().getId().intValue(),
                        precioMayorista.getId().intValue())));
    }

    @Test
    void listaInexistenteNoMuestraArticulos() throws Exception {
        mvc.perform(get("/api/catalogos/lista-precio-articulos")
                        .param("listaPrecioId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private ListaPrecioArticulo crearPrecioMayorista() {
        ListaPrecio mayorista = listaPrecioRepo.save(
                ListaPrecio.builder().codigo("MAY").denominacion("Mayorista").build());
        return listaPrecioArticuloRepo.save(ListaPrecioArticulo.builder()
                .listaPrecio(mayorista).articulo(datos.getLpaNotebook().getArticulo())
                .precioVenta(800).build());
    }
}
