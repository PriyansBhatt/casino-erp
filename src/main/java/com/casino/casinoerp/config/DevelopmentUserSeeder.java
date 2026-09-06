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
    static final String PIT_SUPERVISOR_USERNAME = "pitsupervisor";
    static final String PIT_SUPERVISOR_PASSWORD = "pitsupervisor123";
    static final String DEALER_USERNAME = "dealer";
    static final String DEALER_PASSWORD = "dealer123";
    static final String SECOND_DEALER_USERNAME = "dealer2";
    static final String SECOND_DEALER_PASSWORD = "dealer2123";

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

    @Bean
    ApplicationRunner developmentPitSupervisorSeeder() {
        return args -> {
            if (userRepository.findByUsername(PIT_SUPERVISOR_USERNAME) != null) {
                return;
            }

            User pitSupervisor = new User();
            pitSupervisor.setId(UUID.randomUUID());
            pitSupervisor.setUsername(PIT_SUPERVISOR_USERNAME);
            pitSupervisor.setPasswordHash(passwordEncoder.encode(PIT_SUPERVISOR_PASSWORD));
            pitSupervisor.setFullName("Development Pit Supervisor");
            pitSupervisor.setEmail("pitsupervisor@localhost");
            pitSupervisor.setStatus("ACTIVE");
            pitSupervisor.setRole(Role.PIT_SUPERVISOR.name());
            pitSupervisor.setCreatedAt(LocalDateTime.now());

            userRepository.save(pitSupervisor);
        };
    }

    @Bean
    ApplicationRunner developmentDealerSeeder() {
        return args -> {
            if (userRepository.findByUsername(DEALER_USERNAME) != null) {
                return;
            }

            User dealer = new User();
            dealer.setId(UUID.randomUUID());
            dealer.setUsername(DEALER_USERNAME);
            dealer.setPasswordHash(passwordEncoder.encode(DEALER_PASSWORD));
            dealer.setFullName("Development Dealer");
            dealer.setEmail("dealer@localhost");
            dealer.setStatus("ACTIVE");
            dealer.setRole(Role.DEALER.name());
            dealer.setCreatedAt(LocalDateTime.now());

            userRepository.save(dealer);
        };
    }

    @Bean
    ApplicationRunner developmentSecondDealerSeeder() {
        return args -> {
            if (userRepository.findByUsername(SECOND_DEALER_USERNAME) != null) {
                return;
            }

            User dealer = new User();
            dealer.setId(UUID.randomUUID());
            dealer.setUsername(SECOND_DEALER_USERNAME);
            dealer.setPasswordHash(passwordEncoder.encode(SECOND_DEALER_PASSWORD));
            dealer.setFullName("Development Dealer 2");
            dealer.setEmail("dealer2@localhost");
            dealer.setStatus("ACTIVE");
            dealer.setRole(Role.DEALER.name());
            dealer.setCreatedAt(LocalDateTime.now());

            userRepository.save(dealer);
        };
    }
}
