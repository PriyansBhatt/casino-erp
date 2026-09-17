package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import com.casino.casinoerp.exception.*;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTests {
 UserRepository users; StaffProfileRepository staff; AuthenticatedUserService actors; AuditLogService audit;
 EntityManager em; UserService service; User admin,target; BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 User account(String name,String role){User u=new User();u.setId(UUID.randomUUID());u.setUsername(name);u.setFullName(name);u.setStatus("ACTIVE");u.setRole(role);return u;}
 @BeforeEach void setup(){
  users=mock(UserRepository.class);staff=mock(StaffProfileRepository.class);actors=mock(AuthenticatedUserService.class);audit=mock(AuditLogService.class);em=mock(EntityManager.class);
  service=new UserService(users,staff,actors,encoder,audit,em,Validation.buildDefaultValidatorFactory().getValidator());
  admin=account("admin","SUPER_ADMIN");target=account("cashier","CASHIER");
  when(actors.getRequiredUser()).thenReturn(admin);when(users.findByIdForUpdate(admin.getId())).thenReturn(Optional.of(admin));when(users.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));when(users.countActiveSuperAdmins()).thenReturn(2L);
 }
 @Test void createsTrimmedCasePreservedHashedAuditedAccount(){
  var result=service.create(new CreateUserRequest(" MixedCase "," Full Name "," person@example.com ","CASHIER",null,"secret-value"));
  var captor=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(captor.capture());var saved=captor.getValue();
  assertThat(saved.getUsername()).isEqualTo("MixedCase");assertThat(saved.getEmail()).isEqualTo("person@example.com");assertThat(saved.getFullName()).isEqualTo("Full Name");assertThat(saved.getStatus()).isEqualTo("ACTIVE");assertThat(encoder.matches("secret-value",saved.getPasswordHash())).isTrue();assertThat(result.getUpdatedAt()).isNotNull();
  verify(audit).log(eq("USER_CREATED"),eq("USER_MANAGEMENT"),eq(saved.getId()),eq(admin.getId()),eq("role=CASHIER; status=ACTIVE"));
 }
 @Test void blankEmailBecomesNull(){assertThat(service.create(new CreateUserRequest("new","Name","  ","DEALER","ACTIVE","secret-value")).getEmail()).isNull();}
 @Test void rejectsInvalidInputs(){
  for(String role:List.of("ADMIN","MANAGER","COMPLIANCE_OFFICER","SURVEILLANCE_OFFICER","cashier"))assertThatThrownBy(()->service.create(new CreateUserRequest("new","Name",null,role,null,"secret-value"))).isInstanceOf(IllegalArgumentException.class);
  for(String password:List.of("short"," ".repeat(8),"é".repeat(37)))assertThatThrownBy(()->service.create(new CreateUserRequest("new","Name",null,"CASHIER",null,password))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.create(new CreateUserRequest(" ","Name",null,"CASHIER",null,"secret-value"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.create(new CreateUserRequest("new"," ",null,"CASHIER",null,"secret-value"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.create(new CreateUserRequest("new","Name","bad address","CASHIER",null,"secret-value"))).isInstanceOf(IllegalArgumentException.class);
  verify(users,never()).saveAndFlush(any());
 }
 @Test void uniquenessFailureIsConflictAndNotAudited(){when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("private database detail"));assertThatThrownBy(()->service.create(new CreateUserRequest("new","Name",null,"CASHIER",null,"secret-value"))).isInstanceOf(ResourceConflictException.class).hasMessageNotContaining("private");verifyNoInteractions(audit);}
 @Test void roleStatusAndResetHaveSafeEvents(){
  service.changeRole(target.getId(),new ChangeUserRoleRequest("DEALER","CASHIER"));assertThat(target.getRole()).isEqualTo("DEALER");
  service.changeStatus(target.getId(),new ChangeUserStatusRequest("INACTIVE","ACTIVE"));assertThat(target.getStatus()).isEqualTo("INACTIVE");
  service.changeStatus(target.getId(),new ChangeUserStatusRequest("ACTIVE","INACTIVE"));assertThat(target.getStatus()).isEqualTo("ACTIVE");
  service.resetPassword(target.getId(),new ResetUserPasswordRequest("replacement-secret"));assertThat(encoder.matches("replacement-secret",target.getPasswordHash())).isTrue();
  verify(audit).log(eq("USER_PASSWORD_RESET"),eq("USER_MANAGEMENT"),eq(target.getId()),eq(admin.getId()),eq("Password replaced; existing JWTs are not revoked."));
  verify(audit).log(eq("USER_ROLE_CHANGED"),anyString(),any(),any(),eq("role=CASHIER -> DEALER"));
 }
 @Test void ownResetAllowedButSelfRemovalForbidden(){service.resetPassword(admin.getId(),new ResetUserPasswordRequest("replacement-secret"));assertThatThrownBy(()->service.changeRole(admin.getId(),new ChangeUserRoleRequest("DEALER","SUPER_ADMIN"))).isInstanceOf(ResourceConflictException.class);assertThatThrownBy(()->service.changeStatus(admin.getId(),new ChangeUserStatusRequest("INACTIVE","ACTIVE"))).isInstanceOf(ResourceConflictException.class);}
 @Test void finalAdminProtectedForBothMutations(){target.setRole("SUPER_ADMIN");when(users.countActiveSuperAdmins()).thenReturn(1L);assertThatThrownBy(()->service.changeRole(target.getId(),new ChangeUserRoleRequest("DEALER","SUPER_ADMIN"))).isInstanceOf(ResourceConflictException.class);assertThatThrownBy(()->service.changeStatus(target.getId(),new ChangeUserStatusRequest("INACTIVE","ACTIVE"))).isInstanceOf(ResourceConflictException.class);verifyNoInteractions(audit);}
 @Test void actorRefreshedAfterGlobalLockAndStaleAuthorityRejected(){doAnswer(i->{admin.setRole("CASHIER");return null;}).when(em).refresh(admin,LockModeType.PESSIMISTIC_WRITE);assertThatThrownBy(()->service.resetPassword(target.getId(),new ResetUserPasswordRequest("replacement-secret"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);var order=inOrder(users,em);order.verify(users).lockAccountAdministration();order.verify(users).findByIdForUpdate(admin.getId());order.verify(em).refresh(admin,LockModeType.PESSIMISTIC_WRITE);verify(users,never()).saveAndFlush(any());}
 @Test void staleEditsRejected(){assertThatThrownBy(()->service.changeRole(target.getId(),new ChangeUserRoleRequest("DEALER","DIRECTOR"))).isInstanceOf(ResourceConflictException.class);assertThatThrownBy(()->service.changeStatus(target.getId(),new ChangeUserStatusRequest("INACTIVE","INACTIVE"))).isInstanceOf(ResourceConflictException.class);}
 @Test void directoryBoundedAndStaffBulkLoaded(){StaffProfile profile=new StaffProfile();profile.setId(UUID.randomUUID());profile.setUserId(target.getId());profile.setEmployeeCode("EMP-1");when(users.findByOrderByUsernameAscIdAsc(any())).thenReturn(List.of(target));when(staff.findByUserIdIn(any())).thenReturn(List.of(profile));assertThat(service.getAllUsers().getFirst().getStaff().employeeCode()).isEqualTo("EMP-1");verify(staff,never()).findByUserId(any());when(users.findByOrderByUsernameAscIdAsc(any())).thenReturn(Collections.nCopies(501,target));assertThatThrownBy(service::getAllUsers).isInstanceOf(ResourceConflictException.class);}
 @Test void readAndWriteRequireAdmin(){admin.setRole("DIRECTOR");assertThatThrownBy(service::getAllUsers).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThatThrownBy(()->service.resetPassword(target.getId(),new ResetUserPasswordRequest("replacement-secret"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
 @Test void passwordRequestsDoNotSerializeOrStringifySecrets()throws Exception{var request=new ResetUserPasswordRequest("replacement-secret");assertThat(request.toString()).doesNotContain("replacement-secret");assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request)).doesNotContain("replacement-secret");}
}
