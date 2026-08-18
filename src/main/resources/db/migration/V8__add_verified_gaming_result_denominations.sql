create table if not exists casino.verified_gaming_result_denominations (
    verified_gaming_result_id uuid not null
        references casino.verified_gaming_results(id) on delete cascade,
    denomination integer not null,
    quantity integer not null,
    primary key (verified_gaming_result_id, denomination),
    constraint ck_verified_result_denomination_supported
        check (denomination in (500, 1000, 5000, 10000, 25000)),
    constraint ck_verified_result_denomination_quantity
        check (quantity > 0)
);
