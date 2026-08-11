package com.casino.casinoerp.config;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
}
