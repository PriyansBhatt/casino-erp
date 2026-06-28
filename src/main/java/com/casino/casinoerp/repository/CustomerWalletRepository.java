package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerWalletRepository extends JpaRepository<CustomerWallet, UUID> {

    Optional<CustomerWallet> findByCustomerId(UUID customerId);

}