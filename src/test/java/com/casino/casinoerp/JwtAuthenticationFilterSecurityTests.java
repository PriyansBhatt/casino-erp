package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.AuthController;
import com.casino.casinoerp.controller.UserController;
import com.casino.casinoerp.dto.LoginResponse;
import com.casino.casinoerp.service.AuthService;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        UserController.class,
        AuthController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class JwtAuthenticationFilterSecurityTests {

    private static final String PROTECTED_PATH = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.casino.casinoerp.repository.UserRepository users;

    @org.junit.jupiter.api.BeforeEach void activeAccounts() {
        for(String role: java.util.List.of("SUPER_ADMIN","CASHIER")) {
            var value=new com.casino.casinoerp.entity.User();value.setUsername(role.equals("SUPER_ADMIN")?"superadmin":"cashier");value.setRole(role);value.setStatus("ACTIVE");
            when(users.findByUsername(value.getUsername())).thenReturn(value);
        }
    }
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @Test
    void validJwtAuthenticatesProtectedRequest() throws Exception {
        when(jwtService.extractUsername("valid-token")).thenReturn("superadmin");
        when(jwtService.extractRole("valid-token")).thenReturn("SUPER_ADMIN");

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void expiredJwtReturnsControlledUnauthorizedJsonAndClearsSecurityContext() throws Exception {
        when(jwtService.extractUsername("expired-token")).thenThrow(new ExpiredJwtException(
                mock(Header.class), mock(Claims.class), "expired"));

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication token is invalid or expired."))
                .andExpect(jsonPath("$.data").isEmpty());

        org.assertj.core.api.Assertions.assertThat(
                SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedJwtReturnsControlledUnauthorizedJson() throws Exception {
        when(jwtService.extractUsername("malformed-token"))
                .thenThrow(new MalformedJwtException("malformed"));

        assertInvalidTokenIsUnauthorized("malformed-token");
    }

    @Test
    void invalidSignatureJwtReturnsControlledUnauthorizedJson() throws Exception {
        when(jwtService.extractUsername("invalid-signature-token"))
                .thenThrow(new SignatureException("invalid signature"));

        assertInvalidTokenIsUnauthorized("invalid-signature-token");
    }

    @Test
    void missingTokenPreservesProtectedEndpointBehavior() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenDoesNotBlockPublicLoginEndpoint() throws Exception {
        when(authService.login(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid credentials."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void authenticatedButUnauthorizedRoleRemainsForbidden() throws Exception {
        when(jwtService.extractUsername("cashier-token")).thenReturn("cashier");
        when(jwtService.extractRole("cashier-token")).thenReturn("CASHIER");

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer cashier-token"))
                .andExpect(status().isForbidden());
    }

    private void assertInvalidTokenIsUnauthorized(String token) throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication token is invalid or expired."));
    }

}
