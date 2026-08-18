create table if not exists casino.pit_table_customer_assignments (
    id uuid primary key,
    pit_table_id uuid not null references casino.pit_tables(id),
    customer_id uuid not null references customer.customers(id),
    customer_session_id uuid not null references session.customer_sessions(id),
    business_date date not null,
    status varchar(20) not null,
    joined_at timestamp not null,
    joined_by uuid not null references core.users(id),
    left_at timestamp,
    left_by uuid references core.users(id),
    constraint ck_pit_table_customer_assignment_status check (status in ('ACTIVE', 'LEFT'))
);

create unique index if not exists uq_active_pit_assignment_per_customer_session
    on casino.pit_table_customer_assignments (customer_session_id)
    where status = 'ACTIVE';

create index if not exists ix_pit_table_active_players
    on casino.pit_table_customer_assignments (pit_table_id, status, joined_at);

alter table casino.verified_gaming_results
    add column if not exists pit_table_id uuid references casino.pit_tables(id),
    add column if not exists assignment_id uuid references casino.pit_table_customer_assignments(id),
    add column if not exists idempotency_key varchar(100);

create unique index if not exists uq_verified_gaming_result_idempotency_key
    on casino.verified_gaming_results (idempotency_key)
    where idempotency_key is not null;

create index if not exists ix_verified_gaming_result_pit_table
    on casino.verified_gaming_results (pit_table_id, created_at);
