package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HrStaffProfileServiceTests {
    private final StaffProfileRepository profiles=mock(StaffProfileRepository.class); private final UserRepository users=mock(UserRepository.class);
    private final DepartmentRepository departments=mock(DepartmentRepository.class); private final JobTitleRepository titles=mock(JobTitleRepository.class);
    private final AuthenticatedUserService auth=mock(AuthenticatedUserService.class); private final CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    private final AuditLogService audit=mock(AuditLogService.class); private final StaffProfileService service=new StaffProfileService(profiles,users,departments,titles,auth,roles,new RolePermissionService(),audit);
    private final UUID userId=UUID.randomUUID(),departmentId=UUID.randomUUID(),titleId=UUID.randomUUID(),actorId=UUID.randomUUID();

    @BeforeEach void setup(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));when(users.findById(userId)).thenReturn(Optional.of(user(userId,"employee")));when(departments.findById(departmentId)).thenReturn(Optional.of(department(true)));when(titles.findById(titleId)).thenReturn(Optional.of(title(true)));when(auth.getRequiredUser()).thenReturn(user(actorId,"director"));when(profiles.saveAndFlush(any())).thenAnswer(i->{StaffProfile v=i.getArgument(0);v.setId(UUID.randomUUID());return v;});}

    @Test void createsCanonicalProfileWithoutDuplicatingAuthenticationData(){StaffProfileResponse result=service.create(request(" emp-001 ",null));assertThat(result.employeeCode()).isEqualTo("EMP-001");verify(profiles).saveAndFlush(argThat(v->v.getUserId().equals(userId)&&v.getEmploymentStatus()==EmploymentStatus.ACTIVE));verify(audit).log(eq("CREATE_STAFF_PROFILE"),eq("HR"),any(),eq(actorId),contains("EMP-001"));}
    @Test void duplicateUserProfileRejected(){when(profiles.existsByUserId(userId)).thenReturn(true);assertThatThrownBy(()->service.create(request("EMP-001",null))).hasMessage("User already has a Staff Profile.");verify(profiles,never()).saveAndFlush(any());}
    @Test void duplicateEmployeeCodeRejected(){when(profiles.existsByEmployeeCodeIgnoreCase("EMP-001")).thenReturn(true);assertThatThrownBy(()->service.create(request("emp-001",null))).hasMessage("Employee code already exists.");}
    @Test void missingUserRejected(){when(users.findById(userId)).thenReturn(Optional.empty());assertThatThrownBy(()->service.create(request("EMP-001",null))).hasMessage("User not found.");}
    @Test void inactiveDepartmentAndTitleRejected(){when(departments.findById(departmentId)).thenReturn(Optional.of(department(false)));assertThatThrownBy(()->service.create(request("EMP-001",null))).hasMessage("Department must be ACTIVE.");when(departments.findById(departmentId)).thenReturn(Optional.of(department(true)));when(titles.findById(titleId)).thenReturn(Optional.of(title(false)));assertThatThrownBy(()->service.create(request("EMP-001",null))).hasMessage("Job Title must be ACTIVE.");}
    @Test void selfManagerRejectedOnUpdate(){UUID id=UUID.randomUUID();StaffProfile existing=new StaffProfile();existing.setId(id);when(profiles.findById(id)).thenReturn(Optional.of(existing));UpdateStaffProfileRequest update=new UpdateStaffProfileRequest(departmentId,titleId,EmploymentStatus.ACTIVE,EmploymentType.FULL_TIME,LocalDate.now(),null,id,null);assertThatThrownBy(()->service.update(id,update)).hasMessageContaining("cannot report to itself");}
    @Test void cashierDeniedAtServiceLayer(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));assertThatThrownBy(()->service.create(request("EMP-001",null))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(users);}
    @Test void candidatesUseSingleOrderedRepositoryQueryAndExposeOnlySafeFields(){User alpha=user(UUID.randomUUID(),"alpha"),beta=user(UUID.randomUUID(),"beta");alpha.setFullName("Alpha User");alpha.setRole("DEALER");alpha.setStatus("ACTIVE");beta.setFullName("Beta User");beta.setRole("CASHIER");beta.setStatus("INACTIVE");when(users.findUsersWithoutStaffProfileOrderByUsernameAsc()).thenReturn(List.of(alpha,beta));assertThat(service.candidates()).containsExactly(new HrStaffUserCandidateResponse(alpha.getId(),"alpha","Alpha User","DEALER","ACTIVE"),new HrStaffUserCandidateResponse(beta.getId(),"beta","Beta User","CASHIER","INACTIVE"));verify(users).findUsersWithoutStaffProfileOrderByUsernameAsc();verify(profiles,never()).existsByUserId(any());}
    @Test void noEligibleCandidatesReturnsEmptyList(){when(users.findUsersWithoutStaffProfileOrderByUsernameAsc()).thenReturn(List.of());assertThat(service.candidates()).isEmpty();}
    @Test void candidateDiscoveryRequiresHrManager(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));assertThatThrownBy(service::candidates).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(users,never()).findUsersWithoutStaffProfileOrderByUsernameAsc();}

    @Test void listBulkLoadsReferencesAndPreservesDetailContent() {
        StaffProfile manager = new StaffProfile();
        manager.setId(UUID.randomUUID()); manager.setUserId(actorId); manager.setEmployeeCode("MGR");
        StaffProfile first = new StaffProfile();
        first.setId(UUID.randomUUID()); first.setUserId(userId); first.setEmployeeCode("EMP-1");
        first.setDepartmentId(departmentId); first.setJobTitleId(titleId);
        first.setReportingManagerStaffProfileId(manager.getId());
        StaffProfile second = new StaffProfile();
        second.setId(UUID.randomUUID()); second.setUserId(actorId); second.setEmployeeCode("EMP-2");
        second.setDepartmentId(departmentId); second.setJobTitleId(titleId);
        when(profiles.findById(first.getId())).thenReturn(Optional.of(first));
        when(profiles.findById(second.getId())).thenReturn(Optional.of(second));
        when(profiles.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(users.findById(actorId)).thenReturn(Optional.of(user(actorId,"director")));
        var expected = List.of(service.get(first.getId()), service.get(second.getId()));
        when(profiles.findAllByOrderByEmployeeCodeAsc()).thenReturn(List.of(first,second));
        when(profiles.findAllById(Set.of(manager.getId()))).thenReturn(List.of(manager));
        when(users.findAllById(Set.of(userId,actorId))).thenReturn(List.of(user(actorId,"director"),user(userId,"employee")));
        when(departments.findAllById(Set.of(departmentId))).thenReturn(List.of(department(true)));
        when(titles.findAllById(Set.of(titleId))).thenReturn(List.of(title(true)));
        clearInvocations(profiles,users,departments,titles);
        assertThat(service.list()).containsExactlyElementsOf(expected);
        verify(profiles).findAllByOrderByEmployeeCodeAsc();
        verify(profiles).findAllById(Set.of(manager.getId()));
        verify(users).findAllById(Set.of(userId,actorId));
        verify(departments).findAllById(Set.of(departmentId));
        verify(titles).findAllById(Set.of(titleId));
        verifyNoMoreInteractions(profiles,users,departments,titles);
    }

    @Test void emptyListDoesNotLoadReferences() {
        assertThat(service.list()).isEmpty();
        verify(profiles).findAllByOrderByEmployeeCodeAsc();
        verifyNoMoreInteractions(profiles);
        verifyNoInteractions(users,departments,titles);
    }

    private CreateStaffProfileRequest request(String code,UUID manager){return new CreateStaffProfileRequest(userId,code,departmentId,titleId,EmploymentStatus.ACTIVE,EmploymentType.FULL_TIME,LocalDate.of(2026,9,9),null,manager,null);}
    private Department department(boolean active){Department v=new Department();v.setId(departmentId);v.setCode("HR");v.setName("HR");v.setActive(active);return v;}
    private JobTitle title(boolean active){JobTitle v=new JobTitle();v.setId(titleId);v.setCode("OFFICER");v.setName("Officer");v.setActive(active);return v;}
    private User user(UUID id,String username){User v=new User();v.setId(id);v.setUsername(username);v.setFullName(username);return v;}
}
