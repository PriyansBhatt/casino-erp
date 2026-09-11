package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.util.*;

public interface StaffRosterAssignmentRepository extends JpaRepository<StaffRosterAssignment,UUID>, JpaSpecificationExecutor<StaffRosterAssignment> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roster from StaffRosterAssignment roster where roster.id = :id")
    Optional<StaffRosterAssignment> findByIdForUpdate(@Param("id") UUID id);

    List<StaffRosterAssignment> findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
            UUID staffProfileId,RosterStatus status,LocalDate from,LocalDate to);

    // Omit absent filters so every bound value has a typed column comparison.
    default List<StaffRosterAssignment> search(LocalDate fromDate, LocalDate toDate,
            UUID staffProfileId, UUID departmentId, UUID shiftId, RosterStatus status) {
        return findAll((roster, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(roster.get("rosterDate"), fromDate));
            if (toDate != null) predicates.add(cb.lessThanOrEqualTo(roster.get("rosterDate"), toDate));
            if (staffProfileId != null) predicates.add(cb.equal(roster.get("staffProfileId"), staffProfileId));
            if (shiftId != null) predicates.add(cb.equal(roster.get("shiftDefinitionId"), shiftId));
            if (status != null) predicates.add(cb.equal(roster.get("status"), status));
            if (departmentId != null) {
                var staffIds = query.subquery(UUID.class);
                var staff = staffIds.from(StaffProfile.class);
                staffIds.select(staff.get("id")).where(cb.equal(staff.get("departmentId"), departmentId));
                predicates.add(roster.get("staffProfileId").in(staffIds));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, Sort.by("rosterDate", "createdAt"));
    }
}
