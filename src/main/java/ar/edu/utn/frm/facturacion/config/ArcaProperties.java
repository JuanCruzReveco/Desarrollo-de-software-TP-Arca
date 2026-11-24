package ar.edu.utn.frm.facturacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Solo homologación; los endpoints no se pueden redirigir a producción por configuración. */
@ConfigurationProperties(prefix = "arca")
public record ArcaProperties(String cuit, String certificatePath, String privateKeyPath,
                             Integer condicionIvaEmisor) {}
