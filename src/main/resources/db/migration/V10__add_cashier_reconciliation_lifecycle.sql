alter table cashier.cashier_reconciliations
    add column if not exists lifecycle_status varchar(20),
    add column if not exists reopened_at timestamp,
    add column if not exists reopened_by uuid references core.users(id),
    add column if not exists reopen_reason varchar(1000);

update cashier.cashier_reconciliations
set lifecycle_status = 'SUBMITTED'
where lifecycle_status is null;

alter table cashier.cashier_reconciliations
    alter column lifecycle_status set not null;

alter table cashier.cashier_reconciliations
    add constraint ck_cashier_reconciliation_lifecycle
        check (lifecycle_status in ('SUBMITTED', 'REOPENED'));
