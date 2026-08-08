package com.casino.casinoerp;

import com.casino.casinoerp.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTests {

    @Test
    void normalizesSuperAdminVariants() {
        assertThat(Role.fromValue("SUPER_ADMIN")).contains(Role.SUPER_ADMIN);
        assertThat(Role.fromValue("Super Admin")).contains(Role.SUPER_ADMIN);
        assertThat(Role.fromValue("super admin")).contains(Role.SUPER_ADMIN);
        assertThat(Role.fromValue("  super   admin  ")).contains(Role.SUPER_ADMIN);
    }

    @Test
    void normalizesPitSupervisorVariants() {
        assertThat(Role.fromValue("PIT_SUPERVISOR")).contains(Role.PIT_SUPERVISOR);
        assertThat(Role.fromValue("Pit Supervisor")).contains(Role.PIT_SUPERVISOR);
        assertThat(Role.fromValue("  pit supervisor ")).contains(Role.PIT_SUPERVISOR);
    }

    @Test
    void normalizesExistingReceptionAlias() {
        assertThat(Role.fromValue("Reception")).contains(Role.RECEPTIONIST);
        assertThat(Role.fromValue("Receptionist")).contains(Role.RECEPTIONIST);
    }

    @Test
    void rejectsUnknownAndPrefixedRoles() {
        assertThat(Role.fromValue("UNKNOWN_ROLE")).isEmpty();
        assertThat(Role.fromValue("ROLE_SUPER_ADMIN")).isEmpty();
        assertThat(Role.fromValue(" ")).isEmpty();
        assertThat(Role.fromValue(null)).isEmpty();
    }

    @Test
    void generatesCanonicalSpringAuthority() {
        assertThat(Role.SUPER_ADMIN.authority()).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(Role.fromAuthority("ROLE_SUPER_ADMIN")).contains(Role.SUPER_ADMIN);
        assertThat(Role.fromAuthority("SUPER_ADMIN")).isEmpty();
    }
}
