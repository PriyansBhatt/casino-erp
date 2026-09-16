package com.casino.casinoerp;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.service.AuthenticatedUserService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class S1AccountAuthorityTests {
 UserRepository repo=mock(UserRepository.class);AuthenticatedUserService service=new AuthenticatedUserService(repo);
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void authenticate(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",null,List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));}
 User user(String status,String role){User u=new User();u.setUsername("user");u.setStatus(status);u.setRole(role);return u;}
 @Test void missingAuthenticationRejected(){assertThatThrownBy(service::getRequiredUser).isInstanceOf(org.springframework.security.core.AuthenticationException.class);}
 @Test void deletedActorRejected(){authenticate();assertThatThrownBy(service::getRequiredUser).isInstanceOf(org.springframework.security.core.AuthenticationException.class);}
 @Test void inactiveActorRejected(){authenticate();when(repo.findByUsername("user")).thenReturn(user("INACTIVE","SUPER_ADMIN"));assertThatThrownBy(service::getRequiredUser).isInstanceOf(org.springframework.security.core.AuthenticationException.class);}
 @Test void staleRoleActorRejected(){authenticate();when(repo.findByUsername("user")).thenReturn(user("ACTIVE","CASHIER"));assertThatThrownBy(service::getRequiredUser).isInstanceOf(org.springframework.security.core.AuthenticationException.class);}
 @Test void validatedSnapshotAvoidsSecondLookup(){User u=user("ACTIVE","SUPER_ADMIN");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUserService.Account(u),null,List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));assertThat(service.getRequiredUser()).isSameAs(u);verifyNoInteractions(repo);}
}
