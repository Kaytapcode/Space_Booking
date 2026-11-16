package com.spacebooking.security;

import com.spacebooking.auth.service.JwtService;
import com.spacebooking.common.api.ProblemFactory;
import com.spacebooking.config.CorsProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Same truthy spellings as Spring's String->boolean conversion, so no spelling slips past the admin rule.
    private static final Set<String> TRUTHY = Set.of("true", "on", "yes", "1");

    // Same path matching as the other rules (decoded, normalised), so an encoded path such as /api/v1/%72ooms cannot skip it.
    private static final RequestMatcher ROOMS_LIST = AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/rooms");

    private static final RequestMatcher INCLUDE_INACTIVE = request ->
            ROOMS_LIST.matches(request)
                    && request.getParameterValues("includeInactive") != null
                    && Arrays.stream(request.getParameterValues("includeInactive"))
                            .anyMatch(v -> TRUTHY.contains(v.trim().toLowerCase(Locale.ROOT)));

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, ProblemFactory problems,
                                            Environment env) throws Exception {
        boolean e2e = env.acceptsProfiles(Profiles.of("e2e"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    if (e2e) {
                        auth.requestMatchers("/test-support/**").permitAll();
                    }
                    auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Any method: a wrong method on a public path must reach MVC and get 405, not 401.
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // Read by the contract-conformance check only; springdoc disables it unless API_DOCS_ENABLED=true, so prod answers 404.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        // Roles are decided here, before MVC binds and validates the request (D29, domain.md section 9).
                        .requestMatchers(INCLUDE_INACTIVE).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rooms/{roomId}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rooms/{roomId}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasAnyRole("EMPLOYEE", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/my-bookings")
                                .hasAnyRole("EMPLOYEE", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/bookings/pending").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/{bookingId}/approve",
                                "/api/v1/bookings/{bookingId}/reject").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/{bookingId}/check-in",
                                "/api/v1/bookings/{bookingId}/checkout", "/api/v1/bookings/{bookingId}/cancel")
                                .hasAnyRole("EMPLOYEE", "MANAGER")
                        .anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(ProblemSecurityHandlers.entryPoint(problems))
                        .accessDeniedHandler(ProblemSecurityHandlers.accessDenied(problems)))
                .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
