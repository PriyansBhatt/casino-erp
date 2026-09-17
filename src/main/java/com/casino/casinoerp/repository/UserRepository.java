package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, UUID> {
    // All A1 writers acquire this transaction-scoped lock before any account row lock.
    @Query(value = "select 1 from pg_advisory_xact_lock(42424220260917)", nativeQuery = true)
    int lockAccountAdministration();

    @Query(value = """
            select count(*) from core.users where upper(status) = 'ACTIVE'
            and regexp_replace(regexp_replace(upper(btrim(role)), '[[:space:]-]+', '_', 'g'), '_+', '_', 'g') = 'SUPER_ADMIN'
            """, nativeQuery = true)
    long countActiveSuperAdmins();

    List<User> findByOrderByUsernameAscIdAsc(org.springframework.data.domain.Pageable pageable);

    User findByUsername(String username);
    List<User> findByStatusIgnoreCaseOrderByUsernameAsc(String status);

    @Query("""
            select user from User user
            where not exists (
                select staff.id from StaffProfile staff where staff.userId = user.id
            )
            order by user.username asc, user.id asc
            """)
    List<User> findUsersWithoutStaffProfileOrderByUsernameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    java.util.Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}
