package com.casino.casinoerp;

import com.casino.casinoerp.entity.StaffAttendanceStatus;
import com.casino.casinoerp.repository.StaffAttendanceRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAttendanceCorrectionConcurrencyTests {

    @Test void correctionAndSelfCheckoutSerializeOnTheSameAttendanceRow() throws Exception {
        Lock correctionLock = StaffAttendanceRepository.class
                .getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class);
        Lock checkoutLock = StaffAttendanceRepository.class
                .getMethod("findOpenByUserIdForUpdate", UUID.class, StaffAttendanceStatus.class)
                .getAnnotation(Lock.class);

        assertThat(correctionLock).isNotNull();
        assertThat(correctionLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(checkoutLock).isNotNull();
        assertThat(checkoutLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
