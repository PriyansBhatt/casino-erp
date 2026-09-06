create table casino.physical_pit_tables (
    id uuid primary key,
    table_code varchar(50) not null,
    table_name varchar(100) not null,
    game_type varchar(50) not null,
    max_players integer,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_physical_pit_table_code unique (table_code),
    constraint ck_physical_pit_table_status check (status in ('ACTIVE', 'INACTIVE'))
);

insert into casino.physical_pit_tables
    (id, table_code, table_name, game_type, max_players, status, created_at, updated_at)
select gen_random_uuid(), source.table_code, source.table_name, source.game_type,
       source.max_players, 'ACTIVE', source.created_at, current_timestamp
from (
    select distinct on (upper(trim(coalesce(table_code, ''))))
           coalesce(nullif(upper(trim(table_code)), ''), 'LEGACY-' || id::text) as table_code,
           coalesce(nullif(trim(table_name), ''), 'Legacy Pit Table') as table_name,
           coalesce(nullif(trim(game_type), ''), 'UNKNOWN') as game_type,
           max_players,
           coalesce(opened_at, current_timestamp) as created_at
    from casino.pit_tables
    order by upper(trim(coalesce(table_code, ''))), opened_at desc nulls last, id
) source;

alter table casino.pit_tables
    add column physical_table_id uuid references casino.physical_pit_tables(id),
    add column opening_idempotency_key varchar(100);

update casino.pit_tables operation
set physical_table_id = physical.id
from casino.physical_pit_tables physical
where physical.table_code = coalesce(
    nullif(upper(trim(operation.table_code)), ''), 'LEGACY-' || operation.id::text);

alter table casino.pit_tables
    alter column physical_table_id set not null;

alter table casino.pit_tables
    add constraint uq_pit_table_physical_business_date
        unique (physical_table_id, business_date),
    add constraint uq_pit_table_opening_idempotency
        unique (opening_idempotency_key);

create unique index uq_pit_table_one_open_operation
    on casino.pit_tables (physical_table_id)
    where upper(status) = 'OPEN';

create index ix_pit_table_physical_history
    on casino.pit_tables (physical_table_id, business_date desc);
