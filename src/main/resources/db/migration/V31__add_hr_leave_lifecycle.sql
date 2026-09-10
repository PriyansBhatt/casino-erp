alter table casino.staff_leave_requests
    add column reviewed_by_user_id uuid references core.users(id),
    add column reviewed_at timestamp,
    add column review_reason varchar(500),
    add column cancelled_by_user_id uuid references core.users(id),
    add column cancelled_at timestamp,
    add column cancellation_reason varchar(500);

alter table casino.staff_leave_requests
    add constraint chk_staff_leave_review_reason
        check (review_reason is null or btrim(review_reason) <> ''),
    add constraint chk_staff_leave_cancellation_reason
        check (cancellation_reason is null or btrim(cancellation_reason) <> ''),
    add constraint chk_staff_leave_lifecycle_metadata check (
        (status = 'PENDING'
            and reviewed_by_user_id is null and reviewed_at is null and review_reason is null
            and cancelled_by_user_id is null and cancelled_at is null and cancellation_reason is null)
        or
        (status = 'APPROVED'
            and reviewed_by_user_id is not null and reviewed_at is not null
            and cancelled_by_user_id is null and cancelled_at is null and cancellation_reason is null)
        or
        (status = 'REJECTED'
            and reviewed_by_user_id is not null and reviewed_at is not null and review_reason is not null
            and cancelled_by_user_id is null and cancelled_at is null and cancellation_reason is null)
        or
        (status = 'CANCELLED'
            and cancelled_by_user_id is not null and cancelled_at is not null and cancellation_reason is not null
            and ((reviewed_by_user_id is null and reviewed_at is null and review_reason is null)
                or (reviewed_by_user_id is not null and reviewed_at is not null)))
    );

create index ix_staff_leave_requests_reviewed_by
    on casino.staff_leave_requests (reviewed_by_user_id);

create index ix_staff_leave_requests_cancelled_by
    on casino.staff_leave_requests (cancelled_by_user_id);
