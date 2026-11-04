package ar.edu.utn.frm.facturacion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: el frontend es un proyecto separado (otro origen), así que el navegador
 * exige que el backend lo autorice explícitamente.
 * Interceptor: resuelve el usuario de auditoría de cada request.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FacturacionProperties props;
    private final UsuarioActualInterceptor usuarioActualInterceptor;

    public WebConfig(FacturacionProperties props, UsuarioActualInterceptor usuarioActualInterceptor) {
        this.props = props;
        this.usuarioActualInterceptor = usuarioActualInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().allowedOrigins().split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(usuarioActualInterceptor).addPathPatterns("/api/**");
    }
}
