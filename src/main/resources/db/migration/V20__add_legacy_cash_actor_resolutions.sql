create table cashier.legacy_cash_actor_resolutions (
    id uuid primary key,
    actor_user_id uuid not null references core.users(id),
    business_date date not null,
    cash_received numeric(19, 2) not null check (cash_received >= 0),
    cash_paid numeric(19, 2) not null check (cash_paid >= 0),
    net_cash_movement numeric(19, 2) not null,
    opening_cash_verification varchar(32) not null
        check (opening_cash_verification = 'LEGACY_UNVERIFIED'),
    reason varchar(500) not null,
    resolved_by uuid not null references core.users(id),
    resolved_at timestamp not null,
    idempotency_key varchar(100) not null,
    constraint uq_legacy_cash_actor_resolution_actor_date
        unique (actor_user_id, business_date),
    constraint uq_legacy_cash_actor_resolution_idempotency_key
        unique (idempotency_key)
);

create index idx_legacy_cash_actor_resolutions_business_date
    on cashier.legacy_cash_actor_resolutions (business_date);
