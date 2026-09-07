create table core.staff_attendance (
    id uuid primary key,
    user_id uuid not null references core.users(id),
    business_date date not null,
    status varchar(20) not null,
    check_in_at timestamptz not null,
    check_out_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_staff_attendance_status check (status in ('OPEN', 'CLOSED')),
    constraint chk_staff_attendance_checkout_after_checkin
        check (check_out_at is null or check_out_at > check_in_at),
    constraint chk_staff_attendance_lifecycle
        check ((status = 'OPEN' and check_out_at is null)
            or (status = 'CLOSED' and check_out_at is not null))
);

create unique index uq_staff_attendance_open_user
    on core.staff_attendance (user_id)
    where status = 'OPEN';

create index ix_staff_attendance_business_date
    on core.staff_attendance (business_date);

create index ix_staff_attendance_user
    on core.staff_attendance (user_id);

create index ix_staff_attendance_status
    on core.staff_attendance (status);

create index ix_staff_attendance_check_in
    on core.staff_attendance (check_in_at);
