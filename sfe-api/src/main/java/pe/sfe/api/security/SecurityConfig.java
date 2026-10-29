package pe.sfe.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pe.sfe.persistence.repo.PosRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * API sin estado: cada peticion se autentica con su API Key. CSRF no aplica (no hay sesion ni cookies).
     * Todo lo que no sea /api/** se deniega; actuator vive en OTRO puerto (management.server.port), no expuesto al exterior.
     */
    @Bean
    SecurityFilterChain filtros(HttpSecurity http, PosRepository posRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/**").hasRole("POS")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new ApiKeyFilter(posRepository), UsernamePasswordAuthenticationFilter.class)
                .headers(h -> h.frameOptions(f -> f.deny()));
        return http.build();
    }
}
