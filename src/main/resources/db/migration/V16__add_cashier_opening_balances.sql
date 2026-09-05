create table cashier.cashier_opening_balances (
    id uuid primary key,
    cashier_user_id uuid not null references core.users(id),
    business_date date not null,
    opening_cash_amount numeric(19, 2) not null check (opening_cash_amount >= 0),
    created_by uuid not null references core.users(id),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_cashier_opening_balance_business_date unique (cashier_user_id, business_date)
);

create index idx_cashier_opening_balances_business_date
    on cashier.cashier_opening_balances (business_date);
