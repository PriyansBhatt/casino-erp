alter table cashier.chip_custody_movements
    drop constraint ck_chip_custody_movement_type;

alter table cashier.chip_custody_movements
    add constraint ck_chip_custody_movement_type check (movement_type in
        ('CAGE_OPENING', 'BUY_IN_ISSUE', 'CASH_OUT_RETURN', 'TABLE_FLOAT_ISSUE',
         'TABLE_FLOAT_RETURN', 'CUSTOMER_TO_TABLE', 'TABLE_TO_CUSTOMER'));

alter table casino.pit_table_customer_assignments
    add column custody_settled_at timestamp,
    add column custody_settlement_key varchar(100);

alter table casino.pit_table_customer_assignments
    add constraint uq_pit_assignment_custody_settlement_key unique (custody_settlement_key);
