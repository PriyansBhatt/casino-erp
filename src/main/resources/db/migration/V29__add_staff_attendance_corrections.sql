create table core.staff_attendance_corrections (
    id uuid primary key,
    attendance_id uuid not null references core.staff_attendance(id),
    correction_type varchar(30) not null,
    previous_check_in_at timestamptz,
    new_check_in_at timestamptz,
    previous_check_out_at timestamptz,
    new_check_out_at timestamptz,
    previous_worked_minutes bigint,
    new_worked_minutes bigint,
    reason varchar(500) not null,
    corrected_by_user_id uuid not null references core.users(id),
    corrected_at timestamptz not null,
    constraint chk_staff_attendance_correction_type check (correction_type in (
        'CHECK_IN_TIME', 'CHECK_OUT_TIME', 'CHECK_IN_AND_OUT', 'MISSED_CHECKOUT'
    )),
    constraint chk_staff_attendance_correction_reason check (btrim(reason) <> ''),
    constraint chk_staff_attendance_correction_minutes check (
        (previous_worked_minutes is null or previous_worked_minutes >= 0)
        and (new_worked_minutes is null or new_worked_minutes >= 0)
    ),
    constraint chk_staff_attendance_correction_values check (
        (correction_type = 'CHECK_IN_TIME'
            and previous_check_in_at is not null and new_check_in_at is not null
            and previous_check_out_at is null and new_check_out_at is null)
        or (correction_type = 'CHECK_OUT_TIME'
            and previous_check_in_at is null and new_check_in_at is null
            and previous_check_out_at is not null and new_check_out_at is not null)
        or (correction_type = 'CHECK_IN_AND_OUT'
            and previous_check_in_at is not null and new_check_in_at is not null
            and previous_check_out_at is not null and new_check_out_at is not null)
        or (correction_type = 'MISSED_CHECKOUT'
            and previous_check_in_at is null and new_check_in_at is null
            and previous_check_out_at is null and new_check_out_at is not null)
    )
);

create index ix_staff_attendance_corrections_attendance_time
    on core.staff_attendance_corrections (attendance_id, corrected_at);

create index ix_staff_attendance_corrections_actor
    on core.staff_attendance_corrections (corrected_by_user_id);
