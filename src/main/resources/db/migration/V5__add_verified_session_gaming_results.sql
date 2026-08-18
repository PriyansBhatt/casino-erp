create table if not exists casino.verified_gaming_results (
    id uuid primary key,
    customer_id uuid not null,
    customer_session_id uuid not null,
    business_date date not null,
    source_type varchar(30) not null,
    result_type varchar(20) not null,
    amount numeric(19, 2) not null,
    created_at timestamp not null,
    created_by uuid not null,
    constraint fk_verified_gaming_result_customer
        foreign key (customer_id) references customer.customers(id),
    constraint fk_verified_gaming_result_session
        foreign key (customer_session_id) references session.customer_sessions(id),
    constraint fk_verified_gaming_result_actor
        foreign key (created_by) references core.users(id),
    constraint ck_verified_gaming_result_source check (source_type in ('TABLE')),
    constraint ck_verified_gaming_result_type check (result_type in ('WIN', 'LOSS')),
    constraint ck_verified_gaming_result_amount check (amount > 0)
);

create index if not exists ix_verified_gaming_result_session
    on casino.verified_gaming_results (customer_session_id, created_at);

create index if not exists ix_verified_gaming_result_customer_business_date
    on casino.verified_gaming_results (customer_id, business_date);
