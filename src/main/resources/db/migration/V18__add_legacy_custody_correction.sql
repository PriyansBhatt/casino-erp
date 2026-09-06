alter table cashier.chip_custody_movements
    add column correction_reason varchar(500);

alter table cashier.chip_custody_movements
    drop constraint ck_chip_custody_movement_type;

alter table cashier.chip_custody_movements
    add constraint ck_chip_custody_movement_type check (movement_type in
        ('CAGE_OPENING', 'BUY_IN_ISSUE', 'CASH_OUT_RETURN', 'TABLE_FLOAT_ISSUE',
         'TABLE_FLOAT_RETURN', 'CUSTOMER_TO_TABLE', 'TABLE_TO_CUSTOMER',
         'LEGACY_CUSTODY_CORRECTION'));

alter table cashier.chip_custody_movements
    add constraint ck_legacy_custody_correction_reason check (
        movement_type <> 'LEGACY_CUSTODY_CORRECTION'
        or length(trim(correction_reason)) >= 10
    );
