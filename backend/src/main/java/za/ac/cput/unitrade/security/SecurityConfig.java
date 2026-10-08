package za.ac.cput.unitrade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import za.ac.cput.unitrade.exception.ErrorResponse;

import java.io.IOException;

/**
 * Which endpoints are public and which need a login. Stateless: there are no server sessions, every request
 * proves itself with its JWT. CSRF protection is switched off because it defends cookie sessions, and we use
 * a token header that a malicious website cannot make the browser attach.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // uses the origins configured in WebConfig
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // browser CORS pre-flight
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Browsing is public. "mine" must be listed first: it matches {id} too but needs a login.
                        .requestMatchers(HttpMethod.GET, "/api/listings/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/listings", "/api/listings/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{id}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bulletin", "/api/bulletin/{id}").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized(objectMapper))
                        .accessDeniedHandler(forbidden(objectMapper)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt: a slow, salted one-way hash. Passwords are never stored or compared in plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // These two run inside the security filters, before controllers, so the global exception handler
    // cannot catch them. They write the same JSON shape by hand.
    private static AuthenticationEntryPoint unauthorized(ObjectMapper mapper) {
        return (request, response, ex) -> writeError(mapper, response, HttpStatus.UNAUTHORIZED,
                "Please log in to continue.", request.getRequestURI());
    }

    private static AccessDeniedHandler forbidden(ObjectMapper mapper) {
        return (request, response, ex) -> writeError(mapper, response, HttpStatus.FORBIDDEN,
                "You are not allowed to do that.", request.getRequestURI());
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, HttpStatus status,
                                   String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, message, path, null));
    }
}
