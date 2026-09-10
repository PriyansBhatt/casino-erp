package com.casino.casinoerp;

import com.casino.casinoerp.repository.StaffProfileRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffLeaveConcurrencyTests {
    @Test void leaveCreationSerializesOnStaffProfileRow() throws Exception {
        Lock lock = StaffProfileRepository.class.getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
