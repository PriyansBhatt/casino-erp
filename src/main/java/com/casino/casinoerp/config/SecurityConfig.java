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

                        .requestMatchers(HttpMethod.GET, "/api/business-status/current")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/machines", "/api/machines/**")
                        .hasAnyRole(Role.SUPER_ADMIN.name(), Role.DIRECTOR.name())
                        .requestMatchers("/api/machines", "/api/machines/**")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/attendance")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers("/api/attendance/*/corrections")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers("/api/attendance/**")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/hr/staff/me")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/hr/leave/me")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/hr/leave-types/available")
                        .authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/hr/leave")
                        .authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/hr/leave/me/*/cancel")
                        .authenticated()

                        .requestMatchers("/api/hr", "/api/hr/**")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET,
                                "/api/pit-tables/*/mode", "/api/pit-tables/*/eligible-players")
                        .hasAnyRole(Role.DEALER.name(), Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/customers/id/**")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/{customerId}/kyc/privileged",
                                "/api/customers/{customerId}/identity-documents"
                        )
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.PATCH, "/api/customers/{customerId}/classification")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/customers/{customerId}/kyc")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.PATCH, "/api/customers/{customerId}/kyc")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST, "/api/customers/{customerId}/identity-documents")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/customers")
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/customers", "/api/customers/**")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST, "/api/customers")
                        .hasAnyRole(
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST, "/api/buyins")
                        .hasAnyRole(Role.CASHIER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/buyins/current")
                        .hasAnyRole(Role.CASHIER.name(), Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/reports/running-funds")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/customer-bonuses")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/customer-bonuses")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers("/api/hotel-bookings/**", "/api/hotel-bookings")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/cashouts")
                        .hasAnyRole(Role.CASHIER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/losing-returns/eligibility/customer/*",
                                "/api/losing-returns/history/customer/*")
                        .hasAnyRole(Role.CASHIER.name(), Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/losing-returns")
                        .hasAnyRole(Role.CASHIER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/verified-gaming-results")
                        .hasAnyRole(
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST, "/api/pit/tables/*/players", "/api/pit/tables/*/players/*/leave")
                        .hasAnyRole(
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/pit/tables/*/players", "/api/pit/tables/*/players/history")
                        .hasAnyRole(
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/pit-tables/staff/candidates")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/pit-tables/*/staff", "/api/pit-tables/*/staff/history")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.DEALER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/pit-tables/*/staff", "/api/pit-tables/*/staff/**")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/pit-tables/physical/*/history")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/pit-tables", "/api/pit-tables/**")
                        .hasAnyRole(
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST,
                                "/api/pit-tables/*/legacy-reconciliation-resolution")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST,
                                "/api/pit-tables/physical/*/open")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.PUT, "/api/pit-tables/*/close")
                        .hasAnyRole(
                                Role.PIT_SUPERVISOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/verified-gaming-results/**")
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/session-summary/**")
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.GET, "/api/chip-control/sessions")
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.PIT_SUPERVISOR.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

                        .requestMatchers(HttpMethod.POST, "/api/chip-custody/cage/opening")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/chip-custody/legacy-session-correction")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/chip-custody/tables/*/float-issue",
                                "/api/chip-custody/tables/*/float-return")
                        .hasAnyRole(Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST,
                                "/api/chip-custody/tables/*/customer-sessions/*/issue",
                                "/api/chip-custody/tables/*/customer-sessions/*/return")
                        .hasAnyRole(Role.DEALER.name(), Role.PIT_SUPERVISOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/chip-custody/movements/current")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/chip-custody/cage")
                        .hasAnyRole(Role.CASHIER.name(), Role.PIT_SUPERVISOR.name(),
                                Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET,
                                "/api/chip-custody/customer-sessions/*", "/api/chip-custody/tables/*")
                        .hasAnyRole(Role.CASHIER.name(), Role.PIT_SUPERVISOR.name(), Role.DEALER.name(),
                                Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers("/api/pit-table-transactions", "/api/pit-table-transactions/**")
                        .denyAll()

                        .requestMatchers(HttpMethod.GET,
                                "/api/dashboard/management",
                                "/api/dashboard/pit-summary",
                                "/api/dashboard/table-results",
                                "/api/dashboard/pit-performance",
                                "/api/dashboard/overall-position")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/cashier-reconciliation/current")
                        .hasAnyRole(Role.CASHIER.name(), Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/cashier-opening-balances/current")
                        .hasAnyRole(Role.CASHIER.name(), Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/cashier-opening-balances/current")
                        .hasAnyRole(Role.CASHIER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET, "/api/cashier-reconciliation/current/submitted")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.GET,
                                "/api/cashier-reconciliation/current/legacy-actor-resolutions")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST,
                                "/api/cashier-reconciliation/legacy-actor-resolution/*")
                        .hasRole(Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/cashier-reconciliation/*/reopen")
                        .hasAnyRole(Role.DIRECTOR.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST,
                                "/api/cashier-reconciliation/preview", "/api/cashier-reconciliation/submit")
                        .hasAnyRole(Role.CASHIER.name(), Role.SUPER_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, "/api/wallet-transactions", "/api/wallet-transactions/**")
                        .denyAll()

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

                        .requestMatchers(HttpMethod.GET, "/api/sessions/active/customer/{customerId}")
                        .hasAnyRole(
                                Role.CASHIER.name(),
                                Role.PIT_SUPERVISOR.name(),
                                Role.DEALER.name(),
                                Role.RECEPTIONIST.name(),
                                Role.DIRECTOR.name(),
                                Role.SUPER_ADMIN.name()
                        )

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

                        .requestMatchers(HttpMethod.POST, "/api/business-date/continuation-override")
                        .hasAnyRole(Role.SUPER_ADMIN.name(), Role.DIRECTOR.name())

                        .requestMatchers(HttpMethod.DELETE, "/api/business-date/continuation-override")
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
