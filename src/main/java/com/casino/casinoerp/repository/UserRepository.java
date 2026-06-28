package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByUsername(String username);
}