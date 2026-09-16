package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.*;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class,UserController.class,S1AuthenticationTests.ActorProbe.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,AuthService.class,
        AuthenticatedUserService.class,CurrentUserRoleService.class,PasswordConfig.class,S1AuthenticationTests.ActorProbe.class})
class S1AuthenticationTests {
 @Autowired MockMvc mvc;@Autowired JwtService jwt;@Autowired PasswordEncoder passwords;@Autowired Environment env;
 @MockitoBean UserRepository users;@MockitoBean UserService userService;
 User account;
 @BeforeEach void setup(){account=new User();account.setId(UUID.randomUUID());account.setUsername("operator");account.setStatus("ACTIVE");account.setRole("SUPER_ADMIN");account.setPasswordHash(passwords.encode("valid-password"));when(users.findByUsername("operator")).thenReturn(account);}
 @RestController static class ActorProbe {
  private final AuthenticatedUserService actors;private final CurrentUserRoleService roles;
  ActorProbe(AuthenticatedUserService actors,CurrentUserRoleService roles){this.actors=actors;this.roles=roles;}
  @GetMapping("/api/s1-test-actor") String actor(){actors.getRequiredUser();roles.getCurrentUserId();return actors.getRequiredUser().getUsername();}
 }
 private String token(){return jwt.generateToken("operator","SUPER_ADMIN");}
 private org.springframework.test.web.servlet.ResultActions request(String token)throws Exception{return mvc.perform(get("/api/users").header("Authorization","Bearer "+token));}
 @Test void activeLoginIssuesToken()throws Exception{mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"operator\",\"password\":\"valid-password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty());}
 @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"INACTIVE","DISABLED","CLOSED","UNKNOWN"}) void nonActiveLoginDenied(String status)throws Exception{account.setStatus(status);mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"operator\",\"password\":\"valid-password\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").isEmpty());}
 @Test void unknownAndWrongPasswordHaveSameFailure()throws Exception{String first=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"unknown\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();String second=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"operator\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var firstJson=mapper.readTree(first);var secondJson=mapper.readTree(second);for(String field:List.of("success","message","data"))assertThat(firstJson.get(field)).isEqualTo(secondJson.get(field));}
 @Test void activeTokenAndSingleRequestLookup()throws Exception{String token=token();request(token).andExpect(status().isOk());clearInvocations(users);mvc.perform(get("/api/s1-test-actor").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(content().string("operator"));verify(users,times(1)).findByUsername("operator");}
 @Test void accountDisabledAfterIssueRejected()throws Exception{String token=token();request(token).andExpect(status().isOk());account.setStatus("INACTIVE");request(token).andExpect(status().isUnauthorized());}
 @Test void accountDeletedAfterIssueRejected()throws Exception{String token=token();when(users.findByUsername("operator")).thenReturn(null);request(token).andExpect(status().isUnauthorized());}
 @Test void demotionRequiresFreshLoginAndCannotRetainPrivilege()throws Exception{String old=token();account.setRole("CASHIER");request(old).andExpect(status().isUnauthorized());request(jwt.generateToken("operator","CASHIER")).andExpect(status().isForbidden());}
 @Test void promotionRequiresFreshTokenAndThenGetsCurrentAccess()throws Exception{String old=jwt.generateToken("operator","CASHIER");request(old).andExpect(status().isUnauthorized());request(token()).andExpect(status().isOk());}
 @Test void unknownPersistedRoleRejected()throws Exception{account.setRole("ADMIN");request(token()).andExpect(status().isUnauthorized());}
 @Test void invalidSignatureExpiredMalformedAndMissingRejected()throws Exception{
  String invalid=Jwts.builder().subject("operator").claim("role","SUPER_ADMIN").signWith(Jwts.SIG.HS512.key().build()).compact();request(invalid).andExpect(status().isUnauthorized());
  var key=Keys.hmacShaKeyFor(env.getProperty("casino.security.jwt.secret").getBytes(StandardCharsets.UTF_8));
  String expired=Jwts.builder().subject("operator").claim("role","SUPER_ADMIN").expiration(new Date(0)).signWith(key,Jwts.SIG.HS512).compact();request(expired).andExpect(status().isUnauthorized());request("bad").andExpect(status().isUnauthorized());mvc.perform(get("/api/users")).andExpect(status().is4xxClientError());
 }
 @Test void noSessionCanPreserveAuthenticationWithoutBearer()throws Exception{var result=request(token()).andExpect(status().isOk()).andReturn();assertThat(result.getRequest().getSession(false)).isNull();mvc.perform(get("/api/users")).andExpect(status().is4xxClientError());}
}
