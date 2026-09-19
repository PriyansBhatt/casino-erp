# AC1 Accounts bill workflow

AC1 is an invoice register and three-person review chain. It does not execute payment or create a financial ledger posting. Changes are intended for review before deployment.

## Access and decisions

| Current active role | Read scope | Actions |
| --- | --- | --- |
| STORE_MANAGER / ACCOUNTANT_HEAD | Bills originally recorded by that user, in every state | Create; edit/upload in own Draft or Returned bill; submit |
| ACCOUNTS_MANAGER | Every bill submitted at least once, including subsequent history | Verify, hold, resume verification, return or reject at verification stage |
| DIRECTOR | Every bill verified at least once, including subsequent corrections/history | Approve for payment, hold, resume approval, return or reject at approval stage |
| SUPER_ADMIN | No AC1 bill/document access | Existing A1 account provisioning only |
| All others | None | None |

The immutable preparer cannot review their own bill after changing roles. The verifier cannot perform Director review of that revision after changing roles. A held bill requires explicit resume, return or rejection by a currently authorized reviewer of that stage; the individual need not be the original holder. Approved and rejected bills are final in AC1.

Returned bills receive a fresh unverified revision, even if resubmitted without a content edit. Every edit and evidence upload also creates a revision. Historical visibility is sticky; historical verification does not authorize the current unverified revision. Corrections require the original preparer with an active preparer role. Reassignment, takeover, approval reversal and post-approval correction are not implemented.

## Persistence and consistency

V37 adds only Accounts parties, bills, immutable revision snapshots, evidence metadata, decisions and operation receipts. V1–V36 are unchanged. It performs no backfill or operational data update.

Party codes are unique stable identities. AC1 does not rename shared parties. Invoice snapshots retain the party and source display references used when prepared. References to procurement, receipts and hotel bookings are read-only and never copy source costs into invoice authority. Lines and explicit NPR components are validated with BigDecimal and no rounding. Response money uses exact two-decimal strings, never JavaScript numeric conversion. JDBC timestamps preserve PostgreSQL microseconds. The invoice subtotal must equal the line sum; total must equal subtotal minus discount plus tax.

A database uniqueness constraint protects party plus normalized invoice reference (trimmed, repeated whitespace collapsed, uppercase). Rejected bills retain this claim. Duplicate conflicts explicitly flag numbering exceptions for policy resolution; no bypass or clone exception exists.

New recording acquires the existing Business Date lifecycle lock, obtains the persisted OPEN date from BusinessDateService and compares the expected date. Invoice/due dates remain separate. Reviews and evidence changes do not require the recording day to remain open and do not call System Lock or operational-mutation guards.

Bill-row locks serialize corrections, evidence changes and decisions. Expected versions reject stale intent. Immutable verification decisions identify revision and evidence-set digest; approval links to the exact verification and rechecks stored file availability/integrity. No read/write loop hydrates transaction history. Directory and history pages are bounded at 100; source associations are bounded at 20 and resolved in at most three source queries. Current evidence is bounded at 20 files, each at most 5 MiB (5,242,880 bytes). Verification/approval perform bounded local file-integrity reads, not N+1 database queries.

Operation keys are bound to the authenticated actor, operation, target, version and payload (including file checksum for uploads). Matching completed receipts are returned before date/version/state checks. Reusing a key for another intent is rejected. Current authentication and object visibility remain required for replay; a receipt key is never an access credential. Equivalent decimal scales, source ordering and normalized invoice references share the same request fingerprint; original invoice display text is retained.

## Private evidence configuration

Set `ACCOUNTS_EVIDENCE_DIRECTORY` to an absolute, canonical private directory **outside repositories and public web roots**. An unset directory leaves evidence unavailable; it does not fall back to the repository or a public directory. Symlink paths are rejected; on macOS use canonical `/private/...` paths where applicable. New directories use owner-only access and files owner read/write. Existing directories must already be private; application code does not change their permissions. Production infrastructure is not configured by AC1.

PDFBox parses PDFs strictly (at most 2,000 pages); PNG chunk CRC/end checks and JPEG end/warning checks precede acceptance. ImageIO validates image content and declared media type with a 40-million-pixel limit. Bounded reads recheck stored length and checksum. Encrypted/unreadable PDFs, unreadable images, mismatches, empty files and files over 5 MiB (5,242,880 bytes) are rejected. Evidence downloads require the same bill visibility as detail/history, including for replaced documents. Downloads are attachments with no-store, nosniff and sandbox headers; storage keys and filesystem paths are not returned.

Uploads write and sync a private incomplete file, then atomically move it to an immutable generated key before metadata can commit. A failed database transaction can leave an unreferenced final blob, but cannot publish accepted evidence metadata. Retrying uses the same intent key; incomplete files and orphan blobs do not count for verification. Existing or replaced financial documents are never automatically removed. Orphan investigation/reconciliation is an operator task using a consistent database/filesystem snapshot; no cleanup job is provided. Database and blob backups must be managed together outside AC1.

This provides authenticated stored evidence, not antivirus certification or legal digital signatures.

## HTTP surface

All endpoints use `/api/accounts` and the existing ApiResponse contract, except authenticated binary downloads:

- `GET /context`, `GET/POST /parties`, `GET /sources?type=PROCUREMENT|RECEIPT|HOTEL`.
- `GET/POST /bills`; list accepts `q`, `status`, `page`, `size`.
- `GET /bills/{id}` and `GET /bills/{id}/history/{revisions|decisions|evidence}`.
- `POST /bills/{id}/{corrections|submit|verify|approve|hold|resume|return|reject}`.
- Multipart `POST /bills/{id}/evidence`: file, idempotencyKey, expectedVersion, invoiceDocument, optional replacesId.
- `GET /bills/{id}/evidence/{evidenceId}`.

There are no generic status-setting, delete, payment or public-file endpoints. AU1 receives fixed supplementary audit events; AC1 rows are excluded before global AU1 search/pagination and Business Date audit counts, so IDs, actors and counts cannot disclose protected bills. Records remain stored for object-authorized Accounts history. Free-form review reasons and evidence are exposed only through record-authorized Accounts history.

## Frontend and recovery

Only `/accounts` and `/accounts/bills` are promoted from T0; both use the same real register/table/detail screen. Other Accounts routes remain unavailable, including when demo mode is enabled. The Super Admin route shortcut cannot grant Accounts access. The three new responsibilities have Accounts login destinations and are available through A1 provisioning.

The Accounts context supplies the authenticated actor UUID because the existing login contract contains only the username. Ownership controls use this UUID, not a guessed identity. Read generations, authority-scoped view state and response validation reject stale or wrong-scope data. Confirmations freeze target, version and payload. A synchronous guard prevents overlapping submissions. Confirmed success remains success if a follow-up refresh fails. Uncertain operations retain actor-scoped original intent in session storage; retries are explicit. Upload recovery stores filename/type/size/checksum only and requires reselecting the exact file, never stored document bytes or credentials. Binary evidence is downloaded via the authenticated API.

No new reports, payment controls, hotel billing cycles, cheques, vouchers, deposits, Day Book or Payables calculations are implemented. An unavailable original preparer and legitimate duplicate-number exceptions remain explicit operational limitations requiring a later policy/workflow.

## Validation isolation

AC1 database tests refuse any URL except `jdbc:postgresql://127.0.0.1:55437/casino_ac1_test`. They create/drop randomized schemas and use JUnit temporary evidence directories. Existing SP1 database tests require a separate disposable cluster on port 55436. Neither suite reads application datasource configuration. No application was started against an existing database and no Flyway repair was run.

Focused backend tests: AccountsDatabaseTests, AccountsSecurityTests, AccountsEvidenceStorageTests, AccountsMigrationTests. Affected regressions cover S1 authentication/authority, A1 users, role permissions, AU1 reads/security, SP1 database/security, RP1 report service/security and Business Date/System Lock services/security.

Frontend validation: accounts, userManagement, t0, hrSafety, i1b, auditLogs, store and reports test files, plus `npm run build`. Backend packaging uses `./mvnw -DskipTests package`. Exact run outcomes are reported with delivery.

## Delivery verification

- 243 unique backend tests passed across the focused and affected regression runs; no failures, errors or skips in their final reports.
- 146 focused/affected frontend tests passed.
- Independent review details and commands are in AC1_FINAL_REVIEW.md. Focused tests overlap the affected runs; these are selected regressions, not entire suites.
- Backend package and frontend production build passed. Vite reports the existing large-bundle warning.
- Both tracked diffs and all new files passed whitespace checks.
- V1–V36 compared byte-for-byte with HEAD: unchanged. ST0_LOCAL_TEST_ENVIRONMENT.md SHA-256 remains `bebcef6a891cf543aca4ec0dd22a5c473389d8b3871a3ea83d116cfa120c11dc`.
- No operational/ST1/ST1-B database was used. No staging, commits or pushes.

### Exact changed-file manifest

`M` means modified and unstaged; `??` means new and untracked. The pre-existing ST0 file is excluded from the implementation manifest.

casino-erp:

```text
 M pom.xml
 M src/main/java/com/casino/casinoerp/config/SecurityConfig.java
 M src/main/java/com/casino/casinoerp/repository/AuditLogReadRepository.java
 M src/main/java/com/casino/casinoerp/repository/AuditLogRepository.java
 M src/main/java/com/casino/casinoerp/security/Role.java
 M src/main/java/com/casino/casinoerp/service/UserService.java
 M src/main/resources/application.properties
 M src/test/java/com/casino/casinoerp/UserServiceTests.java
?? docs/AC1_ACCOUNTS.md
?? docs/AC1_FINAL_REVIEW.md
?? src/main/java/com/casino/casinoerp/controller/AccountsController.java
?? src/main/java/com/casino/casinoerp/controller/AccountsExceptionHandler.java
?? src/main/java/com/casino/casinoerp/dto/AccountsDtos.java
?? src/main/java/com/casino/casinoerp/repository/AccountsRepository.java
?? src/main/java/com/casino/casinoerp/service/AccountsAmounts.java
?? src/main/java/com/casino/casinoerp/service/AccountsEvidenceStorage.java
?? src/main/java/com/casino/casinoerp/service/AccountsService.java
?? src/main/resources/db/migration/V37__add_accounts_bill_workflow.sql
?? src/test/java/com/casino/casinoerp/AccountsDatabaseTests.java
?? src/test/java/com/casino/casinoerp/AccountsEvidenceStorageTests.java
?? src/test/java/com/casino/casinoerp/AccountsMigrationTests.java
?? src/test/java/com/casino/casinoerp/AccountsSecurityTests.java
```

Casino-Management-System-main:

```text
 M src/api/accountsApi.js
 M src/components/layout/MainLayout.jsx
 M src/constants/permissions.js
 M src/constants/roles.js
 M src/constants/routePermissions.js
 M src/routes/AppRoutes.jsx
 M src/utils/accessControl.js
 M src/utils/testEnvironment.js
 M src/utils/userManagement.js
 M tests/store.test.mjs
 M tests/t0.test.mjs
 M tests/userManagement.test.mjs
?? src/pages/accounts/AccountsBills.jsx
?? src/utils/accounts.js
?? tests/accounts.test.mjs
```
