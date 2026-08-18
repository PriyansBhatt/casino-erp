alter table cashier.chip_cash_outs
    add column if not exists payment_mode varchar(20),
    add column if not exists payment_reference varchar(150),
    add column if not exists idempotency_key varchar(100),
    alter column cashier_shift_id drop not null;

create unique index if not exists uq_chip_cash_outs_idempotency_key
    on cashier.chip_cash_outs (idempotency_key)
    where idempotency_key is not null;

create unique index if not exists uq_chip_cash_outs_cash_out_code
    on cashier.chip_cash_outs (cash_out_code);
