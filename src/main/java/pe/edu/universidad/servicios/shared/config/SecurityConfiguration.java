package pe.edu.universidad.servicios.shared.config;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import pe.edu.universidad.servicios.auth.repository.UserSessionRepository;
import pe.edu.universidad.servicios.auth.security.AccessTokenService;
import pe.edu.universidad.servicios.auth.security.SessionAuthenticationFilter;
import pe.edu.universidad.servicios.shared.web.CorrelationIdFilter;
import pe.edu.universidad.servicios.shared.web.ProblemResponseWriter;

/**
 * Cadena sin estado: deniega por defecto y autentica con JWT + sesión vigente en base de datos.
 * CSRF de Spring se desactiva porque las operaciones con cookie (refresh y logout) validan el origen
 * y la cookie usa SameSite=Lax; el resto usa Authorization: Bearer.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService tokens, UserSessionRepository sessions,
            ProblemResponseWriter problems, Clock clock) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/status", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> problems.write(response, HttpStatus.UNAUTHORIZED,
                                "NO_AUTENTICADO", "Se requiere una sesión válida."))
                        .accessDeniedHandler((request, response, e) -> problems.write(response, HttpStatus.FORBIDDEN,
                                "ACCESO_DENEGADO", "La operación no está permitida para el rol actual.")))
                .headers(headers -> headers.cacheControl(cache -> { }))
                .addFilterBefore(new SessionAuthenticationFilter(tokens, sessions, problems, clock),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.security().allowedOrigins().stream().map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER));
        config.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Location", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
