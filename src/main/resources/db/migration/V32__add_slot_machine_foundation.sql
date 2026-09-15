create table casino.gaming_machines (
    id uuid primary key,
    machine_code varchar(40) not null unique,
    display_name varchar(120) not null,
    machine_type varchar(30) not null check (machine_type in ('SLOT', 'AUTOMATIC_ROULETTE')),
    location varchar(120),
    availability varchar(30) not null check (availability in ('AVAILABLE', 'OUT_OF_SERVICE')),
    created_at timestamp not null,
    updated_at timestamp not null,
    created_by uuid not null references core.users(id),
    constraint ck_machine_code check (machine_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$')
);

-- IN_USE is derived from an ACTIVE play; availability cannot drift from occupancy.
create table casino.slot_plays (
    id uuid primary key,
    machine_id uuid not null references casino.gaming_machines(id),
    customer_id uuid not null references customer.customers(id),
    customer_session_id uuid not null references session.customer_sessions(id),
    business_date date not null,
    status varchar(20) not null check (status in ('ACTIVE', 'ENDED')),
    started_at timestamp not null,
    ended_at timestamp,
    started_by uuid not null references core.users(id),
    ended_by uuid references core.users(id),
    start_key varchar(100) not null unique,
    end_key varchar(100) unique,
    constraint ck_slot_play_lifecycle check (
        (status = 'ACTIVE' and ended_at is null and ended_by is null and end_key is null)
        or (status = 'ENDED' and ended_at is not null and ended_by is not null and end_key is not null and ended_at >= started_at))
);
create unique index uq_slot_active_machine on casino.slot_plays(machine_id) where status = 'ACTIVE';
create unique index uq_slot_active_session on casino.slot_plays(customer_session_id) where status = 'ACTIVE';
create index ix_slot_machine_history on casino.slot_plays(machine_id, started_at desc, id desc);
create index ix_slot_play_business_date on casino.slot_plays(business_date, status);
