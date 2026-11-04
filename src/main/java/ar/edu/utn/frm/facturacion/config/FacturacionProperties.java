package ar.edu.utn.frm.facturacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades "facturacion.*" de application.yml. */
@ConfigurationProperties(prefix = "facturacion")
public record FacturacionProperties(
        Cors cors,
        double ivaPorDefecto,
        String usuarioPorDefecto) {

    public record Cors(String allowedOrigins) {}
}
