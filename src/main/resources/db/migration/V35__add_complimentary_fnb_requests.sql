-- Complimentary operational quantities only; no financial posting or inventory deduction.
CREATE TABLE customer.fnb_requests (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL REFERENCES customer.customers(id),
    customer_session_id uuid REFERENCES session.customer_sessions(id),
    business_date date NOT NULL,
    requested_at timestamp NOT NULL,
    delivered_at timestamp,
    delivered_business_date date,
    request_type varchar(20) NOT NULL CHECK (request_type IN ('FOOD','BEVERAGE')),
    item varchar(200) NOT NULL CHECK (length(trim(item)) > 0),
    quantity integer NOT NULL CHECK (quantity > 0),
    location varchar(300) NOT NULL CHECK (length(trim(location)) > 0),
    remarks varchar(2000),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','PREPARING','READY','DELIVERED','CANCELLED')),
    requested_by uuid NOT NULL REFERENCES core.users(id),
    handled_by uuid REFERENCES core.users(id),
    updated_at timestamp NOT NULL,
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    idempotency_key varchar(100) NOT NULL UNIQUE,
    request_signature text NOT NULL,
    CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CHECK ((status = 'DELIVERED') = (delivered_business_date IS NOT NULL))
);
CREATE INDEX fnb_history ON customer.fnb_requests(business_date,requested_at DESC,id DESC);
CREATE INDEX fnb_live ON customer.fnb_requests(requested_at DESC,id DESC)
    WHERE status IN ('PENDING','PREPARING','READY');
CREATE INDEX fnb_customer_history ON customer.fnb_requests(customer_id,business_date);
CREATE TABLE customer.fnb_status_events (
    request_id uuid NOT NULL REFERENCES customer.fnb_requests(id),
    version integer NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','PREPARING','READY','DELIVERED','CANCELLED')),
    actor_id uuid NOT NULL REFERENCES core.users(id),
    changed_at timestamp NOT NULL,
    PRIMARY KEY(request_id,version)
);

CREATE INDEX fnb_delivered_date ON customer.fnb_requests(delivered_business_date) WHERE status='DELIVERED';
