-- Quantity-only Store ledger. No casino financial or settlement relationships.
create table casino.store_items (
 id uuid primary key, code varchar(50) not null unique,
 name varchar(150) not null check (btrim(name) <> ''), category varchar(100) not null check (btrim(category) <> ''),
 unit varchar(10) not null check (unit in ('PCS','BOX','PACK','BOTTLE')),
 active boolean not null default true,
 quantity_balance integer not null default 0 check (quantity_balance >= 0),
 created_at timestamp not null, created_by uuid not null references core.users(id) on delete restrict,
 updated_at timestamp not null, updated_by uuid not null references core.users(id) on delete restrict,
 version bigint not null default 0 check (version >= 0),
 check (code = upper(btrim(code)) and code ~ '^[A-Z0-9_-]+$')
);
create table casino.store_requests (
 id uuid primary key, reference varchar(50) not null unique,
 department_id uuid not null references casino.departments(id) on delete restrict,
 requester_staff_profile_id uuid not null references casino.staff_profiles(id) on delete restrict,
 recorded_by_user_id uuid not null references core.users(id) on delete restrict,
 created_at timestamp not null, required_date date, remarks varchar(1000),
 status varchar(30) not null check (status in ('PENDING','PARTIALLY_FULFILLED','FULFILLED','CANCELLED')),
 cancelled_at timestamp, cancelled_by uuid references core.users(id) on delete restrict, cancellation_reason varchar(500),
 version bigint not null default 0 check (version >= 0),
 creation_retry_key varchar(100) not null unique, request_fingerprint varchar(64) not null,
 check ((cancelled_at is null and cancelled_by is null and cancellation_reason is null) or
        (cancelled_at is not null and cancelled_by is not null and cancellation_reason is not null and btrim(cancellation_reason) <> ''))
);
create table casino.store_request_lines (
 id uuid primary key, request_id uuid not null references casino.store_requests(id) on delete restrict,
 item_id uuid not null references casino.store_items(id) on delete restrict,
 requested_quantity integer not null check (requested_quantity > 0),
 issued_quantity integer not null default 0 check (issued_quantity >= 0),
 cancelled_quantity integer not null default 0 check (cancelled_quantity >= 0),
 unique(request_id,item_id), unique(id,item_id),
 check (issued_quantity::bigint + cancelled_quantity <= requested_quantity)
);
create table casino.store_procurements (
 id uuid primary key, reference varchar(50) not null unique,
 request_line_id uuid not null, item_id uuid not null,
 quantity integer not null check (quantity > 0), received_quantity integer not null default 0,
 status varchar(20) not null check (status in ('PENDING','ORDERED','RECEIVED','CANCELLED')),
 supplier_reference varchar(200),
 created_by uuid not null references core.users(id) on delete restrict, created_at timestamp not null,
 ordered_by uuid references core.users(id) on delete restrict, ordered_at timestamp,
 cancelled_by uuid references core.users(id) on delete restrict, cancelled_at timestamp, cancellation_reason varchar(500),
 version bigint not null default 0 check (version >= 0),
 creation_retry_key varchar(100) not null unique, request_fingerprint varchar(64) not null,
 foreign key(request_line_id,item_id) references casino.store_request_lines(id,item_id) on delete restrict,
 unique(id,item_id), check (received_quantity >= 0 and received_quantity <= quantity),
 check (status <> 'RECEIVED' or received_quantity = quantity),
 check (status <> 'PENDING' or received_quantity = 0),
 check (status not in ('ORDERED','RECEIVED') or (ordered_by is not null and ordered_at is not null)),
 check (status <> 'CANCELLED' or (cancelled_by is not null and cancelled_at is not null and cancellation_reason is not null and btrim(cancellation_reason) <> ''))
);
create unique index uq_store_active_procurement on casino.store_procurements(request_line_id) where status in ('PENDING','ORDERED');
create table casino.store_movements (
 ledger_order bigint generated always as identity unique,
 id uuid primary key, reference varchar(50) not null unique,
 item_id uuid not null references casino.store_items(id) on delete restrict,
 movement_type varchar(20) not null check (movement_type in ('OPENING','RECEIPT','ISSUE','ADJUSTMENT_IN','ADJUSTMENT_OUT')),
 quantity integer not null check (quantity > 0), balance_after integer not null check (balance_after >= 0),
 request_line_id uuid, procurement_id uuid,
 performed_by uuid not null references core.users(id) on delete restrict, performed_at timestamp not null,
 business_date date, reason varchar(500), external_reference varchar(200),
 idempotency_key varchar(100) not null unique, request_fingerprint varchar(64) not null,
 foreign key(request_line_id,item_id) references casino.store_request_lines(id,item_id) on delete restrict,
 foreign key(procurement_id,item_id) references casino.store_procurements(id,item_id) on delete restrict,
 check ((movement_type='ISSUE' and request_line_id is not null and procurement_id is null) or
        (movement_type='RECEIPT' and procurement_id is not null and request_line_id is null) or
        (movement_type in ('OPENING','ADJUSTMENT_IN','ADJUSTMENT_OUT') and request_line_id is null and procurement_id is null)),
 check (movement_type not in ('ADJUSTMENT_IN','ADJUSTMENT_OUT') or (reason is not null and btrim(reason) <> ''))
);
create unique index uq_store_opening on casino.store_movements(item_id) where movement_type='OPENING';
create index ix_store_items_page on casino.store_items(created_at desc,id desc);
create index ix_store_requests_page on casino.store_requests(created_at desc,id desc);
create index ix_store_lines_request on casino.store_request_lines(request_id);
create index ix_store_lines_item on casino.store_request_lines(item_id);
create index ix_store_procurement_page on casino.store_procurements(created_at desc,id desc);
create index ix_store_movements_page on casino.store_movements(ledger_order desc);
create index ix_store_movements_item on casino.store_movements(item_id,ledger_order desc);
-- Ledger records cannot be edited/deleted, including through future repository mistakes.
create function casino.store_immutable_movement() returns trigger language plpgsql as $$
begin raise exception 'Store movements are immutable'; end $$;
create trigger store_immutable_movement before update or delete on casino.store_movements
for each row execute function casino.store_immutable_movement();

-- Historical request quantities/item identity cannot be rewritten into different authority.
create function casino.store_request_line_identity() returns trigger language plpgsql as $$
begin
 if new.request_id <> old.request_id or new.item_id <> old.item_id or new.requested_quantity <> old.requested_quantity then
  raise exception 'Store request line identity and requested quantity are immutable';
 end if;
 return new;
end $$;
create trigger store_request_line_identity before update on casino.store_request_lines
for each row execute function casino.store_request_line_identity();
create function casino.store_item_history_identity() returns trigger language plpgsql as $$
begin
 if (new.code <> old.code or new.unit <> old.unit) and
    (exists(select 1 from casino.store_request_lines where item_id=old.id) or
     exists(select 1 from casino.store_movements where item_id=old.id)) then
  raise exception 'Store item code and unit are immutable after history';
 end if;
 return new;
end $$;
create trigger store_item_history_identity before update on casino.store_items
for each row execute function casino.store_item_history_identity();
