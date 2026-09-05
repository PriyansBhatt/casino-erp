create table cashier.chip_custody_movements (
    id uuid primary key,
    movement_type varchar(40) not null,
    business_date date not null,
    source_type varchar(30) not null,
    source_reference_id uuid,
    destination_type varchar(30) not null,
    destination_reference_id uuid,
    related_transaction_type varchar(40),
    related_transaction_id uuid,
    customer_session_id uuid references session.customer_sessions(id),
    pit_table_id uuid references casino.pit_tables(id),
    total_value numeric(19, 2) not null,
    idempotency_key varchar(100) not null,
    created_by uuid not null references core.users(id),
    created_at timestamp not null,
    constraint ck_chip_custody_movement_type check (movement_type in
        ('CAGE_OPENING', 'BUY_IN_ISSUE', 'CASH_OUT_RETURN', 'TABLE_FLOAT_ISSUE', 'TABLE_FLOAT_RETURN')),
    constraint ck_chip_custody_source_type check (source_type in ('EXTERNAL', 'CAGE', 'CUSTOMER_SESSION', 'PIT_TABLE')),
    constraint ck_chip_custody_destination_type check (destination_type in ('EXTERNAL', 'CAGE', 'CUSTOMER_SESSION', 'PIT_TABLE')),
    constraint ck_chip_custody_total_positive check (total_value > 0),
    constraint uq_chip_custody_idempotency unique (idempotency_key)
);

create table cashier.chip_buy_in_denominations (
    chip_buy_in_id uuid not null references cashier.chip_buy_ins(id),
    denomination integer not null,
    quantity bigint not null,
    primary key (chip_buy_in_id, denomination),
    constraint ck_chip_buy_in_denomination check (denomination in (500, 1000, 5000, 10000, 25000)),
    constraint ck_chip_buy_in_denomination_quantity check (quantity > 0)
);

create table cashier.chip_cash_out_denominations (
    chip_cash_out_id uuid not null references cashier.chip_cash_outs(id),
    denomination integer not null,
    quantity bigint not null,
    primary key (chip_cash_out_id, denomination),
    constraint ck_chip_cash_out_denomination check (denomination in (500, 1000, 5000, 10000, 25000)),
    constraint ck_chip_cash_out_denomination_quantity check (quantity > 0)
);

create table cashier.chip_custody_movement_lines (
    movement_id uuid not null references cashier.chip_custody_movements(id),
    denomination integer not null,
    quantity bigint not null,
    primary key (movement_id, denomination),
    constraint ck_chip_custody_line_denomination check (denomination in (500, 1000, 5000, 10000, 25000)),
    constraint ck_chip_custody_line_quantity check (quantity > 0)
);

create table cashier.chip_custody_inventory (
    id uuid primary key,
    location_key varchar(80) not null,
    location_type varchar(30) not null,
    reference_id uuid,
    denomination integer not null,
    quantity bigint not null default 0,
    constraint uq_chip_custody_inventory_location_denomination unique (location_key, denomination),
    constraint ck_chip_custody_inventory_location_type check (location_type in ('CAGE', 'CUSTOMER_SESSION', 'PIT_TABLE')),
    constraint ck_chip_custody_inventory_denomination check (denomination in (500, 1000, 5000, 10000, 25000)),
    constraint ck_chip_custody_inventory_quantity check (quantity >= 0)
);

insert into cashier.chip_custody_inventory
    (id, location_key, location_type, reference_id, denomination, quantity)
values
    (gen_random_uuid(), 'CAGE', 'CAGE', null, 500, 0),
    (gen_random_uuid(), 'CAGE', 'CAGE', null, 1000, 0),
    (gen_random_uuid(), 'CAGE', 'CAGE', null, 5000, 0),
    (gen_random_uuid(), 'CAGE', 'CAGE', null, 10000, 0),
    (gen_random_uuid(), 'CAGE', 'CAGE', null, 25000, 0)
on conflict (location_key, denomination) do nothing;

create index ix_chip_custody_movement_business_date
    on cashier.chip_custody_movements (business_date, created_at desc);
create index ix_chip_custody_movement_session
    on cashier.chip_custody_movements (customer_session_id, created_at);
create index ix_chip_custody_movement_table
    on cashier.chip_custody_movements (pit_table_id, created_at);
