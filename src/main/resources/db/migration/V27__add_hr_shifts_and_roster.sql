create table casino.shift_definitions (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(150) not null,
    description varchar(1000),
    start_time time not null,
    end_time time not null,
    crosses_midnight boolean not null,
    late_grace_minutes integer not null,
    early_check_in_minutes integer not null,
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_shift_definitions_code unique (code),
    constraint chk_shift_definitions_code_canonical
        check (code = upper(btrim(code)) and code <> ''),
    constraint chk_shift_definitions_name_not_blank check (btrim(name) <> ''),
    constraint chk_shift_definitions_grace_nonnegative
        check (late_grace_minutes >= 0 and early_check_in_minutes >= 0),
    constraint chk_shift_definitions_time_range check (
        start_time <> end_time
        and ((start_time < end_time and crosses_midnight = false)
          or (end_time < start_time and crosses_midnight = true))
    )
);

create table casino.staff_roster_assignments (
    id uuid primary key,
    staff_profile_id uuid not null references casino.staff_profiles(id),
    shift_definition_id uuid not null references casino.shift_definitions(id),
    roster_date date not null,
    status varchar(20) not null,
    remarks varchar(1000),
    cancellation_reason varchar(1000),
    cancelled_by uuid references core.users(id),
    cancelled_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint chk_staff_roster_status check (status in ('SCHEDULED', 'CANCELLED')),
    constraint chk_staff_roster_cancellation check (
        (status = 'SCHEDULED' and cancellation_reason is null and cancelled_by is null and cancelled_at is null)
        or (status = 'CANCELLED' and cancellation_reason is not null and btrim(cancellation_reason) <> ''
            and cancelled_by is not null and cancelled_at is not null)
    )
);

create index ix_shift_definitions_active_name
    on casino.shift_definitions (active, name);

create unique index uq_staff_roster_scheduled_staff_date
    on casino.staff_roster_assignments (staff_profile_id, roster_date)
    where status = 'SCHEDULED';

create index ix_staff_roster_date on casino.staff_roster_assignments (roster_date);
create index ix_staff_roster_shift on casino.staff_roster_assignments (shift_definition_id);
create index ix_staff_roster_status on casino.staff_roster_assignments (status);
