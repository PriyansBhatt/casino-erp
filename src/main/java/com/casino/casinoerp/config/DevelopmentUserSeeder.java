package com.casino.casinoerp.config;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile("dev")
public class DevelopmentUserSeeder implements ApplicationRunner {

    static final String RECEPTION_USERNAME = "reception";
    static final String RECEPTION_PASSWORD = "reception123";
    static final String CASHIER_USERNAME = "cashier";
    static final String CASHIER_PASSWORD = "cashier123";
    static final String SUPER_ADMIN_USERNAME = "superadmin";
    static final String SUPER_ADMIN_PASSWORD = "superadmin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(RECEPTION_USERNAME) != null) {
            return;
        }

        User receptionist = new User();
        receptionist.setId(UUID.randomUUID());
        receptionist.setUsername(RECEPTION_USERNAME);
        receptionist.setPasswordHash(passwordEncoder.encode(RECEPTION_PASSWORD));
        receptionist.setFullName("Development Reception User");
        receptionist.setEmail("reception@localhost");
        receptionist.setStatus("ACTIVE");
        receptionist.setRole(Role.RECEPTIONIST.name());
        receptionist.setCreatedAt(LocalDateTime.now());

        userRepository.save(receptionist);
    }

    @Bean
    ApplicationRunner developmentCashierSeeder() {
        return args -> {
            if (userRepository.findByUsername(CASHIER_USERNAME) != null) {
                return;
            }

            User cashier = new User();
            cashier.setId(UUID.randomUUID());
            cashier.setUsername(CASHIER_USERNAME);
            cashier.setPasswordHash(passwordEncoder.encode(CASHIER_PASSWORD));
            cashier.setFullName("Development Cashier User");
            cashier.setEmail("cashier@localhost");
            cashier.setStatus("ACTIVE");
            cashier.setRole(Role.CASHIER.name());
            cashier.setCreatedAt(LocalDateTime.now());

            userRepository.save(cashier);
        };
    }

    @Bean
    ApplicationRunner developmentSuperAdminSeeder() {
        return args -> {
            if (userRepository.findByUsername(SUPER_ADMIN_USERNAME) != null) {
                return;
            }

            User superAdmin = new User();
            superAdmin.setId(UUID.randomUUID());
            superAdmin.setUsername(SUPER_ADMIN_USERNAME);
            superAdmin.setPasswordHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
            superAdmin.setFullName("Development Super Admin");
            superAdmin.setEmail("superadmin@localhost");
            superAdmin.setStatus("ACTIVE");
            superAdmin.setRole(Role.SUPER_ADMIN.name());
            superAdmin.setCreatedAt(LocalDateTime.now());

            userRepository.save(superAdmin);
        };
    }
}
