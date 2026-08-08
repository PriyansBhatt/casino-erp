package com.casino.casinoerp.config;

import com.casino.casinoerp.security.Role;
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
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/customers/id/**")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/customers", "/api/customers/**")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/wallets", "/api/wallets/**",
                                "/api/wallet-transactions", "/api/wallet-transactions/**",
                                "/api/buyins", "/api/buyins/**",
                                "/api/cashouts", "/api/cashouts/**"
                        )
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customer-report", "/api/customer-report/**",
                                "/api/customer-tier", "/api/customer-tier/**",
                                "/api/customer-value-report", "/api/customer-value-report/**",
                                "/api/daily-customer-value", "/api/daily-customer-value/**",
                                "/api/customer-services", "/api/customer-services/**",
                                "/api/customer-service-report", "/api/customer-service-report/**"
                        )
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/sessions", "/api/sessions/*/close")
                        .hasAnyRole(Role.RECEPTIONIST.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/sessions", "/api/sessions/**")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers("/api/system-lock/**")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers("/api/audit-logs/**")
                        .hasAnyRole(
                                Role.SUPER_ADMIN.name(),
                                Role.COMPLIANCE_OFFICER.name(),
                                Role.SURVEILLANCE_OFFICER.name()
                        )

                        .requestMatchers("/api/alerts/**")
                        .hasAnyRole(
                                Role.SUPER_ADMIN.name(),
                                Role.DIRECTOR.name(),
                                Role.SURVEILLANCE_OFFICER.name()
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/business-date/open/**",
                                "/api/business-date/close/**",
                                "/api/business-date/reopen/**"
                        )
                        .hasAnyRole(Role.SUPER_ADMIN.name(), Role.DIRECTOR.name())

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
