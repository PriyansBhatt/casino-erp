package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {
    public static final int DIRECTORY_LIMIT = 500;
    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "DIRECTOR", "RECEPTIONIST", "CASHIER", "PIT_SUPERVISOR", "DEALER", "STORE_MANAGER", "ACCOUNTANT_HEAD", "ACCOUNTS_MANAGER");
    private final UserRepository users;
    private final StaffProfileRepository staff;
    private final AuthenticatedUserService actors;
    private final PasswordEncoder passwords;
    private final AuditLogService audit;
    private final EntityManager entityManager;
    private final jakarta.validation.Validator validator;

    public UserService(UserRepository users, StaffProfileRepository staff, AuthenticatedUserService actors,
                       PasswordEncoder passwords, AuditLogService audit, EntityManager entityManager, jakarta.validation.Validator validator) {
        this.users=users; this.staff=staff; this.actors=actors; this.passwords=passwords;
        this.audit=audit; this.entityManager=entityManager; this.validator=validator;
    }

    @Transactional(readOnly=true)
    public List<UserResponse> getAllUsers() {
        requireAdmin(actors.getRequiredUser());
        var rows=users.findByOrderByUsernameAscIdAsc(PageRequest.of(0,DIRECTORY_LIMIT+1));
        if(rows.size()>DIRECTORY_LIMIT) throw new ResourceConflictException("User directory exceeds 500 accounts; a paged directory is required.");
        if(rows.isEmpty()) return List.of();
        var links=staff.findByUserIdIn(rows.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(com.casino.casinoerp.entity.StaffProfile::getUserId,
                        p->new UserResponse.StaffLink(p.getId(),p.getEmployeeCode())));
        return rows.stream().map(u->response(u,links.get(u.getId()))).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        User actor=lockActor();
        User target=new User(); target.setId(UUID.randomUUID());
        target.setUsername(requiredText(request.username(),100,"Username"));
        // Login currently caps usernames at 50; creation must not produce an unusable login.
        if(target.getUsername().length()>50) throw new IllegalArgumentException("Username must not exceed the login limit of 50 characters.");
        target.setFullName(requiredText(request.fullName(),150,"Full name"));
        target.setEmail(email(request.email())); target.setRole(role(request.role()));
        target.setStatus(request.status()==null ? "ACTIVE" : status(request.status()));
        target.setPasswordHash(passwords.encode(password(request.password())));
        target.setCreatedAt(LocalDateTime.now()); target.setUpdatedAt(target.getCreatedAt());
        persist(target);
        audit.log("USER_CREATED","USER_MANAGEMENT",target.getId(),actor.getId(),
                "role="+target.getRole()+"; status="+target.getStatus());
        return response(target,null);
    }

    @Transactional
    public UserResponse changeRole(UUID id, ChangeUserRoleRequest request) {
        User actor=lockActor(); User target=locked(id); String next=role(request.role());
        if(!Objects.equals(target.getRole(),request.expectedRole())) throw conflict("Role changed; refresh before editing.");
        if(actor.getId().equals(id) && !"SUPER_ADMIN".equals(next)) throw conflict("You cannot demote your own account.");
        protectFinalAdmin(target,next,target.getStatus());
        String before=target.getRole(); target.setRole(next);
        return changed(target,actor,"USER_ROLE_CHANGED","role="+before+" -> "+next);
    }

    @Transactional
    public UserResponse changeStatus(UUID id, ChangeUserStatusRequest request) {
        User actor=lockActor(); User target=locked(id); String next=status(request.status());
        if(!Objects.equals(target.getStatus(),request.expectedStatus())) throw conflict("Status changed; refresh before editing.");
        if(actor.getId().equals(id) && !"ACTIVE".equals(next)) throw conflict("You cannot deactivate your own account.");
        protectFinalAdmin(target,target.getRole(),next);
        String before=target.getStatus(); target.setStatus(next);
        return changed(target,actor,"ACTIVE".equals(next)?"USER_ACTIVATED":"USER_DEACTIVATED","status="+before+" -> "+next);
    }

    @Transactional
    public UserResponse resetPassword(UUID id, ResetUserPasswordRequest request) {
        User actor=lockActor(); User target=locked(id);
        target.setPasswordHash(passwords.encode(password(request.password())));
        return changed(target,actor,"USER_PASSWORD_RESET","Password replaced; existing JWTs are not revoked.");
    }

    private User lockActor() {
        User snapshot=actors.getRequiredUser(); requireAdmin(snapshot);
        users.lockAccountAdministration();
        // OSIV/JPA may already contain the JWT filter's account. Refresh after locking.
        User actor=locked(snapshot.getId()); requireAdmin(actor); return actor;
    }
    private User locked(UUID id) {
        User user=users.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("User not found."));
        entityManager.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    private void requireAdmin(User user) {
        if(AuthenticatedUserService.requireActiveRole(user)!=Role.SUPER_ADMIN) throw new AccessDeniedException("Only SUPER_ADMIN may manage users.");
    }
    private void protectFinalAdmin(User target,String nextRole,String nextStatus) {
        boolean currentlyAdmin="ACTIVE".equalsIgnoreCase(target.getStatus()) && Role.fromValue(target.getRole()).orElse(null)==Role.SUPER_ADMIN;
        boolean remainsAdmin="ACTIVE".equalsIgnoreCase(nextStatus) && Role.fromValue(nextRole).orElse(null)==Role.SUPER_ADMIN;
        if(currentlyAdmin && !remainsAdmin && users.countActiveSuperAdmins()<=1) throw conflict("At least one ACTIVE SUPER_ADMIN must remain.");
    }
    private UserResponse changed(User target,User actor,String event,String detail) {
        target.setUpdatedAt(LocalDateTime.now()); persist(target);
        audit.log(event,"USER_MANAGEMENT",target.getId(),actor.getId(),detail);
        var link=staff.findByUserId(target.getId()).map(p->new UserResponse.StaffLink(p.getId(),p.getEmployeeCode())).orElse(null);
        return response(target,link);
    }
    private void persist(User user) {
        try { users.saveAndFlush(user); }
        catch(DataIntegrityViolationException ex) { throw conflict("Username or email conflicts with an existing account."); }
    }
    private static ResourceConflictException conflict(String message) { return new ResourceConflictException(message); }
    private static String role(String value) {
        if(!ROLES.contains(value==null?"":value)) throw new IllegalArgumentException("Choose an approved User Management role."); return value;
    }
    private static String status(String value) {
        if(!Set.of("ACTIVE","INACTIVE").contains(value==null?"":value)) throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE."); return value;
    }
    private static String requiredText(String value,int max,String label) {
        if(value==null || value.trim().isEmpty() || value.trim().length()>max) throw new IllegalArgumentException(label+" is required and must not exceed "+max+" characters."); return value.trim();
    }
    private record EmailInput(@jakarta.validation.constraints.Email String value) {}
    private String email(String value) {
        if(value==null || value.trim().isEmpty()) return null;
        String normalized=value.trim();
        if(normalized.length()>150 || !validator.validate(new EmailInput(normalized)).isEmpty())
            throw new IllegalArgumentException("A valid email address is required.");
        return normalized;
    }
    private static String password(String value) {
        if(value==null || value.isBlank() || value.length()<6 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Password must contain at least 6 characters and at most 72 UTF-8 bytes.");
        return value;
    }
    private UserResponse response(User u,UserResponse.StaffLink link) {
        return new UserResponse(u.getId(),u.getUsername(),u.getFullName(),u.getEmail(),u.getStatus(),u.getRole(),u.getCreatedAt(),u.getUpdatedAt(),link);
    }
}
