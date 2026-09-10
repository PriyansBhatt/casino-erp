package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.StaffAttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface StaffAttendanceCorrectionRepository extends JpaRepository<StaffAttendanceCorrection, UUID> {
    List<StaffAttendanceCorrection> findByAttendanceIdOrderByCorrectedAtAsc(UUID attendanceId);
}
