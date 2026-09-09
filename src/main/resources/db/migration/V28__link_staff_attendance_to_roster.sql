alter table core.staff_attendance
    add column roster_assignment_id uuid references casino.staff_roster_assignments(id),
    add column roster_date date,
    add column shift_code varchar(50),
    add column shift_name varchar(150),
    add column scheduled_start_at timestamptz,
    add column scheduled_end_at timestamptz,
    add column roster_late_grace_minutes integer,
    add column roster_early_check_in_minutes integer,
    add constraint chk_staff_attendance_roster_snapshot check (
        (roster_assignment_id is null
            and roster_date is null
            and shift_code is null
            and shift_name is null
            and scheduled_start_at is null
            and scheduled_end_at is null
            and roster_late_grace_minutes is null
            and roster_early_check_in_minutes is null)
        or
        (roster_assignment_id is not null
            and roster_date is not null
            and shift_code is not null and btrim(shift_code) <> ''
            and shift_name is not null and btrim(shift_name) <> ''
            and scheduled_start_at is not null
            and scheduled_end_at is not null
            and scheduled_end_at > scheduled_start_at
            and roster_late_grace_minutes is not null and roster_late_grace_minutes >= 0
            and roster_early_check_in_minutes is not null and roster_early_check_in_minutes >= 0)
    );

create index ix_staff_attendance_roster_assignment
    on core.staff_attendance (roster_assignment_id)
    where roster_assignment_id is not null;
