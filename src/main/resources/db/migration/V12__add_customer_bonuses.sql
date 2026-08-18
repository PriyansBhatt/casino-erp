create table if not exists customer.customer_bonuses (
    id uuid primary key,
    bonus_code varchar(120) not null unique,
    customer_id uuid not null references customer.customers(id),
    customer_session_id uuid not null references session.customer_sessions(id),
    business_date date not null,
    bonus_type varchar(40) not null check (bonus_type in ('WELCOME','PROMOTIONAL','LOYALTY','VIP','MANUAL_ADJUSTMENT')),
    amount numeric(19,2) not null check (amount > 0),
    reason varchar(1000) not null check (length(trim(reason)) > 0),
    status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED','CANCELLED')),
    created_by uuid not null references core.users(id),
    approved_by uuid not null references core.users(id),
    created_at timestamp not null,
    approved_at timestamp not null,
    idempotency_key varchar(100) not null unique
);

create index if not exists ix_customer_bonuses_business_date
    on customer.customer_bonuses(business_date, created_at desc);
create index if not exists ix_customer_bonuses_customer
    on customer.customer_bonuses(customer_id, created_at desc);
