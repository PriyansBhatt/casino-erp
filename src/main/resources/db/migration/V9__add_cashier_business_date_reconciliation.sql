create table if not exists cashier.cashier_reconciliations (
    id uuid primary key,
    business_date date not null,
    cashier_user_id uuid not null references core.users(id),
    opening_cash numeric(19, 2) not null check (opening_cash >= 0),
    actual_closing_cash numeric(19, 2) not null check (actual_closing_cash >= 0),
    expected_closing_cash numeric(19, 2) not null,
    variance numeric(19, 2) not null,
    status varchar(20) not null check (status in ('BALANCED', 'OVER', 'SHORT')),
    idempotency_key varchar(100) not null,
    submitted_at timestamp not null,
    remarks varchar(1000),
    constraint uq_cashier_reconciliation_business_date unique (cashier_user_id, business_date),
    constraint uq_cashier_reconciliation_idempotency unique (idempotency_key)
);

create table if not exists cashier.cashier_reconciliation_denominations (
    reconciliation_id uuid not null references cashier.cashier_reconciliations(id) on delete cascade,
    denomination integer not null,
    quantity integer not null,
    primary key (reconciliation_id, denomination),
    constraint ck_cashier_reconciliation_denomination
        check (denomination in (5, 10, 20, 50, 100, 500, 1000)),
    constraint ck_cashier_reconciliation_quantity check (quantity > 0)
);
