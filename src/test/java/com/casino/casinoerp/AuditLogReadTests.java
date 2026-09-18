package com.casino.casinoerp;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuditLogReadTests {
 @Test void validatesBoundsAndPassesCombinedFilters() {
  var reads=mock(AuditLogReadRepository.class);var roles=mock(CurrentUserRoleService.class);when(roles.getCurrentUserRole()).thenReturn("DIRECTOR");
  var service=new AuditLogReadService(reads,roles,new RolePermissionService());var actor=UUID.randomUUID();var from=LocalDateTime.of(2026,9,1,9,0);var date=from.toLocalDate();
  service.read(3,100,from,from.plusDays(1),date," USER_CREATED "," USER_MANAGEMENT ",actor," root ");
  verify(reads).read(3,100,from,from.plusDays(1),date,"USER_CREATED","USER_MANAGEMENT",actor,"root");
  assertThatThrownBy(()->service.read(0,50,null,null,null,null,null,null,"x".repeat(201))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void serviceAlsoEnforcesManagementPolicy() {
  var reads=mock(AuditLogReadRepository.class);var roles=mock(CurrentUserRoleService.class);when(roles.getCurrentUserRole()).thenReturn("COMPLIANCE_OFFICER");
  assertThatThrownBy(()->new AuditLogReadService(reads,roles,new RolePermissionService()).read(0,50,null,null,null,null,null,null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(reads);
 }
 @Test void detailsDefaultWithheldAndSafeActionsExplicit() {
  for(String action:List.of("USER_ROLE_CHANGED","USER_ACTIVATED","USER_DEACTIVATED","SYSTEM_UNLOCK","CORRECT_STAFF_ATTENDANCE","REJECT_LEAVE_REQUEST","CANCEL_LEAVE_REQUEST","APPROVE_LEAVE_REQUEST","CANCEL_STAFF_ROSTER","LEGACY_SESSION_CUSTODY_CORRECTION","LEGACY_CASH_ACTOR_BUCKET_RESOLVED","LEGACY_PIT_TABLE_RECONCILIATION_RESOLVED","FUTURE_EVENT"))assertThat(AuditDetailsPolicy.allows(action)).isFalse();
  assertThat(AuditDetailsPolicy.allows(null)).isFalse();assertThat(AuditDetailsPolicy.allows("USER_PASSWORD_RESET")).isTrue();
 }
 @Test void explicitBusinessDateNeverResolvesCurrentDate() {
  var repo=mock(AuditLogRepository.class);var dates=mock(BusinessDateService.class);var actor=UUID.randomUUID();var target=UUID.randomUUID();var day=LocalDate.of(2026,9,2);
  when(repo.save(any())).thenAnswer(i->i.getArgument(0));
  var service=new AuditLogService(repo,dates,new RolePermissionService(),mock(CurrentUserRoleService.class));
  var row=service.logForBusinessDate(day,"CLOSE_BUSINESS_DATE","BUSINESS_DATE",target,actor,"closed");
  assertThat(row.getBusinessDate()).isEqualTo(day);assertThat(row.getPerformedBy()).isEqualTo(actor);verifyNoInteractions(dates);
 }
 @Test void existingSummaryUsesOnlyAuthorizedDateCount() {
  var repo=mock(AuditLogRepository.class);var dates=mock(BusinessDateService.class);var roles=mock(CurrentUserRoleService.class);
  var day=LocalDate.of(2026,9,2);when(roles.getCurrentUserRole()).thenReturn("DIRECTOR");when(repo.countByBusinessDate(day)).thenReturn(12L);
  var service=new AuditLogService(repo,dates,new RolePermissionService(),roles);
  assertThat(service.countByBusinessDate(day)).isEqualTo(12);verify(repo).countByBusinessDate(day);verifyNoMoreInteractions(repo);
 }
}
