package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Lee el header "X-Usuario" (o usa facturacion.usuario-por-defecto) y deja el Usuario
 * disponible para los callbacks de auditoría durante todo el request.
 */
@Component
public class UsuarioActualInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Usuario";

    private final UsuarioRepository usuarioRepository;
    private final FacturacionProperties props;

    public UsuarioActualInterceptor(UsuarioRepository usuarioRepository, FacturacionProperties props) {
        this.usuarioRepository = usuarioRepository;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String nombre = request.getHeader(HEADER);
        if (nombre == null || nombre.isBlank()) nombre = props.usuarioPorDefecto();
        Usuario usuario = usuarioRepository.findByUsuario(nombre)
                .or(() -> usuarioRepository.findByUsuario(props.usuarioPorDefecto()))
                .orElse(null);
        UsuarioActual.set(usuario);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UsuarioActual.clear();
    }
}
