create table if not exists customer.hotel_bookings (
 id uuid primary key, booking_code varchar(120) not null unique,
 customer_id uuid not null references customer.customers(id),
 customer_session_id uuid references session.customer_sessions(id), business_date date not null,
 hotel_name varchar(200) not null, room_type varchar(100) not null,
 check_in_date date not null, check_out_date date not null,
 number_of_guests integer not null check(number_of_guests > 0),
 estimated_cost numeric(19,2) not null check(estimated_cost >= 0), actual_cost numeric(19,2) check(actual_cost >= 0),
 billing_type varchar(40) not null check(billing_type in ('CASINO_COMPLIMENTARY','CUSTOMER_DIRECT')),
 status varchar(30) not null check(status in ('REQUESTED','APPROVED','BOOKED','CHECKED_IN','COMPLETED','CANCELLED','REJECTED')),
 remarks varchar(1000), created_at timestamp not null, updated_at timestamp not null,
 created_by uuid not null references core.users(id), approved_by uuid references core.users(id), approved_at timestamp,
 idempotency_key varchar(100) not null unique,
 constraint chk_hotel_booking_dates check(check_out_date >= check_in_date)
);
create index if not exists ix_hotel_bookings_business_date on customer.hotel_bookings(business_date,created_at desc);
create index if not exists ix_hotel_bookings_customer on customer.hotel_bookings(customer_id,created_at desc);
