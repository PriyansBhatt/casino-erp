package com.casino.casinoerp.config;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentUserSeederTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DevelopmentUserSeeder seeder =
            new DevelopmentUserSeeder(userRepository, passwordEncoder);

    @Test
    void createsCanonicalReceptionistWhenDevelopmentUserDoesNotExist() throws Exception {
        when(userRepository.findByUsername(DevelopmentUserSeeder.RECEPTION_USERNAME)).thenReturn(null);
        when(passwordEncoder.encode(DevelopmentUserSeeder.RECEPTION_PASSWORD)).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getId() != null
                        && DevelopmentUserSeeder.RECEPTION_USERNAME.equals(user.getUsername())
                        && "bcrypt-hash".equals(user.getPasswordHash())
                        && "ACTIVE".equals(user.getStatus())
                        && Role.RECEPTIONIST.name().equals(user.getRole())
                        && user.getCreatedAt() != null
        ));
    }

    @Test
    void preservesExistingDevelopmentUserWithoutChangingPassword() throws Exception {
        User existing = new User();
        existing.setUsername(DevelopmentUserSeeder.RECEPTION_USERNAME);
        existing.setPasswordHash("existing-hash");
        when(userRepository.findByUsername(DevelopmentUserSeeder.RECEPTION_USERNAME)).thenReturn(existing);

        seeder.run(new DefaultApplicationArguments());

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void preparesCanonicalPitSupervisorDevelopmentUserIdempotently() throws Exception {
        when(userRepository.findByUsername(DevelopmentUserSeeder.PIT_SUPERVISOR_USERNAME)).thenReturn(null);
        when(passwordEncoder.encode(DevelopmentUserSeeder.PIT_SUPERVISOR_PASSWORD)).thenReturn("pit-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.developmentPitSupervisorSeeder().run(new DefaultApplicationArguments());

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                DevelopmentUserSeeder.PIT_SUPERVISOR_USERNAME.equals(user.getUsername())
                        && "pit-hash".equals(user.getPasswordHash())
                        && Role.PIT_SUPERVISOR.name().equals(user.getRole())
                        && "ACTIVE".equals(user.getStatus())));
    }

    @Test
    void preparesCanonicalDealerDevelopmentUserIdempotently() throws Exception {
        when(userRepository.findByUsername(DevelopmentUserSeeder.DEALER_USERNAME)).thenReturn(null);
        when(passwordEncoder.encode(DevelopmentUserSeeder.DEALER_PASSWORD)).thenReturn("dealer-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.developmentDealerSeeder().run(new DefaultApplicationArguments());

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                DevelopmentUserSeeder.DEALER_USERNAME.equals(user.getUsername())
                        && "dealer-hash".equals(user.getPasswordHash())
                        && Role.DEALER.name().equals(user.getRole())
                        && "ACTIVE".equals(user.getStatus())));
    }

    @Test
    void createsCanonicalSecondDealerWhenAbsent() throws Exception {
        when(userRepository.findByUsername(DevelopmentUserSeeder.SECOND_DEALER_USERNAME)).thenReturn(null);
        when(passwordEncoder.encode(DevelopmentUserSeeder.SECOND_DEALER_PASSWORD)).thenReturn("dealer2-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.developmentSecondDealerSeeder().run(new DefaultApplicationArguments());

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getId() != null
                        && DevelopmentUserSeeder.SECOND_DEALER_USERNAME.equals(user.getUsername())
                        && "dealer2-hash".equals(user.getPasswordHash())
                        && "Development Dealer 2".equals(user.getFullName())
                        && "dealer2@localhost".equals(user.getEmail())
                        && Role.DEALER.name().equals(user.getRole())
                        && "ACTIVE".equals(user.getStatus())
                        && user.getCreatedAt() != null));
    }

    @Test
    void preservesExistingSecondDealerWithoutChangingPasswordOrSaving() throws Exception {
        User existing = new User();
        existing.setUsername(DevelopmentUserSeeder.SECOND_DEALER_USERNAME);
        existing.setPasswordHash("existing-dealer2-hash");
        when(userRepository.findByUsername(DevelopmentUserSeeder.SECOND_DEALER_USERNAME)).thenReturn(existing);

        seeder.developmentSecondDealerSeeder().run(new DefaultApplicationArguments());

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-dealer2-hash");
    }

    @Test
    void developmentSeederIsRestrictedToDevProfile() {
        Profile profile = DevelopmentUserSeeder.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
    }
}
