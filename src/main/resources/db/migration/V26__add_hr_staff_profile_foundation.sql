create table casino.departments (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(150) not null,
    description varchar(1000),
    active boolean not null default true,
    sort_order integer,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint chk_departments_code_canonical check (code = upper(btrim(code)) and code <> ''),
    constraint chk_departments_name_not_blank check (btrim(name) <> ''),
    constraint uq_departments_code unique (code)
);

create table casino.job_titles (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(150) not null,
    description varchar(1000),
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint chk_job_titles_code_canonical check (code = upper(btrim(code)) and code <> ''),
    constraint chk_job_titles_name_not_blank check (btrim(name) <> ''),
    constraint uq_job_titles_code unique (code)
);

create table casino.staff_profiles (
    id uuid primary key,
    user_id uuid not null references core.users(id),
    employee_code varchar(50) not null,
    department_id uuid not null references casino.departments(id),
    job_title_id uuid not null references casino.job_titles(id),
    employment_status varchar(20) not null,
    employment_type varchar(20) not null,
    date_of_joining date not null,
    phone varchar(50),
    reporting_manager_staff_profile_id uuid references casino.staff_profiles(id),
    remarks varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_staff_profiles_user unique (user_id),
    constraint uq_staff_profiles_employee_code unique (employee_code),
    constraint chk_staff_profiles_employee_code_canonical
        check (employee_code = upper(btrim(employee_code)) and employee_code <> ''),
    constraint chk_staff_profiles_employment_status
        check (employment_status in ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'TERMINATED')),
    constraint chk_staff_profiles_employment_type
        check (employment_type in ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMPORARY')),
    constraint chk_staff_profiles_reporting_manager_not_self
        check (reporting_manager_staff_profile_id is null or reporting_manager_staff_profile_id <> id)
);

create index ix_departments_active_sort_order on casino.departments (active, sort_order, name);
create index ix_job_titles_active_name on casino.job_titles (active, name);
create index ix_staff_profiles_department on casino.staff_profiles (department_id);
create index ix_staff_profiles_job_title on casino.staff_profiles (job_title_id);
create index ix_staff_profiles_employment_status on casino.staff_profiles (employment_status);
create index ix_staff_profiles_reporting_manager on casino.staff_profiles (reporting_manager_staff_profile_id);
