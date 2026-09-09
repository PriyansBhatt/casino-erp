package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HrMasterDataServiceTests {
    private final DepartmentRepository departments=mock(DepartmentRepository.class); private final JobTitleRepository titles=mock(JobTitleRepository.class);
    private final AuthenticatedUserService auth=mock(AuthenticatedUserService.class); private final CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    private final AuditLogService audit=mock(AuditLogService.class); private final HrMasterDataService service=new HrMasterDataService(departments,titles,auth,roles,new RolePermissionService(),audit);
    private final UUID actorId=UUID.randomUUID();
    @BeforeEach void setup(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));User actor=new User();actor.setId(actorId);when(auth.getRequiredUser()).thenReturn(actor);when(departments.saveAndFlush(any())).thenAnswer(i->{Department v=i.getArgument(0);v.setId(UUID.randomUUID());return v;});when(titles.saveAndFlush(any())).thenAnswer(i->{JobTitle v=i.getArgument(0);v.setId(UUID.randomUUID());return v;});}
    @Test void createsCanonicalDepartmentAndAudits(){var result=service.createDepartment(new HrMasterDataRequest(" hr "," Human Resources ",null,null,10));assertThat(result.code()).isEqualTo("HR");assertThat(result.active()).isTrue();verify(audit).log(eq("CREATE_DEPARTMENT"),eq("HR"),any(),eq(actorId),contains("HR"));}
    @Test void duplicateDepartmentRejected(){when(departments.existsByCodeIgnoreCase("HR")).thenReturn(true);assertThatThrownBy(()->service.createDepartment(new HrMasterDataRequest("hr","HR",null,true,null))).hasMessage("Department code already exists.");}
    @Test void updatesAndDeactivatesDepartmentWithoutChangingCode(){UUID id=UUID.randomUUID();Department value=new Department();value.setId(id);value.setCode("HR");value.setName("Old");value.setActive(true);when(departments.findById(id)).thenReturn(Optional.of(value));when(departments.save(any())).thenAnswer(i->i.getArgument(0));var result=service.updateDepartment(id,new HrMasterDataUpdateRequest("Human Resources",null,false,20));assertThat(result.code()).isEqualTo("HR");assertThat(result.active()).isFalse();}
    @Test void createsAndRejectsDuplicateJobTitle(){service.createJobTitle(new HrMasterDataRequest(" pit_supervisor ","Pit Supervisor",null,true,null));verify(titles).saveAndFlush(argThat(v->v.getCode().equals("PIT_SUPERVISOR")));when(titles.existsByCodeIgnoreCase("DEALER")).thenReturn(true);assertThatThrownBy(()->service.createJobTitle(new HrMasterDataRequest("dealer","Dealer",null,true,null))).hasMessage("Job Title code already exists.");}
    @Test void cashierCannotManageMasterData(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));assertThatThrownBy(()->service.departments()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(departments);}
}
