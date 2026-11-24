package ar.edu.utn.frm.facturacion.service.arca;

import ar.edu.utn.frm.facturacion.config.ArcaProperties;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Cliente SOAP WSFEv1 dirigido exclusivamente al endpoint de homologación. */
@Component
public class Wsfev1Gateway implements ArcaGateway {
    private static final URI WSFE_HOMO = URI.create("https://wswhomo.afip.gov.ar/wsfev1/service.asmx");
    private static final String NS = "http://ar.gov.afip.dif.FEV1/";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;
    private final WsaaTicketService wsaa;
    private final ArcaProperties properties;
    private final ArcaSoapTransport transport = new ArcaSoapTransport();

    public Wsfev1Gateway(WsaaTicketService wsaa, ArcaProperties properties) {
        this.wsaa = wsaa;
        this.properties = properties;
    }

    @Override
    public long ultimoAutorizado(int puntoVenta, int tipo) {
        Document xml = invocar("FECompUltimoAutorizado", "<PtoVta>" + puntoVenta + "</PtoVta><CbteTipo>" + tipo + "</CbteTipo>");
        Element result = ArcaSoapXml.first(xml.getDocumentElement(), "FECompUltimoAutorizadoResult");
        if (result == null) throw new ArcaConexionException("WSFEv1 no devolvió FECompUltimoAutorizadoResult");
        if (error602(result)) return 0;
        comprobarErrores(result);
        return Long.parseLong(ArcaSoapXml.required(result, "CbteNro"));
    }

    @Override
    public Optional<Autorizacion> consultar(int puntoVenta, int tipo, long numero) {
        String datos = "<FeCompConsReq><CbteTipo>" + tipo + "</CbteTipo><CbteNro>" + numero
                + "</CbteNro><PtoVta>" + puntoVenta + "</PtoVta></FeCompConsReq>";
        Document xml = invocar("FECompConsultar", datos);
        Element result = ArcaSoapXml.first(xml.getDocumentElement(), "FECompConsultarResult");
        if (result == null) throw new ArcaConexionException("WSFEv1 no devolvió FECompConsultarResult");
        Element comprobante = ArcaSoapXml.first(result, "ResultGet");
        if (comprobante == null) {
            if (error602(result)) return Optional.empty();
            comprobarErrores(result);
            throw new ArcaConexionException("WSFEv1 no informó el estado del comprobante consultado");
        }
        comprobarErrores(result);
        String resultado = ArcaSoapXml.required(comprobante, "Resultado");
        if (!"A".equals(resultado)) return Optional.empty();
        return Optional.of(new Autorizacion(
                ArcaSoapXml.required(comprobante, "CodAutorizacion"),
                fecha(ArcaSoapXml.required(comprobante, "FchVto")),
                Integer.parseInt(ArcaSoapXml.required(comprobante, "DocTipo")),
                Long.parseLong(ArcaSoapXml.required(comprobante, "DocNro")),
                fecha(ArcaSoapXml.required(comprobante, "CbteFch")),
                new BigDecimal(ArcaSoapXml.required(comprobante, "ImpTotal")),
                new BigDecimal(ArcaSoapXml.required(comprobante, "ImpNeto")),
                new BigDecimal(ArcaSoapXml.required(comprobante, "ImpIVA")),
                ArcaSoapXml.required(comprobante, "MonId")));
    }

    @Override
    public Respuesta autorizar(ArcaComprobante c, long numero) {
        StringBuilder iva = new StringBuilder();
        for (ArcaComprobante.Alicuota a : c.alicuotas()) {
            iva.append("<AlicIva><Id>").append(a.codigo()).append("</Id><BaseImp>").append(a.base())
                    .append("</BaseImp><Importe>").append(a.importe()).append("</Importe></AlicIva>");
        }
        String datos = "<FeCAEReq><FeCabReq><CantReg>1</CantReg><PtoVta>" + c.puntoVenta()
                + "</PtoVta><CbteTipo>" + c.tipo() + "</CbteTipo></FeCabReq><FeDetReq><FECAEDetRequest>"
                + "<Concepto>1</Concepto><DocTipo>" + c.documentoTipo() + "</DocTipo><DocNro>" + c.documentoNumero()
                + "</DocNro><CbteDesde>" + numero + "</CbteDesde><CbteHasta>" + numero
                + "</CbteHasta><CbteFch>" + c.fecha().format(FECHA) + "</CbteFch>"
                + "<ImpTotal>" + c.importeTotal() + "</ImpTotal><ImpTotConc>0</ImpTotConc><ImpNeto>"
                + c.importeNeto() + "</ImpNeto><ImpOpEx>0</ImpOpEx><ImpTrib>0</ImpTrib><ImpIVA>"
                + c.importeIva() + "</ImpIVA><MonId>PES</MonId><MonCotiz>1</MonCotiz>"
                + "<CondicionIVAReceptorId>" + c.condicionIvaReceptor() + "</CondicionIVAReceptorId>"
                + (c.tipo() == 11 ? "" : "<Iva>" + iva + "</Iva>")
                + "</FECAEDetRequest></FeDetReq></FeCAEReq>";
        Document xml = invocar("FECAESolicitar", datos);
        Element result = ArcaSoapXml.first(xml.getDocumentElement(), "FECAESolicitarResult");
        if (result == null) throw new ArcaConexionException("WSFEv1 no devolvió FECAESolicitarResult");
        Element detail = ArcaSoapXml.first(result, "FECAEDetResponse");
        if (detail == null) {
            String error = mensaje(result);
            if (error != null) return new Respuesta(false, null, null, error);
            throw new ArcaConexionException("WSFEv1 no devolvió el resultado del comprobante");
        }
        String resultado = ArcaSoapXml.text(detail, "Resultado");
        if ("A".equals(resultado)) {
            String cae = ArcaSoapXml.required(detail, "CAE");
            if (!cae.matches("[0-9]{14}")) throw new ArcaConexionException("WSFEv1 devolvió un CAE inválido");
            return new Respuesta(true, cae, fecha(ArcaSoapXml.required(detail, "CAEFchVto")), mensaje(detail));
        }
        if ("R".equals(resultado) || "R".equals(ArcaSoapXml.text(result, "Resultado"))) {
            return new Respuesta(false, null, null, mensaje(detail) == null ? mensaje(result) : mensaje(detail));
        }
        throw new ArcaConexionException("WSFEv1 devolvió un resultado de autorización desconocido");
    }

    private Document invocar(String accion, String datos) {
        WsaaTicketService.Ticket ticket = wsaa.ticket();
        String auth = "<Auth><Token>" + ArcaSoapXml.escape(ticket.token()) + "</Token><Sign>"
                + ArcaSoapXml.escape(ticket.sign()) + "</Sign><Cuit>" + cuit() + "</Cuit></Auth>";
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<" + accion + " xmlns=\"" + NS + "\">" + auth + datos + "</" + accion + ">"
                + "</soap:Body></soap:Envelope>";
        return transport.post(WSFE_HOMO, "\"" + NS + accion + "\"", body);
    }

    private long cuit() {
        if (properties.cuit() == null || !properties.cuit().matches("[0-9]{11}")) {
            throw new ReglaNegocioException("Falta ARCA_CUIT de homologación");
        }
        return Long.parseLong(properties.cuit());
    }

    private static boolean error602(Element result) {
        Element errors = ArcaSoapXml.first(result, "Errors");
        return errors != null && "602".equals(ArcaSoapXml.text(errors, "Code"));
    }

    private static void comprobarErrores(Element result) {
        Element errors = ArcaSoapXml.first(result, "Errors");
        String msg = errors == null ? null : ArcaSoapXml.text(errors, "Msg");
        if (msg != null && !msg.isBlank()) throw new ArcaConexionException("WSFEv1 rechazó la consulta: " + msg);
    }

    private static String mensaje(Element result) {
        Element errors = ArcaSoapXml.first(result, "Errors");
        if (errors != null && ArcaSoapXml.text(errors, "Msg") != null) return ArcaSoapXml.text(errors, "Msg");
        Element observations = ArcaSoapXml.first(result, "Observaciones");
        return observations == null ? null : ArcaSoapXml.text(observations, "Msg");
    }

    private static LocalDate fecha(String value) {
        try { return LocalDate.parse(value, FECHA); }
        catch (Exception e) { throw new ArcaConexionException("WSFEv1 devolvió una fecha inválida", e); }
    }
}
