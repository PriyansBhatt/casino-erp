create table casino.legacy_pit_table_reconciliation_resolutions (
    id uuid primary key,
    pit_table_id uuid not null references casino.pit_tables(id),
    business_date date not null,
    original_opening_float numeric(19, 2) not null,
    physical_closing_float numeric(19, 2) not null check (physical_closing_float >= 0),
    legacy_opening_float_gap numeric(19, 2) not null,
    opening_float_verification varchar(30) not null,
    reason varchar(500) not null,
    resolved_by uuid not null references core.users(id),
    resolved_at timestamp not null,
    idempotency_key varchar(100) not null,
    constraint uq_legacy_pit_table_resolution_table unique (pit_table_id),
    constraint uq_legacy_pit_table_resolution_idempotency unique (idempotency_key),
    constraint ck_legacy_pit_table_opening_verification
        check (opening_float_verification = 'LEGACY_UNVERIFIED')
);

create index ix_legacy_pit_table_resolution_business_date
    on casino.legacy_pit_table_reconciliation_resolutions (business_date);
