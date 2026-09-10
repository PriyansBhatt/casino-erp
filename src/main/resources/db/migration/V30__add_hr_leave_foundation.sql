create table casino.leave_types (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(150) not null,
    description varchar(1000),
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_leave_types_code unique (code),
    constraint chk_leave_types_code_canonical
        check (code = upper(btrim(code)) and code <> ''),
    constraint chk_leave_types_name_not_blank check (btrim(name) <> '')
);

create table casino.staff_leave_requests (
    id uuid primary key,
    staff_profile_id uuid not null references casino.staff_profiles(id),
    leave_type_id uuid not null references casino.leave_types(id),
    start_date date not null,
    end_date date not null,
    reason varchar(1000) not null,
    status varchar(20) not null,
    submitted_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint chk_staff_leave_request_dates check (end_date >= start_date),
    constraint chk_staff_leave_request_reason check (btrim(reason) <> ''),
    constraint chk_staff_leave_request_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

create index ix_staff_leave_requests_staff_dates
    on casino.staff_leave_requests (staff_profile_id, start_date, end_date);

create index ix_staff_leave_requests_status_dates
    on casino.staff_leave_requests (status, start_date, end_date);

create index ix_staff_leave_requests_leave_type
    on casino.staff_leave_requests (leave_type_id);
