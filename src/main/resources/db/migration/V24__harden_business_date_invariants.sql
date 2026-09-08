alter table casino.business_dates
    alter column business_date set not null,
    alter column status set not null,
    alter column opened_at set not null;

alter table casino.business_dates
    add constraint ck_business_dates_status
        check (status in ('OPEN', 'CLOSED')),
    add constraint ck_business_dates_lifecycle
        check (
            (status = 'OPEN' and closed_at is null)
            or (status = 'CLOSED' and closed_at is not null)
        );

create unique index uq_business_dates_single_open
    on casino.business_dates ((status))
    where status = 'OPEN';
