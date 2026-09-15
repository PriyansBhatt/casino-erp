-- Preserve nullable legacy references and historical records; never cascade deletions.
-- Validate existing rows as well as all future inserts/updates.
ALTER TABLE customer.customer_service_records
    ADD CONSTRAINT fk_customer_service_records_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer.customers (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    ADD CONSTRAINT fk_customer_service_records_session
        FOREIGN KEY (customer_session_id)
        REFERENCES session.customer_sessions (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION;
