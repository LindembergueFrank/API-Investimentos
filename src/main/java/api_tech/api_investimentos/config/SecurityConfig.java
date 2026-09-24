package api_tech.api_investimentos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.crypto.SecretKey;
import jakarta.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/auth/token", "/v1/users")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "urn:problem-type:api-investimentos:authentication-required",
                                "Authentication required",
                                "A valid bearer token is required.",
                                request.getRequestURI()
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.FORBIDDEN,
                                "urn:problem-type:api-investimentos:access-denied",
                                "Access denied",
                                "The authenticated user cannot access this resource.",
                                request.getRequestURI()
                        )))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "urn:problem-type:api-investimentos:invalid-token",
                                "Invalid access token",
                                "The bearer token is missing, invalid or expired.",
                                request.getRequestURI()
                        )));

        return http.build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static SecretKey signingKey(JwtProperties properties) {
        byte[] key = Base64.getDecoder().decode(properties.secretBase64());
        return new SecretKeySpec(key, "HmacSHA256");
    }

    private static void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String type,
            String title,
            String detail,
            String instance
    ) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(instance));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
