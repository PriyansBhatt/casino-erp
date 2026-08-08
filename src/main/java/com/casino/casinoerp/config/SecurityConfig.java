package com.casino.casinoerp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})

                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**")
                        .hasRole("SUPER_ADMIN")

                        .requestMatchers("/api/system-lock/**")
                        .hasRole("SUPER_ADMIN")

                        .requestMatchers("/api/audit-logs/**")
                        .hasAnyRole("SUPER_ADMIN", "COMPLIANCE_OFFICER", "SURVEILLANCE_OFFICER")

                        .requestMatchers("/api/alerts/**")
                        .hasAnyRole("SUPER_ADMIN", "DIRECTOR", "SURVEILLANCE_OFFICER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/business-date/open/**",
                                "/api/business-date/close/**",
                                "/api/business-date/reopen/**"
                        )
                        .hasAnyRole("SUPER_ADMIN", "DIRECTOR")

                        .requestMatchers(HttpMethod.GET, "/api/business-date", "/api/business-date/**")
                        .authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedOrigin("http://localhost:3000");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

}
