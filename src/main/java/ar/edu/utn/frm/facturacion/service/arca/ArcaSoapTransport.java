package ar.edu.utn.frm.facturacion.service.arca;

import org.w3c.dom.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** HTTP sin registro de cuerpos: contienen el CMS o el ticket de acceso. */
final class ArcaSoapTransport {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    Document post(URI endpoint, String soapAction, String body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", soapAction)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Document document = ArcaSoapXml.parse(response.body());
            String fault = ArcaSoapXml.text(document.getDocumentElement(), "faultstring");
            if (fault != null) throw new ArcaConexionException("ARCA rechazó el servicio SOAP: " + fault);
            if (response.statusCode() != 200) throw new ArcaConexionException("ARCA respondió HTTP " + response.statusCode());
            return document;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArcaConexionException("La consulta a ARCA fue interrumpida", e);
        } catch (java.io.IOException e) {
            throw new ArcaConexionException("No se pudo conectar con ARCA; el resultado puede ser incierto", e);
        }
    }
}
