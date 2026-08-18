create table if not exists cashier.losing_returns (
    id uuid primary key,
    losing_return_code varchar(120) not null unique,
    customer_id uuid not null references customer.customers(id),
    customer_session_id uuid not null references session.customer_sessions(id),
    business_date date not null,
    eligible_verified_loss numeric(19,2) not null check (eligible_verified_loss > 0),
    return_rate numeric(6,5) not null check (return_rate > 0 and return_rate <= 1),
    amount_paid numeric(19,2) not null check (amount_paid > 0),
    payment_mode varchar(20) not null check (payment_mode = 'CASH'),
    idempotency_key varchar(100) not null unique,
    created_at timestamp not null,
    created_by uuid not null references core.users(id),
    remarks varchar(1000),
    constraint uq_losing_return_customer_business_date unique (customer_id, business_date)
);

create index if not exists ix_losing_return_customer_business_date
    on cashier.losing_returns(customer_id, business_date);
create index if not exists ix_losing_return_cashier_business_date
    on cashier.losing_returns(created_by, business_date);
