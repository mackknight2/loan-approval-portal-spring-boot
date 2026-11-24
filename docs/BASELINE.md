# Baseline: the app as it is today

This file records what the WestBank loan approval portal does and how healthy it is **before** any upgrade.
Every number below was measured on `main` at commit `f12c479`, with no application code changed.

## What the app does

WestBank is a made-up bank. The app is its home-loan website.

- **Customers** log in, keep their profile up to date, apply for a home loan, decide whether bank staff may see their sensitive data, and sign or decline the loan contract.
- **Bank staff** log into a separate staff portal. Each loan application passes through four roles: a **credit broker** checks the application, a **post-processing clerk** (or a **supervisor** for large loans) reviews it, and a **manager** grants or rejects the loan and signs the contract.
- **Web services (SOAP)** under `/services` do the business checks: whether the borrower can afford the loan, the borrower's banking privileges, credit worthiness, loan risk, who should review the file, contract creation, settlement and closing.
- The workflow that moves an application from one step to the next runs in a **separate engine (Apache ODE)** at `http://localhost:8080/ode/processes/LoanApproval`. That engine is **not in this repository**. The app only calls it and receives call-backs from it.

Demo logins (loaded from `src/main/resources/init-data.sql`): customers `alice@test.com` and `bob@test.com`; staff `broker`, `clerk`, `supervisor`, `manager`. All use the password `abcd1234`.

## Before numbers

| Measure | Value |
|---|---|
| Java version | Java 8 (built and tested with OpenJDK 1.8.0) |
| Framework version | Spring Boot 2.1.2.RELEASE, Apache CXF 3.2.7 |
| Tests passing / total | 56 / 56 (0 skipped) |
| Line coverage, hand-written code | 25.0% (484 of 1,936 lines) |
| Known vulnerabilities: critical / high / medium / low | 30 / 106 / 60 / 11 (207 unique IDs) |
| App starts and main flow works | Starts. Main flow works up to the contract step; signing the contract fails (see bugs below). No test covered it before this baseline. |

How each number was measured:

- **Build and tests:** `mvn -B clean verify` with Java 8.
- **Coverage:** JaCoCo. "Hand-written" excludes the classes Apache CXF generates from the WSDL files (`com.westbank.ws.{business,client,process,test}.*._2019._01`). Including generated code, coverage was 14.6% (491 of 3,353 lines).
- **Vulnerabilities:** Trivy 0.76.2, `trivy rootfs --scanners vuln` on the unpacked `target/loan-approval-portal-spring-boot-0.0.1.war`, counting unique vulnerability IDs by severity.

## After the upgrade (PR 2)

Measured on the upgrade branch with the same commands as above, but with Java 21 and Maven 3.9.16.
The "before" column is `main` after PR 1 was merged (PR 1 added 64 tests, so the numbers above are older than the starting point of this upgrade).

| Measure | Before (main after PR 1) | After (PR 2) |
|---|---|---|
| Java version | Java 8 | Java 21 (OpenJDK 21.0.13) |
| Framework version | Spring Boot 2.1.2.RELEASE, Apache CXF 3.2.7 | Spring Boot 4.1.3, Apache CXF 4.2.4 |
| Tests passing / total | 120 / 120 (0 skipped) | 120 / 120 (0 skipped), same 120 test names |
| Line coverage, hand-written code | 83.8% (1,623 of 1,936 lines) | 83.9% (1,627 of 1,940 lines) |
| Known vulnerabilities: critical / high / medium / low | 30 / 106 / 60 / 11 (207 unique IDs) | 0 / 0 / 0 / 0 |
| App starts and main flow works | Starts. Main flow works up to the contract step (bug 6). | Same. The pages, the `/services` list (12 services) and the end-to-end test give the same results as before. |

All eight bugs listed above behave exactly as before. They are still pinned by the same tests.

## Current behavior pinned by tests (bugs, not fixed)

These are real bugs in today's code. The safety-net tests record the current behavior so that an upgrade cannot change it silently. Fixing them is a separate decision.

1. **Customers can see other customers' loan files.** `LoanFileRepository.findAllByBorrowerId` uses `LEFT JOIN ... ON` instead of a filter, so the customer portal lists every loan file in the bank.
2. **Manager page has no login check on submit.** `POST /staff/manager` without a staff session fails with a server error (`NullPointerException`) instead of redirecting to the login page.
3. **The workflow engine is always told the manager rejected the loan.** `ManagerController` sends the decision to the engine before it reads the form, so the engine receives `granted = false` even when the manager grants the loan.
4. **Saving a valid customer profile fails.** `CustomerProfileController.updateProfile` casts the logged-in customer to `Long` and throws a `ClassCastException`.
5. **Creating a contract for an existing agency fails.** The contract web service re-inserts the agency row (for example `VIC002`) and returns a database error.
6. **Customers cannot sign a contract created by the web service.** The contract is not linked back to its loan file, so signing ends in an HTTP 500 error page and the loan file stays `APPROVED` instead of `ACCEPTED`.
7. **Password hashing is a stub.** `PasswordUtil.encode` returns `null` and `PasswordUtil.matches` always returns `false`. Customer and staff passwords are stored and compared as plain text.
8. **Some business checks are random.** `LoanFileService.checkCreditWorthiness` approves about 97% of requests at random, and `BankPrivilegePortImpl.verify` returns a random answer for every borrower except those named "Power".

## Risks and dependencies

- **External workflow engine.** The main flow depends on Apache ODE, which is not in this repository. Tests replace it with a stand-in (`@MockitoBean LoanApproval`), so they prove the app's side of each call, not the engine's.
- **Random business rules** (bug 8) mean the tests pin only the possible range of answers, not a single answer.
- **Asynchronous call-back.** The `CallbackLoanApproval` web service is one-way, so its effect lands after the call returns. The end-to-end test waits for it.
- **Build tooling.** Since PR 2 the build needs Java 21 and Maven 3.6.3 or newer. The Maven wrapper now downloads Maven 3.9.16, and the CXF code generation plugin uses the same version as the CXF runtime.
- **Travis CI** (`.travis.yml`) is left in place. GitHub Actions (`.github/workflows/ci.yml`) now builds and tests every pull request and every push to `main`.
