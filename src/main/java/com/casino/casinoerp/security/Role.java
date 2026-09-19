package com.casino.casinoerp.security;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum Role {
    SUPER_ADMIN,
    DIRECTOR,
    MANAGER,
    STORE_MANAGER,
    ACCOUNTANT_HEAD,
    ACCOUNTS_MANAGER,
    RECEPTIONIST,
    CASHIER,
    PIT_SUPERVISOR,
    DEALER,
    COMPLIANCE_OFFICER,
    SURVEILLANCE_OFFICER;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private static final Map<String, Role> ALIASES = Map.ofEntries(
            Map.entry("SUPER_ADMIN", SUPER_ADMIN),
            Map.entry("DIRECTOR", DIRECTOR),
            Map.entry("MANAGER", MANAGER),
            Map.entry("STORE_MANAGER", STORE_MANAGER),
            Map.entry("ACCOUNTANT_HEAD", ACCOUNTANT_HEAD),
            Map.entry("ACCOUNTS_MANAGER", ACCOUNTS_MANAGER),
            Map.entry("RECEPTION", RECEPTIONIST),
            Map.entry("RECEPTIONIST", RECEPTIONIST),
            Map.entry("CASHIER", CASHIER),
            Map.entry("PIT_SUPERVISOR", PIT_SUPERVISOR),
            Map.entry("DEALER", DEALER),
            Map.entry("COMPLIANCE_OFFICER", COMPLIANCE_OFFICER),
            Map.entry("SURVEILLANCE_OFFICER", SURVEILLANCE_OFFICER)
    );

    public static Optional<Role> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_");

        return Optional.ofNullable(ALIASES.get(normalized));
    }

    public static Optional<Role> fromAuthority(String authority) {
        if (authority == null || !authority.startsWith(AUTHORITY_PREFIX)) {
            return Optional.empty();
        }

        return fromValue(authority.substring(AUTHORITY_PREFIX.length()));
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
