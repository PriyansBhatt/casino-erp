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
    User findByUsername(String username);
    List<User> findByStatusIgnoreCaseOrderByUsernameAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    java.util.Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}
