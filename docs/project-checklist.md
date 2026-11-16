# Project Checklist — Trade Finance LoC Issuance (Member 1: Workflow & Backend)

Legend: ✅ done and tested · ⚠️ done with a limitation · ⏳ open

## 1. Mandatory BPMN requirements

| # | Requirement | Where (element id) | How to show it | Status |
|---|---|---|---|---|
| 1 | Main BPMN process | `loc-issuance` in `loc-issuance.bpmn` | Open in Modeler; Operate → Processes → *Letter of Credit Issuance* | ✅ |
| 2 | Call Activity | `CallActivity_ComplianceScreening` → `loc-compliance-screening` | Operate: open an instance → click the call activity → *View called instance* | ✅ |
| 3 | Parallel Multi-Instance | `Task_ScreenParty` (one job per party, 3 at once) | Operate on the compliance instance: 3 instances of the task; backend log `[sanctions-screening]` ×3 | ✅ |
| 4 | Sequential Multi-Instance | `Task_VerifyDocument` (one document after another) | Backend log `[verify-trade-document] #1`, `#2` in order | ✅ |
| 5 | Embedded Subprocess | `SubProcess_VerifyDocuments` | Expanded box in the diagram | ✅ |
| 6 | Error Boundary Events | `Boundary_SanctionsHit` (SANCTIONS_HIT), `Boundary_DocumentDiscrepancy` (DOCUMENT_DISCREPANCY) | Demo scenarios 4 and 8 | ✅ |
| 7 | Timer Boundary Events | `Boundary_ApprovalTimeout` (escalation), `Boundary_CorrectionTimeout` (rejection) | Demo scenarios 7 and 8b (`approvalTimeout` / `correctionTimeout` = `PT30S`) | ✅ |
| 8 | Exclusive Gateways | 8 decision gateways + 3 merge gateways (the "regenerate draft" loop goes straight back into *Generate LoC draft*) | Every scenario takes a different branch | ✅ |
| + | Parallel Gateway (extra) | `Gateway_NotifySplit` / `Gateway_NotifyJoin`: customer and advising bank are emailed at the same time after issuing | Operate: both branches completed; Mailpit: 2 emails per issued LoC | ✅ |
| 9 | REST Connector | `Task_CreditAssessment` → `POST /api/mock/credit-bureau/assessments` | Scenario 6 (amount 6,000,000) → declined | ✅ |
| 10 | Email Connectors | `Task_NotifyCustomer`, `Task_NotifyAdvisingBank`, `Task_NotifyRejection` → Mailpit | http://localhost:8025 | ✅ |
| 11 | FEEL expressions & variable mappings | Gateway conditions, `parties` input mapping, AML output mapping (`complianceStatus`), REST result expression, timer durations, email subject/body | Properties panel → *Inputs / Outputs / Condition* | ✅ |
| + | Execution listeners (extra) | `update-status` on 8 elements | Status changes in `GET /api/applications/{id}/history` | ✅ |

## 2. Business workflow (12 steps)

| # | Step | BPMN element | Implemented by | Status |
|---|---|---|---|---|
| 1 | Receive LoC application | `StartEvent_ApplicationReceived` | `POST /api/applications` | ✅ |
| 2 | Validate application details | `Task_ValidateApplication` | `ValidateApplicationWorker` | ✅ |
| 3 | Check customer eligibility | `Task_CheckEligibility` | `CustomerEligibilityWorker` (mock registry) | ✅ |
| 4 | Sanctions screening | `Task_ScreenParty` (compliance process) | `SanctionsScreeningWorker` (mock list) | ✅ |
| 5 | AML review | `Task_AmlReview` (compliance process) | `AmlReviewWorker` (mock engine) | ✅ |
| 6 | Verify trade documents | `Task_VerifyDocument` in subprocess | `TradeDocumentWorker` | ✅ |
| 7 | Credit assessment | `Task_CreditAssessment` | REST connector → mock credit bureau | ✅ |
| 8 | Manager approval | `Task_ManagerApproval` (+ `Task_EscalatedApproval`) | User task via `/api/tasks` | ✅ |
| 9 | Generate LoC draft | `Task_GenerateDraft` | `LocDraftWorker` | ✅ |
| 10 | Verify LoC terms | `Task_VerifyTerms` | User task via `/api/tasks` | ✅ |
| 11 | Issue Letter of Credit | `Task_IssueLoC` | `IssueLocWorker` (DB sequence) | ✅ |
| 12 | Notify customer | `Task_NotifyCustomer` | Email connector | ✅ |

## 3. Exception scenarios

| Scenario | How it happens | Status |
|---|---|---|
| Rejection | 6 paths: not eligible, sanctions hit, AML, credit declined, manager rejects, correction timeout | ✅ |
| Correction | Validation fails or document discrepancy → *Correct application* → re-validation | ✅ |
| Correction of draft | `termsVerified = false` → draft regenerated (v2) | ✅ |
| Escalation | Approval not done in time → *Escalated approval (senior manager)* | ✅ |
| Timeout | Correction not done in time → rejected | ✅ |

All 8 end-to-end scenarios in `docs/demo-scenarios.md` passed against Camunda 8.8 + PostgreSQL on 2026-11-14.

## 4. Technology stack

| Technology | Version / where | Status |
|---|---|---|
| Camunda 8 (Camunda 8 Run) | 8.8.32 | ✅ |
| Camunda Desktop Modeler | 5.49.0 | ✅ |
| Java | 21.0.11 (`JAVA_HOME`) | ✅ |
| Spring Boot | 3.5.16 + `camunda-spring-boot-starter` 8.8.32 | ✅ |
| PostgreSQL | 16 in Docker (`loc-postgres`, port 5440), Flyway migrations | ✅ |
| Docker / Docker Compose | `docker/docker-compose.yml` (PostgreSQL + Mailpit) | ⚠️ infrastructure only — backend and Camunda run outside Docker |
| Git / GitHub | `kinghardesh/-Trade-Finance-Letter-of-Credit-LoC-` | ✅ (push each new commit) |
| React + Vite | `loc-frontend/` (Member 2) | ⏳ teammate |

## 5. Backend deliverables (Member 1)

| Item | Where | Status |
|---|---|---|
| REST API | `ApplicationController`, `TaskController` — see `docs/api-contract.md` | ✅ |
| Zeebe job workers | `worker/` package, 9 workers | ✅ |
| Spring Boot ↔ Camunda integration | `application.properties` (`camunda.client.*`), `@Deployment` auto-deploy | ✅ |
| Business rules & mock services | `ApplicationValidator`, `mock/` package | ✅ |
| Process variables contract | `docs/process-variables.md` | ✅ |
| Database schema proposal | `docs/database.md`, `V1__initial_schema.sql` | ✅ (teammate to review) |
| Automated tests | 8 JUnit tests (`mvnw test`, H2 database) | ✅ |
| End-to-end test script in the repo | — | ⏳ (script exists only on the developer PC) |
| Camunda Forms for Tasklist | `loc-correct-application.form`, `loc-manager-approval.form` (manager + escalated), `loc-verify-terms.form`; corrections synced to PostgreSQL by the `sync-corrections` end listener | ✅ (correction form completed in Tasklist; approval/terms forms linked and checked via API) |
| One-command startup | — | ⏳ Step 8 |

## 6. Before the demo / submission

- [ ] `git push` — latest commit on GitHub
- [ ] Camunda 8 Run started (`c8run.exe start`)
- [ ] Docker started (`docker compose -f docker/docker-compose.yml up -d`)
- [ ] Backend started (`mvnw.cmd spring-boot:run`) → `Started LocBackendApplication`
- [ ] Operate, Mailpit and the React app open in the browser
- [ ] Teammate has reviewed `docs/api-contract.md` and `docs/database.md`
- [ ] Screenshots for the report: main BPMN, compliance BPMN, Operate instance with call activity, Mailpit email, status history
