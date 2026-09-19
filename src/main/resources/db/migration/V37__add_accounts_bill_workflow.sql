-- AC1 is an invoice review register, not a payment or financial ledger.
create table casino.accounts_parties (
 id uuid primary key, code varchar(60) not null unique,
 name varchar(200) not null, kind varchar(20) not null check(kind in ('SUPPLIER','HOTEL','OTHER')),
 created_by uuid not null references core.users(id), created_at timestamp not null
);
create table casino.accounts_bills (
 id uuid primary key, party_id uuid not null references casino.accounts_parties(id),
 invoice_reference varchar(150) not null, normalized_invoice_reference varchar(150) not null,
 recorded_by uuid not null references core.users(id), recorded_at timestamp not null, business_date date not null,
 status varchar(40) not null check(status in ('DRAFT','SUBMITTED','AWAITING_DIRECTOR_APPROVAL','HELD','RETURNED','REJECTED','APPROVED_FOR_PAYMENT')),
 held_stage varchar(20) check(held_stage in ('VERIFICATION','APPROVAL')),
 ever_submitted boolean not null default false, ever_verified boolean not null default false,
 version bigint not null default 0 check(version>=0), revision integer not null default 1 check(revision>0),
 verification_id uuid, approval_id uuid,
 unique(party_id,normalized_invoice_reference),
 check(normalized_invoice_reference=upper(btrim(normalized_invoice_reference)) and normalized_invoice_reference<>''),
 check((status='HELD')=(held_stage is not null)),
 check(not ever_verified or ever_submitted),
 check(status not in ('AWAITING_DIRECTOR_APPROVAL','APPROVED_FOR_PAYMENT') or verification_id is not null),
 check((status='APPROVED_FOR_PAYMENT')=(approval_id is not null))
);
create index ix_accounts_bill_owner on casino.accounts_bills(recorded_by,recorded_at desc,id);
create index ix_accounts_bill_submitted on casino.accounts_bills(recorded_at desc,id) where ever_submitted;
create index ix_accounts_bill_verified on casino.accounts_bills(recorded_at desc,id) where ever_verified;
create table casino.accounts_bill_revisions (
 bill_id uuid not null references casino.accounts_bills(id), revision integer not null,
 snapshot jsonb not null, created_by uuid not null references core.users(id), created_at timestamp not null,
 primary key(bill_id,revision)
);
create table casino.accounts_evidence (
 id uuid primary key, bill_id uuid not null references casino.accounts_bills(id),
 storage_key uuid not null unique, original_name varchar(200) not null,
 media_type varchar(30) not null check(media_type in ('application/pdf','image/jpeg','image/png')),
 byte_size integer not null check(byte_size>0 and byte_size<=5242880), checksum varchar(64) not null,
 invoice_document boolean not null, replaces_id uuid references casino.accounts_evidence(id),
 uploaded_by uuid not null references core.users(id), uploaded_at timestamp not null,
 unique(id,bill_id)
);
create table casino.accounts_decisions (
 id uuid primary key, bill_id uuid not null, revision integer not null,
 action varchar(20) not null check(action in ('SUBMIT','VERIFY','APPROVE','HOLD','RESUME','RETURN','REJECT')),
 stage varchar(20) not null check(stage in ('PREPARATION','VERIFICATION','APPROVAL')),
 actor_id uuid not null references core.users(id), decided_at timestamp not null, reason varchar(1000) not null,
 evidence_digest varchar(64) not null, verification_id uuid references casino.accounts_decisions(id),
 foreign key(bill_id,revision) references casino.accounts_bill_revisions(bill_id,revision),
 check(action not in ('HOLD','RETURN','REJECT') or btrim(reason)<>''),
 check(action<>'APPROVE' or verification_id is not null), unique(id,bill_id)
);
create unique index uq_accounts_verification on casino.accounts_decisions(bill_id,revision) where action='VERIFY';
create unique index uq_accounts_approval on casino.accounts_decisions(bill_id) where action='APPROVE';
alter table casino.accounts_bills add constraint fk_accounts_current_revision foreign key(id,revision) references casino.accounts_bill_revisions(bill_id,revision) deferrable initially deferred;
alter table casino.accounts_bills add constraint fk_accounts_verification foreign key(verification_id,id) references casino.accounts_decisions(id,bill_id);
alter table casino.accounts_bills add constraint fk_accounts_approval foreign key(approval_id,id) references casino.accounts_decisions(id,bill_id);
create table casino.accounts_operations (
 retry_key varchar(100) primary key, actor_id uuid not null references core.users(id),
 fingerprint varchar(64) not null, response jsonb not null, completed_at timestamp not null
);
-- Preserve the original authority and all evidence/decision history even if future code errs.
create function casino.accounts_immutable_record() returns trigger language plpgsql as $$
begin raise exception 'Accounts historical records are immutable'; end $$;
create trigger accounts_immutable_party before update or delete on casino.accounts_parties for each row execute function casino.accounts_immutable_record();
create trigger accounts_immutable_revision before update or delete on casino.accounts_bill_revisions for each row execute function casino.accounts_immutable_record();
create trigger accounts_immutable_evidence before update or delete on casino.accounts_evidence for each row execute function casino.accounts_immutable_record();
create trigger accounts_immutable_decision before update or delete on casino.accounts_decisions for each row execute function casino.accounts_immutable_record();
create trigger accounts_immutable_operation before update or delete on casino.accounts_operations for each row execute function casino.accounts_immutable_record();
create function casino.accounts_bill_identity() returns trigger language plpgsql as $$
begin
 if tg_op='DELETE' then raise exception 'Accounts bills cannot be deleted'; end if;
 if old.status in ('APPROVED_FOR_PAYMENT','REJECTED') or new.recorded_by<>old.recorded_by or
    new.business_date<>old.business_date or new.recorded_at<>old.recorded_at or
    (old.ever_submitted and not new.ever_submitted) or (old.ever_verified and not new.ever_verified) then
  raise exception 'Accounts original authority, visibility and final records are immutable';
 end if;
 return new;
end $$;
create trigger accounts_bill_identity before update or delete on casino.accounts_bills for each row execute function casino.accounts_bill_identity();
