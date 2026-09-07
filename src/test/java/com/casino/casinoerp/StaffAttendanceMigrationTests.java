package com.casino.casinoerp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAttendanceMigrationTests {
    @Test
    void migrationEnforcesOpenShiftUniquenessAndTimestampInvariant() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V23__add_staff_attendance.sql"));

        assertThat(sql).contains("where status = 'OPEN'");
        assertThat(sql).contains("check_out_at is null or check_out_at > check_in_at");
        assertThat(sql).contains("timestamptz");
        assertThat(sql).contains("references core.users(id)");
    }
}
