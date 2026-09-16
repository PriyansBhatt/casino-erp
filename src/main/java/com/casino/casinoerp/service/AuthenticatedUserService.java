package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
public class AuthenticatedUserService {
    private final UserRepository userRepository;
    public AuthenticatedUserService(UserRepository userRepository) {this.userRepository=userRepository;}
    /** Request-local account snapshot; never placed in an HTTP session or logged as a User entity. */
    public static final class Account implements Principal {
        private final User user;
        public Account(User user){this.user=user;}
        @Override public String getName(){return user.getUsername();}
        @Override public String toString(){return getName();}
        public User user(){return user;}
    }
    public static Role requireActiveRole(User user) {
        if(user==null || !"ACTIVE".equalsIgnoreCase(user.getStatus()))throw new BadCredentialsException("Authentication is no longer valid.");
        return Role.fromValue(user.getRole()).orElseThrow(()->new BadCredentialsException("Authentication is no longer valid."));
    }
    public User getRequiredUser() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new BadCredentialsException("Authentication is required.");
        User user=auth.getPrincipal() instanceof Account account ? account.user() : userRepository.findByUsername(auth.getName());
        Role role=requireActiveRole(user);
        if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(role.authority())))throw new BadCredentialsException("Authentication is no longer valid.");
        return user;
    }
}
