# AC1 independent final review — PASS after targeted correction

Reviewed 2026-09-19 against the complete independent-review prompt and the actual source, new files, V37, storage, tests and delivery manifest. No AC0 restart or later financial phase was implemented. No unresolved AC1 blocker remains.

## Verified defects corrected

| Finding | Final source evidence | Correction and validation |
| --- | --- | --- |
| A completed bill receipt could bypass current object visibility after a role change | service/AccountsService.java:55 | Replay now checks current bill visibility after matching actor/fingerprint, before returning the receipt. Completed state/date/version changes still do not invalidate matching replay. PostgreSQL regression covers visibility denial. |
| Global audit metadata and Business Date audit counts could disclose hidden AC1 records | repository/AuditLogReadRepository.java:18; repository/AuditLogRepository.java:12 | Exclude ACCOUNTS rows before global filtering/pagination/counting. Keep stored audit events and object-authorized Accounts histories. Real SQL test covers hidden events, search, pagination and count. |
| JSON money could enter JavaScript as Number; equivalent scale/reference requests could fingerprint differently | dto/AccountsDtos.java:12; service/AccountsService.java:88; frontend src/utils/accounts.js:6 | Exact two-decimal strings; BigDecimal validation without rounding; canonical replay amounts/source ordering/reference with original display text retained. Frontend rejects numeric money. |
| JDBC timestamp serialization could lose PostgreSQL microseconds | repository/AccountsRepository.java:12 | Convert JDBC timestamps to LocalDateTime and dates to LocalDate. Regression checks six-digit fractional timestamp serialization. |
| Real truncated image content could pass the original image validator | service/AccountsEvidenceStorage.java:39 | Reproduced with synthetic real PNG/JPEG. Added strict PDF parsing, end/chunk/CRC checks, JPEG warnings/end checks, memory-only image stream and bounded stored-file reads. Seven storage tests pass. |
| Unsafe filenames and existing nonprivate directories needed explicit rejection | service/AccountsEvidenceStorage.java:19; :30; :94 | Reject path/control/header characters and nonprivate existing roots; do not chmod an arbitrary existing directory. Generated keys, NOFOLLOW reads and integrity checks retained. Exact limit consistently 5 MiB / 5,242,880 bytes. |
| Old refresh/download callbacks could act on a changed selection | frontend src/pages/accounts/AccountsBills.jsx:26; src/utils/accounts.js:59 | Selection/auth generation checks across refresh continuations and binary downloads; reset replacement/history state; cleanup browser URLs. Production-page race and late bytes/errors tests pass. |
| Confirmation lacked explicit frozen evidence/verification context | frontend src/pages/accounts/AccountsBills.jsx:51 | Confirmation includes revision, evidence IDs and verification ID/actor/time/digest alongside immutable target/version/payload. |

Backend source references above are relative to src/main/java/com/casino/casinoerp. Other source citations use repository-relative paths.

## Endpoint and role/visibility matrix

All endpoints below are under `/api/accounts`. JSON uses ApiResponse; document downloads are authenticated binary attachments.

| Endpoints | Authority |
| --- | --- |
| GET /context, GET /parties, GET /sources?type=PROCUREMENT\|RECEIPT\|HOTEL | Four AC1 roles; supporting references return bounded IDs/display labels, not full operational records |
| POST /parties, POST /bills | STORE_MANAGER or ACCOUNTANT_HEAD |
| GET /bills (q/status/page/size), GET /bills/{id} | Current object visibility below |
| GET /bills/{id}/history/{revisions\|decisions\|evidence} | Same current object visibility |
| POST /bills/{id}/corrections, POST /bills/{id}/submit | Original preparer in current preparer role; Draft/Returned only |
| Multipart POST /bills/{id}/evidence | Same preparer/editability rule, expectedVersion and retry key |
| GET /bills/{id}/evidence/{evidenceId} | Same bill visibility, bill/evidence association checked before bytes |
| POST /bills/{id}/verify | ACCOUNTS_MANAGER, Submitted, distinct original preparer |
| POST /bills/{id}/approve | DIRECTOR, Awaiting Director Approval, distinct original preparer/current verifier |
| POST /bills/{id}/{hold\|resume\|return\|reject} | Current stage reviewer; explicit stage/state/reason and person restrictions |

| Current active role | Bill visibility |
| --- | --- |
| STORE_MANAGER, ACCOUNTANT_HEAD | Own immutable recorded_by ID, every state |
| ACCOUNTS_MANAGER | ever_submitted, including later Returned/corrections/final history; never-submitted other Drafts hidden |
| DIRECTOR | ever_verified, including later history; never-verified bills hidden |
| SUPER_ADMIN and every other role | No AC1 bill/document/workflow access |

SecurityConfig.java:166 matches Accounts before the authenticated fallback. AccountsService.actor/visible enforce active current authority and object scope. Visibility is in list SQL before limit/offset; detail/history/download use the same scope and generic not-found responses. No export endpoint exists. New roles are recognized by backend Role, S1, A1 provisioning, frontend role/permission/route maps and Sidebar. Login uses the Accounts landing route. The frontend Accounts restriction executes before generic Super Admin bypass, including decoded/case-normalized paths. Only /accounts and /accounts/bills are promoted from T0. Existing unrelated privileges remain unchanged; new roles gain no HR administration, cashier, Store stock mutation, Audit or Reports authority.

## Workflow, accounting and concurrency

PASS: Draft → Submitted → Awaiting Director Approval → APPROVED_FOR_PAYMENT. Approval records authorization for a future payment, never a payment or legal digital signature. Only the original preparer edits own Draft/Returned records. Reviewer content editing, self-review after role change, stage skipping and terminal mutation are denied. Same-stage authorized reviewers may resume another reviewer's hold. Return makes a new unverified revision even without content changes; Director return requires fresh verification. Rejected/approved invoices retain duplicate claims.

PASS: immutable revision snapshots retain party/source display references, exact lines/amounts and evidence IDs. Verification binds revision/evidence digest; approval links the specific verification and rechecks physical file integrity. Historical visibility never revives obsolete verification. JDBC transactions contain revision, decision, receipt and audit writes. Bill locks plus expected versions serialize conflicting changes; retry advisory lock is acquired before bill lock.

Real PostgreSQL concurrent tests cover duplicate invoice creation, same-key creation, edit/submit, evidence/submit, evidence-finalization/verification, verification/return, verification/rejection, approval/return. They assert only the valid serialized outcome and no stale partial decision. Database rollback after storage leaves no accepted metadata or operation receipt. These use production services/repository/SQL; authenticated-user, clock/date and audit collaborators are controlled fixtures. They are not a full application HTTP/DB end-to-end test.

PASS: NPR only, exact BigDecimal arithmetic, maximum 999,999,999,999.99, nonnegative components, line sum = subtotal, total = subtotal − discount + tax, no rounding. Invalid scale/range and totals fail. Strings preserve exact wire/display money; no parseFloat/Number money conversion. Party plus normalized invoice reference is database-unique across concurrency and all states; generic duplicate errors do not disclose hidden bill content. Source links are read-only and snapshot display references; they do not receive stock or update costs.

PASS: new bills take the existing Business Date lifecycle lock and persisted OPEN date, checking expected date. Invoice/due dates are separate. Later review/correction/evidence retain recording Business Date and actual action timestamps without reopening gaming days. No Accounts action calls System Lock mutation guards or cashier settlement/new-operation guards. Tests cover absent/changed date and review after close; mocked guard assertions are distinguished from live System Lock testing.

PASS: receipts are immutable transactional records bound to actor/operation/target/version/normalized payload (upload includes checksum). Same completed operation returns once even after forward state/date changes, subject to current visibility. New stale operations conflict. Browser retries are explicit, preserve original intent, and synchronous submit guard blocks double clicks. Successful POST remains success on refresh failure. Malformed success remains uncertain. Recovery stores metadata, never evidence bytes/JWT/passwords. Authentication/selection generations invalidate stale read/error/finally callbacks. Upload retry requires matching original file metadata/checksum.

## Storage, recovery and deployment

PASS: configurable canonical absolute private root outside repositories/public roots; no insecure fallback. Missing/unusable config fails closed without disclosing a server path to users. New directories 0700, blobs 0600; existing nonprivate roots rejected. Plain filenames only, UUID storage keys, no symlink path/blob reads. Downloads enforce current authentication/object visibility and use attachment, no-store, nosniff and sandbox headers; no public URL/static route.

Synthetic PDF/JPEG/PNG content validation includes exact 5,242,880-byte maximum, strict PDF parsing and 2,000-page cap, PNG CRC/end validation, JPEG truncation warnings/end validation and 40-million-pixel image bound. This is not malware scanning or signature certification. Blob length/checksum checked on read and review gates. Missing/corrupt evidence cannot satisfy verification/approval; an invoice document is mandatory. Evidence loss after approval makes download unavailable, not automatic approval reversal.

Uploads fsync a private temporary file and atomically rename before DB metadata can commit. DB/audit failure can leave an orphan immutable blob, but no accepted association; retry creates one accepted evidence association and subsequent replay returns its receipt. Tests exercise rollback, missing/corrupt files, lost-response replay and synthetic interrupted-upload residue. The residue test models an interrupted process's incomplete file; it is not a real OS crash/power-loss test. Incomplete/orphan files cannot satisfy invoice gates. Financial evidence is never automatically deleted. Operators must reconcile orphans against a consistent DB/blob snapshot and back up both together. No production storage provisioning, proxy configuration, backup restoration, antivirus, filesystem crash test or live browser run was performed.

## V37 and cross-module safety

PASS: V37 only adds Accounts tables, constraints, indexes and immutable-history triggers, referencing actual UUID core.users. Party/invoice uniqueness, current revision FK, same-bill verification/approval links, checked states, receipt uniqueness, immutable original creator/date and sticky visibility are present. Replacement association and review-stage/person/revision rules are enforced by the locked service; not every application rule is claimed as a database constraint. No migration edits were made during this review: V37 SHA-256 c0fb7aae82ca1afe41b9cd3f90fbb111fe076b096f3829eba6addd97886de39d. Its application state outside disposable tests was not queried or assumed.

AccountsMigrationTests applies the real V36 then V37 SQL in an isolated randomized schema with representative UUID users, HR reference keys, existing Store item and opening movement, asserting preservation and restrictive user FK. This is a representative V36-era forward-migration test, not a full V1–V37 Flyway deployment rehearsal. All V1–V36 bytes match HEAD. V37 remains the only new/highest migration; no V38, Flyway repair or operational migration was performed.

No AC1 write creates payments, cheques, deposits, cash/Day Book movements or payable settlement, and no existing Store/F&B/CRM/cashier/Reception/Business Date close implementation was changed. Two narrow existing audit-read changes are necessary to prevent protected AC1 metadata/count leakage; non-Accounts rows retain existing behavior.

## Validation

Backend focused final command (48 tests):
```sh
AC1_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55437/casino_ac1_test ./mvnw -q -Dtest=AccountsDatabaseTests,AccountsSecurityTests,AccountsEvidenceStorageTests,AccountsMigrationTests test
```
Affected combined run command (240 tests before three additional current-role token cases; final focused run adds these, yielding 243 unique selected tests):
```sh
AC1_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55437/casino_ac1_test SP1_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55436/casino_sp1_test ./mvnw -q -Dtest=AccountsDatabaseTests,AccountsSecurityTests,AccountsEvidenceStorageTests,AccountsMigrationTests,S1AccountAuthorityTests,S1AuthenticationTests,JwtAuthenticationFilterSecurityTests,RoleTests,RolePermissionServiceTests,UserServiceTests,UserSecurityTests,AuditLogReadTests,AuditLogSecurityTests,StoreDatabaseTests,StoreSecurityTests,RunningFundsReportServiceTests,RunningFundsReportSecurityTests,BusinessDateServiceTests,SystemLockServiceTests,SystemLockSecurityTests test
```
Final focused suite overlaps the combined run; do not add 48 to 240. Latest per-suite counts follow; every suite has zero failures/errors/skips.

| Suite | Tests |
| --- | --- |
| AccountsDatabaseTests | 21 |
| AccountsSecurityTests | 19 |
| AccountsEvidenceStorageTests | 7 |
| AccountsMigrationTests | 1 |
| S1AccountAuthorityTests | 5 |
| S1AuthenticationTests | 16 |
| JwtAuthenticationFilterSecurityTests | 7 |
| RoleTests | 5 |
| RolePermissionServiceTests | 10 |
| UserServiceTests | 13 |
| UserSecurityTests | 14 |
| AuditLogReadTests | 5 |
| AuditLogSecurityTests | 13 |
| StoreDatabaseTests | 25 |
| StoreSecurityTests | 29 |
| RunningFundsReportServiceTests | 7 |
| RunningFundsReportSecurityTests | 13 |
| BusinessDateServiceTests | 28 |
| SystemLockServiceTests | 2 |
| SystemLockSecurityTests | 3 |

Frontend commands:
```sh
node --test tests/accounts.test.mjs
node --test tests/accounts.test.mjs tests/userManagement.test.mjs tests/t0.test.mjs tests/hrSafety.test.mjs tests/i1b.test.mjs tests/auditLogs.test.mjs tests/store.test.mjs tests/reports.test.mjs
npm run build
```
Focused 21/21; selected combined 146/146, zero failures/skips/cancelled. The 21 overlap 146. The original 142 was a selected regression count, not the entire frontend suite; four focused review cases were added. Backend original 230 likewise represented selected suites; 13 review cases were added. No test was removed or skipped to pass.

Backend `./mvnw -DskipTests package`: BUILD SUCCESS. Frontend production build passed; existing >500-kB chunk warning remains. Both tracked diffs and new files pass whitespace checks. Earlier executions encountered a reproduced truncated-file defect, a canonical-path test fixture issue, and sandbox-denied localhost/temp-file access; corrected final executions passed. Compile mistakes in added tests were corrected before final runs.

Tests used newly initialized private temporary PostgreSQL clusters on localhost ports 55437 (AC1) and 55436 (SP1), randomized schemas, synthetic identities/documents and JUnit private evidence directories. Both clusters are stopped. No application was started against operational/ST1/ST1-B data. No operational files/data were changed. No live browser run is claimed.

## Scope reconciliation and final Git evidence

Original manifest matched on arrival. Review adds two modified existing audit repositories to close the metadata boundary, one new AccountsMigrationTests file for the requested V36-era preservation test, and this review report. Existing AC1 source/tests and delivery documentation contain the targeted corrections. No unrelated implementation file was added. ST0 is the only unrelated pre-existing untracked file and remains untouched.

ST0 SHA-256: `bebcef6a891cf543aca4ec0dd22a5c473389d8b3871a3ea83d116cfa120c11dc`.
Both indexes empty; no stage/commit/push/merge/rebase. All AC1 work remains modified or untracked. Git evidence follows. Ordinary diff stats omit new files, so their line counts are listed separately (including this report, whose final line count is reported separately at final verification).

### casino-erp

Branch: `feature/hr-backend-integration-audit`; HEAD: `9e84677 Add authoritative Store and Purchase workflow`.

```text
 M pom.xml
 M src/main/java/com/casino/casinoerp/config/SecurityConfig.java
 M src/main/java/com/casino/casinoerp/repository/AuditLogReadRepository.java
 M src/main/java/com/casino/casinoerp/repository/AuditLogRepository.java
 M src/main/java/com/casino/casinoerp/security/Role.java
 M src/main/java/com/casino/casinoerp/service/UserService.java
 M src/main/resources/application.properties
 M src/test/java/com/casino/casinoerp/UserServiceTests.java
?? ST0_LOCAL_TEST_ENVIRONMENT.md
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

Tracked git diff --stat:
```text
 pom.xml                                                             | 1 +
 src/main/java/com/casino/casinoerp/config/SecurityConfig.java       | 3 +++
 .../com/casino/casinoerp/repository/AuditLogReadRepository.java     | 2 +-
 .../java/com/casino/casinoerp/repository/AuditLogRepository.java    | 2 ++
 src/main/java/com/casino/casinoerp/security/Role.java               | 6 ++++++
 src/main/java/com/casino/casinoerp/service/UserService.java         | 2 +-
 src/main/resources/application.properties                           | 5 +++++
 src/test/java/com/casino/casinoerp/UserServiceTests.java            | 5 +++++
 8 files changed, 24 insertions(+), 2 deletions(-)
```

New-file line counts (all additions; ST0 excluded):
```text
131 docs/AC1_ACCOUNTS.md
docs/AC1_FINAL_REVIEW.md (this review report)
46 src/main/java/com/casino/casinoerp/controller/AccountsController.java
27 src/main/java/com/casino/casinoerp/controller/AccountsExceptionHandler.java
31 src/main/java/com/casino/casinoerp/dto/AccountsDtos.java
28 src/main/java/com/casino/casinoerp/repository/AccountsRepository.java
28 src/main/java/com/casino/casinoerp/service/AccountsAmounts.java
104 src/main/java/com/casino/casinoerp/service/AccountsEvidenceStorage.java
259 src/main/java/com/casino/casinoerp/service/AccountsService.java
77 src/main/resources/db/migration/V37__add_accounts_bill_workflow.sql
252 src/test/java/com/casino/casinoerp/AccountsDatabaseTests.java
54 src/test/java/com/casino/casinoerp/AccountsEvidenceStorageTests.java
38 src/test/java/com/casino/casinoerp/AccountsMigrationTests.java
40 src/test/java/com/casino/casinoerp/AccountsSecurityTests.java
```

### Casino-Management-System-main

Branch: `feature/hr-frontend-integration`; HEAD: `89fa33a Replace Store prototype with authoritative inventory workflow`.

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

Tracked git diff --stat:
```text
 src/api/accountsApi.js               | 363 ++++-------------------------------
 src/components/layout/MainLayout.jsx |   5 +-
 src/constants/permissions.js         |   7 +-
 src/constants/roles.js               |   3 +
 src/constants/routePermissions.js    |   1 +
 src/routes/AppRoutes.jsx             |  43 +----
 src/utils/accessControl.js           |   6 +
 src/utils/testEnvironment.js         |   1 +
 src/utils/userManagement.js          |   2 +-
 tests/store.test.mjs                 |   2 +-
 tests/t0.test.mjs                    |   2 +-
 tests/userManagement.test.mjs        |   6 +-
 12 files changed, 58 insertions(+), 383 deletions(-)
```

New-file line counts (all additions; ST0 excluded):
```text
115 src/pages/accounts/AccountsBills.jsx
94 src/utils/accounts.js
158 tests/accounts.test.mjs
```

## Limitations and acceptance

No reassignment/takeover, duplicate-number exceptions, approval reversal, payment execution or deployment infrastructure was introduced. Unavailable original preparers and legitimate duplicate-number exceptions still require a later approved workflow. Deployment requires a private durable canonical evidence root and coordinated DB/blob backup/recovery. Automated evidence does not certify production filesystem/backup behavior or malware safety. These are documented scope limits, not newly invented policy or an unresolved AC1 code blocker.

SAFE TO STAGE: YES

ACCOUNTS AC1 FINAL REVIEW COMPLETE
