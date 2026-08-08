package com.casino.casinoerp;

import com.casino.casinoerp.dto.UserResponse;
import com.casino.casinoerp.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void userEntityDoesNotSerializePasswordHash() throws Exception {
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash("sensitive-hash");

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash", "sensitive-hash");
    }

    @Test
    void userResponseContainsOnlySafeAdministrativeFields() throws Exception {
        UserResponse response = new UserResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "admin",
                "Super Admin",
                "admin@example.com",
                "ACTIVE",
                "SUPER_ADMIN",
                LocalDateTime.of(2026, 8, 5, 12, 0)
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("username", "fullName", "email", "status", "role", "createdAt")
                .doesNotContain("passwordHash", "password", "token", "credentials");
    }
}
