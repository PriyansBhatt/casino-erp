create table casino.pit_table_staff_assignments (
    id uuid primary key,
    pit_table_id uuid not null references casino.pit_tables(id),
    staff_user_id uuid not null references core.users(id),
    assignment_role varchar(30) not null,
    business_date date not null,
    started_at timestamp not null,
    ended_at timestamp,
    assigned_by uuid not null references core.users(id),
    ended_by uuid references core.users(id),
    remarks text,
    end_remarks text,
    assignment_idempotency_key varchar(100),
    end_idempotency_key varchar(100),
    created_at timestamp not null,
    constraint ck_pit_table_staff_assignment_role
        check (assignment_role in ('DEALER', 'PIT_SUPERVISOR')),
    constraint ck_pit_table_staff_assignment_end
        check ((ended_at is null and ended_by is null and end_idempotency_key is null)
            or (ended_at is not null and ended_by is not null and end_idempotency_key is not null)),
    constraint uq_pit_table_staff_assignment_idempotency
        unique (assignment_idempotency_key),
    constraint uq_pit_table_staff_end_idempotency
        unique (end_idempotency_key)
);

create unique index uq_pit_table_active_staff_role
    on casino.pit_table_staff_assignments (pit_table_id, assignment_role)
    where ended_at is null;

create unique index uq_pit_table_active_dealer_user
    on casino.pit_table_staff_assignments (staff_user_id)
    where ended_at is null and assignment_role = 'DEALER';

create index ix_pit_table_staff_assignment_table_history
    on casino.pit_table_staff_assignments (pit_table_id, started_at desc);

create index ix_pit_table_staff_assignment_user_history
    on casino.pit_table_staff_assignments (staff_user_id, started_at desc);

create index ix_pit_table_staff_assignment_business_date
    on casino.pit_table_staff_assignments (business_date, pit_table_id);

create index ix_pit_table_staff_assignment_active
    on casino.pit_table_staff_assignments (pit_table_id, assignment_role, staff_user_id)
    where ended_at is null;
