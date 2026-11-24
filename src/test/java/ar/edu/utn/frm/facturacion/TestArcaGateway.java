package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.service.arca.ArcaComprobante;
import ar.edu.utn.frm.facturacion.service.arca.ArcaEmisionService;
import ar.edu.utn.frm.facturacion.service.arca.ArcaGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** Doble de prueba: nunca se incluye en el binario principal ni contacta ARCA. */
@Component
@Primary
@Profile("test")
public class TestArcaGateway implements ArcaGateway {
    private final FacturaVentaRepository facturas;
    public TestArcaGateway(FacturaVentaRepository facturas) { this.facturas = facturas; }

    @Override public long ultimoAutorizado(int puntoVenta, int tipo) {
        return facturas.ultimoConfirmadoArca(puntoVenta, tipo, ArcaEmisionService.APROBADA);
    }
    @Override public Optional<Autorizacion> consultar(int puntoVenta, int tipo, long numero) { return Optional.empty(); }
    @Override public Respuesta autorizar(ArcaComprobante comprobante, long numero) {
        return new Respuesta(true, String.format("%014d", numero), LocalDate.now().plusDays(10), null);
    }
}
