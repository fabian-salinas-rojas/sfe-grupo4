package pe.sfe.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.sfe.persistence.repo.PosRepository;

/**
 * Autenticacion por API Key (cabecera X-API-Key), una por POS. La BD solo guarda el SHA-256 de la llave: aunque se
 * filtre la tabla, no se pueden reconstruir las llaves. Una llave ausente/invalida/inactiva deja la peticion sin
 * autenticar y el EntryPoint responde 401 (sin explicar cual fue el motivo).
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-API-Key";
    private static final int LONGITUD_MAXIMA = 200;

    private final PosRepository posRepository;

    public ApiKeyFilter(PosRepository posRepository) {
        this.posRepository = posRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String llave = request.getHeader(CABECERA);
        if (llave != null && !llave.isBlank() && llave.length() <= LONGITUD_MAXIMA) {
            posRepository.findByApiKeyHashAndActivoTrue(sha256Hex(llave)).ifPresent(pos -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        new PosPrincipal(pos.getId(), pos.getEmisorId(), pos.getNombre()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_POS")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }

    public static String sha256Hex(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
