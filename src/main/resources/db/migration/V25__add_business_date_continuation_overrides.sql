create table casino.business_date_continuation_overrides (
    id uuid primary key,
    business_date date not null references casino.business_dates(business_date),
    reason varchar(500) not null,
    authorized_by uuid not null references core.users(id),
    authorized_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revoked_by uuid references core.users(id),
    revoke_reason varchar(500),
    constraint ck_business_date_continuation_override_expiry
        check (expires_at > authorized_at),
    constraint ck_business_date_continuation_override_revocation
        check ((revoked_at is null and revoked_by is null and revoke_reason is null)
            or (revoked_at is not null and revoked_by is not null and revoke_reason is not null))
);

create unique index uq_business_date_continuation_override_unrevoked
    on casino.business_date_continuation_overrides (business_date)
    where revoked_at is null;

create index idx_business_date_continuation_override_business_date
    on casino.business_date_continuation_overrides (business_date, authorized_at desc);
