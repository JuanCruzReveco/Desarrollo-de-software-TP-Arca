package ar.edu.utn.frm.facturacion.service.arca;

import ar.edu.utn.frm.facturacion.config.ArcaProperties;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/** Obtiene TA de WSAA y lo reutiliza hasta poco antes de su vencimiento. */
@Component
public class WsaaTicketService {
    private static final URI WSAA_HOMO = URI.create("https://wsaahomo.afip.gov.ar/ws/services/LoginCms");
    private final ArcaProperties properties;
    private final ArcaSoapTransport transport = new ArcaSoapTransport();
    private volatile Ticket cached;

    public WsaaTicketService(ArcaProperties properties) { this.properties = properties; }

    public synchronized Ticket ticket() {
        if (cached != null && Instant.now().isBefore(cached.expira().minusSeconds(120))) return cached;
        Credentials credentials = credentials();
        String tra = tra();
        String cms = firmar(tra, credentials);
        String soap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:wsaa=\"http://wsaa.view.sua.dvadac.desein.afip.gov\"><soapenv:Body>"
                + "<wsaa:loginCms><wsaa:in0>" + cms + "</wsaa:in0></wsaa:loginCms>"
                + "</soapenv:Body></soapenv:Envelope>";
        Document response = transport.post(WSAA_HOMO, "urn:LoginCms", soap);
        String xml = ArcaSoapXml.required(response.getDocumentElement(), "loginCmsReturn");
        Document login = ArcaSoapXml.parse(xml);
        String token = ArcaSoapXml.required(login.getDocumentElement(), "token");
        String sign = ArcaSoapXml.required(login.getDocumentElement(), "sign");
        Instant expira;
        try { expira = OffsetDateTime.parse(ArcaSoapXml.required(login.getDocumentElement(), "expirationTime")).toInstant(); }
        catch (Exception e) { throw new ArcaConexionException("WSAA devolvió un vencimiento de ticket inválido", e); }
        if (expira.isBefore(Instant.now().plusSeconds(120))) throw new ArcaConexionException("WSAA devolvió un ticket vencido");
        cached = new Ticket(token, sign, expira);
        return cached;
    }

    private Credentials credentials() {
        if (properties.cuit() == null || !properties.cuit().matches("[0-9]{11}")
                || properties.certificatePath() == null || properties.certificatePath().isBlank()
                || properties.privateKeyPath() == null || properties.privateKeyPath().isBlank()) {
            throw new ReglaNegocioException("Faltan ARCA_CUIT, ARCA_CERTIFICATE_PATH o ARCA_PRIVATE_KEY_PATH de homologación");
        }
        try {
            X509Certificate certificate;
            try (var input = Files.newInputStream(Path.of(properties.certificatePath()))) {
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
            certificate.checkValidity();
            // RFC2253 representa SERIALNUMBER=CUIT ... como hexadecimal cuando el
            // certificado usa PrintableString. RFC1779 decodifica el atributo para
            // poder comparar la CUIT sin depender de la codificación ASN.1 concreta.
            String subject = certificate.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.RFC1779);
            if (!subject.contains("CUIT " + properties.cuit())) {
                throw new ReglaNegocioException("La CUIT configurada no coincide con el certificado");
            }
            if (!certificate.getIssuerX500Principal().getName().contains("Computadores Test")) {
                throw new ReglaNegocioException("El certificado no corresponde a homologación de ARCA");
            }
            PrivateKey key;
            try (PEMParser parser = new PEMParser(new StringReader(Files.readString(Path.of(properties.privateKeyPath()))))) {
                Object object = parser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                if (object instanceof PrivateKeyInfo info) key = converter.getPrivateKey(info);
                else if (object instanceof PEMKeyPair pair) key = converter.getKeyPair(pair).getPrivate();
                else throw new ReglaNegocioException("La clave debe estar en formato PEM RSA sin cifrar");
            }
            if (!"RSA".equalsIgnoreCase(key.getAlgorithm())) throw new ReglaNegocioException("La clave privada debe ser RSA");
            return new Credentials(certificate, key);
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new ReglaNegocioException("No se pudieron cargar las credenciales de homologación: " + e.getClass().getSimpleName());
        }
    }

    private String tra() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        String generacion = ahora.minusMinutes(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String expiracion = ahora.plusMinutes(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        long id = Instant.now().getEpochSecond();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><loginTicketRequest version=\"1.0\"><header>"
                + "<uniqueId>" + id + "</uniqueId><generationTime>" + generacion + "</generationTime>"
                + "<expirationTime>" + expiracion + "</expirationTime></header><service>wsfe</service></loginTicketRequest>";
    }

    private String firmar(String tra, Credentials credentials) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build()).build(
                    new JcaContentSignerBuilder("SHA256withRSA").build(credentials.key()), credentials.certificate()));
            generator.addCertificates(new JcaCertStore(java.util.List.of(credentials.certificate())));
            byte[] cms = generator.generate(new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)), true).getEncoded();
            return Base64.getEncoder().encodeToString(cms);
        } catch (Exception e) {
            throw new ReglaNegocioException("No se pudo firmar el TRA con el certificado configurado");
        }
    }

    public record Ticket(String token, String sign, Instant expira) {}
    private record Credentials(X509Certificate certificate, PrivateKey key) {}
}
