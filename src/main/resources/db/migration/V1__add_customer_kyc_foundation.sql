alter table customer.customers
    add column if not exists date_of_birth date,
    add column if not exists gender varchar(30),
    add column if not exists permanent_address text,
    add column if not exists current_address text,
    add column if not exists email varchar(254),
    add column if not exists occupation varchar(150),
    add column if not exists category varchar(20),
    add column if not exists risk_level varchar(20),
    add column if not exists kyc_status varchar(20),
    add column if not exists internal_notes text,
    add column if not exists primary_photo_attachment_id uuid,
    add column if not exists created_at timestamp without time zone,
    add column if not exists updated_at timestamp without time zone;

update customer.customers
set category = 'NORMAL'
where category is null;

update customer.customers
set kyc_status = 'PENDING'
where kyc_status is null;

alter table customer.customers
    alter column category set default 'NORMAL',
    alter column category set not null,
    alter column kyc_status set default 'PENDING',
    alter column kyc_status set not null;

create table if not exists customer.customer_attachments (
    id uuid primary key,
    customer_id uuid not null,
    attachment_type varchar(40) not null,
    storage_key varchar(500) not null,
    original_filename varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    checksum varchar(128) not null,
    status varchar(30) not null default 'ACTIVE',
    uploaded_by uuid not null,
    uploaded_at timestamp without time zone not null default current_timestamp,
    constraint fk_customer_attachments_customer
        foreign key (customer_id) references customer.customers (id) on delete restrict,
    constraint fk_customer_attachments_uploaded_by
        foreign key (uploaded_by) references core.users (id) on delete restrict,
    constraint chk_customer_attachments_type
        check (attachment_type in ('CUSTOMER_PHOTO', 'IDENTITY_DOCUMENT')),
    constraint chk_customer_attachments_size
        check (size_bytes >= 0),
    constraint uq_customer_attachments_storage_key unique (storage_key)
);

create index if not exists idx_customer_attachments_customer_id
    on customer.customer_attachments (customer_id);

create table if not exists customer.customer_identity_documents (
    id uuid primary key,
    customer_id uuid not null,
    id_type varchar(40) not null,
    id_number varchar(100) not null,
    normalized_id_number varchar(100) not null,
    issuing_country varchar(100),
    issued_date date,
    expiry_date date,
    attachment_id uuid,
    is_primary boolean not null default false,
    document_status varchar(30) not null default 'ACTIVE',
    verified_by uuid,
    verified_at timestamp without time zone,
    created_by uuid not null,
    created_at timestamp without time zone not null default current_timestamp,
    updated_at timestamp without time zone,
    constraint fk_customer_identity_documents_customer
        foreign key (customer_id) references customer.customers (id) on delete restrict,
    constraint fk_customer_identity_documents_attachment
        foreign key (attachment_id) references customer.customer_attachments (id) on delete restrict,
    constraint fk_customer_identity_documents_verified_by
        foreign key (verified_by) references core.users (id) on delete restrict,
    constraint fk_customer_identity_documents_created_by
        foreign key (created_by) references core.users (id) on delete restrict,
    constraint chk_customer_identity_documents_type
        check (id_type in ('PASSPORT', 'CITIZENSHIP', 'NATIONAL_ID', 'DRIVING_LICENCE', 'OTHER')),
    constraint chk_customer_identity_documents_status
        check (document_status in ('ACTIVE', 'REPLACED', 'EXPIRED', 'REVOKED')),
    constraint chk_customer_identity_documents_dates
        check (issued_date is null or expiry_date is null or expiry_date >= issued_date),
    constraint chk_customer_identity_documents_number
        check (length(trim(normalized_id_number)) > 0)
);

create index if not exists idx_customer_identity_documents_customer_id
    on customer.customer_identity_documents (customer_id);

create unique index if not exists uq_customer_identity_documents_active_number
    on customer.customer_identity_documents (
        id_type,
        coalesce(upper(issuing_country), ''),
        normalized_id_number
    )
    where document_status = 'ACTIVE';

create unique index if not exists uq_customer_identity_documents_active_primary
    on customer.customer_identity_documents (customer_id)
    where is_primary and document_status = 'ACTIVE';

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_customers_status'
          and conrelid = 'customer.customers'::regclass
    ) then
        alter table customer.customers
            add constraint chk_customers_status
            check (status in ('ACTIVE', 'INACTIVE', 'BLOCKED')) not valid;
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_customers_category'
          and conrelid = 'customer.customers'::regclass
    ) then
        alter table customer.customers
            add constraint chk_customers_category
            check (category in ('NORMAL', 'VIP', 'VVIP'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_customers_kyc_status'
          and conrelid = 'customer.customers'::regclass
    ) then
        alter table customer.customers
            add constraint chk_customers_kyc_status
            check (kyc_status in ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'chk_customers_risk_level'
          and conrelid = 'customer.customers'::regclass
    ) then
        alter table customer.customers
            add constraint chk_customers_risk_level
            check (risk_level is null or risk_level in ('LOW', 'MEDIUM', 'HIGH'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_customers_primary_photo_attachment'
          and conrelid = 'customer.customers'::regclass
    ) then
        alter table customer.customers
            add constraint fk_customers_primary_photo_attachment
            foreign key (primary_photo_attachment_id)
            references customer.customer_attachments (id)
            on delete set null;
    end if;
end
$$;

create index if not exists idx_customers_category
    on customer.customers (category);

create index if not exists idx_customers_kyc_status
    on customer.customers (kyc_status);

create index if not exists idx_customers_risk_level
    on customer.customers (risk_level)
    where risk_level is not null;
