package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioRepository;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatalogoServiceTest {

    @Autowired CatalogoService catalogoService;
    @Autowired DatosDePrueba datos;
    @Autowired ListaPrecioRepository listaPrecioRepo;
    @Autowired ListaPrecioArticuloRepository listaPrecioArticuloRepo;

    @BeforeEach
    void setUp() { datos.limpiarYCargar(); }

    @AfterEach
    void tearDown() { UsuarioActual.clear(); }

    @Test
    void listaPrecioArticulosFiltraPorListaYExponeElPrecioCorrecto() {
        ListaPrecioArticulo precioMayorista = crearPrecioMayorista();
        ListaPrecio mayorista = precioMayorista.getListaPrecio();

        List<OpcionDTO> opciones = catalogoService.listaPrecioArticulos(mayorista.getId());

        assertThat(opciones).singleElement().satisfies(opcion -> {
            assertThat(opcion.getId()).isEqualTo(precioMayorista.getId());
            assertThat(opcion.getValor()).isEqualTo(800);
            assertThat(opcion.getListaPrecioId()).isEqualTo(mayorista.getId());
        });
    }

    @Test
    void listaPrecioArticulosSinFiltroConservaTodosLosPrecios() {
        ListaPrecioArticulo precioMayorista = crearPrecioMayorista();
        ListaPrecio mayorista = precioMayorista.getListaPrecio();

        List<OpcionDTO> opciones = catalogoService.listaPrecioArticulos();

        assertThat(opciones).extracting(OpcionDTO::getId).containsExactlyInAnyOrder(
                datos.getLpaNotebook().getId(), datos.getLpaLapicera().getId(), precioMayorista.getId());
        assertThat(opciones).filteredOn(o -> o.getId().equals(precioMayorista.getId()))
                .singleElement().extracting(OpcionDTO::getListaPrecioId).isEqualTo(mayorista.getId());
    }

    private ListaPrecioArticulo crearPrecioMayorista() {
        ListaPrecio mayorista = listaPrecioRepo.save(
                ListaPrecio.builder().codigo("MAY").denominacion("Mayorista").build());
        return listaPrecioArticuloRepo.save(ListaPrecioArticulo.builder()
                .listaPrecio(mayorista).articulo(datos.getLpaNotebook().getArticulo())
                .precioVenta(800).build());
    }
}
