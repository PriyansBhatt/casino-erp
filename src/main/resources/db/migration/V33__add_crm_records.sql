-- Legacy nullable fields and rows are preserved. Only CRM1 rows use the new constraints.
ALTER TABLE customer.customer_service_records
 ADD COLUMN crm_record boolean NOT NULL DEFAULT false,
 ADD COLUMN service_at timestamp,
 ADD COLUMN classification varchar(30),
 ADD COLUMN status varchar(20),
 ADD COLUMN recorded_by uuid REFERENCES core.users(id),
 ADD COLUMN updated_at timestamp,
 ADD COLUMN idempotency_key varchar(100),
 ADD COLUMN request_signature text,
 ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE customer.customer_service_records ADD CONSTRAINT crm_service_valid CHECK (NOT crm_record OR (
 customer_id IS NOT NULL AND service_type IS NOT NULL AND service_type IN ('GIFT','FOOD','TICKET','OTHER')
 AND service_description IS NOT NULL AND length(trim(service_description)) BETWEEN 1 AND 2000
 AND service_at IS NOT NULL AND status IS NOT NULL AND status IN ('COMPLETED','CANCELLED')
 AND classification IS NOT NULL AND classification IN ('COMPLIMENTARY','CUSTOMER_PAID','UNSPECIFIED')
 AND recorded_by IS NOT NULL AND created_at IS NOT NULL AND updated_at IS NOT NULL
 AND idempotency_key IS NOT NULL AND request_signature IS NOT NULL
 AND (service_cost IS NULL OR (service_cost >= 0 AND service_cost <= 99999999999999999.99 AND service_cost = round(service_cost,2)))
));
CREATE UNIQUE INDEX crm_service_retry ON customer.customer_service_records(idempotency_key) WHERE crm_record;
CREATE INDEX crm_service_history ON customer.customer_service_records(service_at DESC,id DESC) WHERE crm_record;
CREATE INDEX crm_service_customer ON customer.customer_service_records(customer_id,service_at DESC) WHERE crm_record;
CREATE TABLE customer.transport_records (
 id uuid PRIMARY KEY, reference varchar(50) NOT NULL UNIQUE,
 customer_id uuid NOT NULL REFERENCES customer.customers(id),
 customer_session_id uuid REFERENCES session.customer_sessions(id),
 business_date date, transport_type varchar(30) NOT NULL CHECK(transport_type IN ('AIRPORT_PICKUP','AIRPORT_DROP','LOCAL_TRAVEL','OTHER')),
 pickup varchar(300) NOT NULL CHECK(length(trim(pickup))>0), destination varchar(300) NOT NULL CHECK(length(trim(destination))>0),
 scheduled_at timestamp NOT NULL, vehicle varchar(300), driver varchar(300),
 cost numeric(19,2) CHECK(cost>=0), notes varchar(2000),
 status varchar(20) NOT NULL CHECK(status IN ('SCHEDULED','COMPLETED','CANCELLED')),
 recorded_by uuid NOT NULL REFERENCES core.users(id), created_at timestamp NOT NULL, updated_at timestamp NOT NULL,
 idempotency_key varchar(100) NOT NULL UNIQUE, request_signature text NOT NULL, version bigint NOT NULL DEFAULT 0
);
CREATE INDEX transport_history ON customer.transport_records(scheduled_at DESC,id DESC);
CREATE INDEX transport_customer ON customer.transport_records(customer_id,scheduled_at DESC);
