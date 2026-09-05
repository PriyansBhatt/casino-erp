insert into cashier.cashier_opening_balances (
    id,
    cashier_user_id,
    business_date,
    opening_cash_amount,
    created_by,
    created_at,
    updated_at
)
select
    gen_random_uuid(),
    cashier_user_id,
    business_date,
    opening_cash,
    cashier_user_id,
    submitted_at,
    submitted_at
from cashier.cashier_reconciliations
on conflict (cashier_user_id, business_date) do nothing;
