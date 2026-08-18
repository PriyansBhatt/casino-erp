alter table cashier.chip_buy_ins
    add column if not exists payment_reference varchar(150),
    add column if not exists idempotency_key varchar(100);

create unique index if not exists uq_chip_buy_ins_idempotency_key
    on cashier.chip_buy_ins (idempotency_key)
    where idempotency_key is not null;
