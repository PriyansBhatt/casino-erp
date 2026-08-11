do $$
begin
    if exists (
        select 1
        from customer.customers
        where status is null
           or lower(btrim(status)) not in ('active', 'inactive', 'blocked')
    ) then
        raise exception 'Unknown customer status values exist; migration aborted';
    end if;
end
$$;

update customer.customers
set status = case lower(btrim(status))
    when 'active' then 'ACTIVE'
    when 'inactive' then 'INACTIVE'
    when 'blocked' then 'BLOCKED'
end
where status is distinct from case lower(btrim(status))
    when 'active' then 'ACTIVE'
    when 'inactive' then 'INACTIVE'
    when 'blocked' then 'BLOCKED'
end;

alter table customer.customers
    validate constraint chk_customers_status;
