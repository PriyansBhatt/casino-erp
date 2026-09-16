package com.casino.casinoerp.config;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String INVALID_TOKEN_MESSAGE = "Authentication token is invalid or expired.";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final org.springframework.beans.factory.ObjectProvider<com.casino.casinoerp.repository.UserRepository> users;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper, org.springframework.beans.factory.ObjectProvider<com.casino.casinoerp.repository.UserRepository> users) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtService.extractUsername(token);
            Optional<Role> role = Role.fromValue(jwtService.extractRole(token));

            if(username==null || username.isBlank() || role.isEmpty())throw new IllegalArgumentException("Invalid token claims");
            var user=users.getObject().findByUsername(username);
            var persistedRole=com.casino.casinoerp.service.AuthenticatedUserService.requireActiveRole(user);
            if(persistedRole!=role.get())throw new org.springframework.security.authentication.BadCredentialsException("Role changed; sign in again.");
            var authentication=new UsernamePasswordAuthenticationToken(
                new com.casino.casinoerp.service.AuthenticatedUserService.Account(user),null,
                List.of(new SimpleGrantedAuthority(persistedRole.authority())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | org.springframework.security.core.AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(INVALID_TOKEN_MESSAGE));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
