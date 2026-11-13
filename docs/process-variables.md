# Process Variables & Task Identifiers — LoC Issuance

**Shared contract between backend (Member 1) and frontend/database (Member 2).**
If you rename anything here, tell the other team member first — BPMN, Java and React
must all use exactly the same names (they are case-sensitive).

- Main process id: **`loc-issuance`** → `loc-backend/src/main/resources/bpmn/loc-issuance.bpmn`
- Called process id: **`loc-compliance-screening`** → `loc-backend/src/main/resources/bpmn/loc-compliance-screening.bpmn`

Naming rules: variables are `camelCase`; enum values are `UPPER_SNAKE_CASE` strings;
dates are ISO-8601 strings (`2026-12-31`); money is a number + separate `currency`.

---

## 1. Input variables (sent when the process is started)

The frontend submits the application form → backend saves it in PostgreSQL →
backend starts process `loc-issuance` with these variables.

| Variable | Type | Example | Notes |
|---|---|---|---|
| `applicationId` | string | `"LOC-APP-2026-0001"` | Business key; primary key in DB |
| `customerId` | string | `"CUST-1001"` | Applicant customer number |
| `applicantName` | string | `"Rhein Machinery GmbH"` | Importer (buyer) |
| `applicantEmail` | string | `"buyer@example.com"` | Used by email notifications |
| `applicantCountry` | string | `"DE"` | ISO 3166-1 alpha-2 |
| `beneficiaryName` | string | `"Shanghai Precision Tools Ltd"` | Exporter (seller) |
| `beneficiaryCountry` | string | `"CN"` | ISO 3166-1 alpha-2 |
| `beneficiaryBankName` | string | `"Bank of Example, Shanghai"` | Advising bank |
| `amount` | number | `250000` | LoC amount, > 0 |
| `currency` | string | `"EUR"` | ISO 4217 |
| `goodsDescription` | string | `"CNC milling machines"` | |
| `latestShipmentDate` | string (date) | `"2026-12-15"` | |
| `expiryDate` | string (date) | `"2027-01-31"` | Must be after `latestShipmentDate` |
| `tradeDocuments` | list of objects | see below | Verified one by one (sequential multi-instance) |
| `correctionTimeout` | string (ISO duration) | `"PT2M"` | *Optional, demo only.* Default `PT48H` |
| `approvalTimeout` | string (ISO duration) | `"PT1M"` | *Optional, demo only.* Default `PT24H` |

`tradeDocuments` element shape:

```json
{ "documentType": "COMMERCIAL_INVOICE", "documentReference": "INV-7781", "fileName": "invoice.pdf" }
```

`documentType` values: `COMMERCIAL_INVOICE`, `BILL_OF_LADING`, `PACKING_LIST`,
`CERTIFICATE_OF_ORIGIN`, `INSURANCE_CERTIFICATE`.

## 2. Variables produced by the workflow

| Variable | Type | Set by | Values / notes |
|---|---|---|---|
| `applicationValid` | boolean | `Task_ValidateApplication` | |
| `validationErrors` | list of string | `Task_ValidateApplication` | Empty when valid |
| `customerEligible` | boolean | `Task_CheckEligibility` | |
| `customerRiskRating` | string | `Task_CheckEligibility` | `LOW`, `MEDIUM`, `HIGH` |
| `parties` | list of objects | call activity input mapping (FEEL) | `{name, country, role}`; roles `APPLICANT`, `BENEFICIARY`, `ADVISING_BANK`. Only exists inside the compliance call |
| `sanctionsResults` | list of objects | `Task_ScreenParty` (parallel MI) | element: `{partyName, role, matchFound, matchScore}` |
| `amlRiskScore` | number | `Task_AmlReview` | 0–100; ≥ 70 → rejected |
| `complianceStatus` | string | `Task_AmlReview` output mapping (FEEL) | `CLEAR`, `REJECTED` |
| `documentResults` | list of objects | `Task_VerifyDocument` (sequential MI) | element: `{documentType, documentReference, valid, remarks}` |
| `documentsVerified` | boolean | `SubProcess_VerifyDocuments` output mapping | |
| `correctionReason` | string | document discrepancy error boundary | Shown on the "Correct application" task |
| `creditScore` | number | `Task_CreditAssessment` (REST connector) | 300–850 (mock) |
| `riskLevel` | string | `Task_CreditAssessment` | `LOW`, `MEDIUM`, `HIGH` |
| `creditApproved` | boolean | `Task_CreditAssessment` | |
| `approvalDecision` | string | `Task_ManagerApproval` / `Task_EscalatedApproval` (user) | `APPROVED`, `REJECTED` |
| `approverComments` | string | same user tasks | |
| `approvalEscalated` | boolean | approval timer boundary | `true` when escalated to senior manager |
| `draftReference` | string | `Task_GenerateDraft` | e.g. `"DRAFT-LOC-APP-2026-0001-1"` |
| `termsVerified` | boolean | `Task_VerifyTerms` (user) | `false` → draft is regenerated |
| `termsComments` | string | `Task_VerifyTerms` (user) | |
| `locNumber` | string | `Task_IssueLoC` | e.g. `"LC-2026-000123"` |
| `issueDate` | string (date) | `Task_IssueLoC` | |
| `rejectionReason` | string | whichever step rejects | Shown to customer and in the rejection email |

## 3. Application status (PostgreSQL `status` column / frontend badge)

The backend updates the status as the workflow moves forward:

`SUBMITTED` → `VALIDATING` → `CORRECTION_REQUIRED` → `COMPLIANCE_CHECK` →
`DOCUMENT_VERIFICATION` → `CREDIT_ASSESSMENT` → `PENDING_APPROVAL` → `ESCALATED` →
`DRAFT_GENERATED` → `ISSUED` — or `REJECTED` at any point.

## 4. BPMN element identifiers

### Service tasks handled by Spring Boot job workers

| Task name | Element id | Job type | Process |
|---|---|---|---|
| Validate application details | `Task_ValidateApplication` | `validate-application` | main |
| Check customer eligibility | `Task_CheckEligibility` | `check-customer-eligibility` | main |
| Screen party against sanctions lists | `Task_ScreenParty` | `sanctions-screening` | compliance (parallel MI, one job per party, input `party`, return `sanctionsResult`) |
| Perform AML review | `Task_AmlReview` | `aml-review` | compliance (return `amlRiskScore`, `amlFindings`) |
| Verify trade document | `Task_VerifyDocument` | `verify-trade-document` | main, inside subprocess (sequential MI, input `document`, return `documentResult`) |
| Generate LoC draft | `Task_GenerateDraft` | `generate-loc-draft` | main |
| Issue Letter of Credit | `Task_IssueLoC` | `issue-loc` | main |
| Record rejection | `Task_RecordRejection` | `record-rejection` | main |

### Service tasks handled by Camunda Connectors (no Java worker)

| Task name | Element id | Connector | Calls |
|---|---|---|---|
| Perform credit assessment | `Task_CreditAssessment` | REST (`io.camunda:http-json:1`) | `POST http://localhost:8081/api/mock/credit-bureau/assessments` |
| Notify customer | `Task_NotifyCustomer` | Email (`io.camunda:email:1`) | SMTP `localhost:1025` (Mailpit) |
| Notify customer of rejection | `Task_NotifyRejection` | Email (`io.camunda:email:1`) | SMTP `localhost:1025` (Mailpit) |

Mock credit bureau contract (built by the backend in Step 5):

```
POST /api/mock/credit-bureau/assessments
Request:  { "applicationId": "...", "customerId": "CUST-1001", "amount": 250000, "currency": "EUR", "customerRiskRating": "LOW" }
Response: { "creditScore": 742, "riskLevel": "LOW", "creditApproved": true }
```

### User tasks (shown in the React app / Camunda Tasklist)

| Task name | Element id | Candidate group | Variables the user submits |
|---|---|---|---|
| Correct application | `Task_CorrectApplication` | `trade-operations` | corrected input fields |
| Obtain manager approval | `Task_ManagerApproval` | `credit-managers` | `approvalDecision`, `approverComments` |
| Escalated approval (senior manager) | `Task_EscalatedApproval` | `senior-managers` | `approvalDecision`, `approverComments` |
| Verify LoC terms | `Task_VerifyTerms` | `trade-operations` | `termsVerified`, `termsComments` |

### Boundary events

| Name | Element id | Type | Attached to | Effect |
|---|---|---|---|---|
| Correction deadline passed | `Boundary_CorrectionTimeout` | Timer (interrupting), `correctionTimeout` or `PT48H` | `Task_CorrectApplication` | Rejects the application |
| Sanctions hit | `Boundary_SanctionsHit` | Error `SANCTIONS_HIT` | `CallActivity_ComplianceScreening` | Rejects the application |
| Document discrepancy | `Boundary_DocumentDiscrepancy` | Error `DOCUMENT_DISCREPANCY` | `SubProcess_VerifyDocuments` | Back to "Correct application" |
| Approval overdue | `Boundary_ApprovalTimeout` | Timer (interrupting), `approvalTimeout` or `PT24H` | `Task_ManagerApproval` | Escalates to senior manager |

### Gateways (FEEL conditions)

| Gateway | Element id | Outgoing conditions |
|---|---|---|
| Application valid? | `Gateway_ApplicationValid` | `=applicationValid = true` / `=applicationValid = false` |
| Customer eligible? | `Gateway_CustomerEligible` | `=customerEligible = true` / `=customerEligible = false` |
| Sanctions match found? *(compliance)* | `Gateway_SanctionsMatch` | `=some result in sanctionsResults satisfies result.matchFound = true` / `=every ... = false` |
| Compliance clear? | `Gateway_ComplianceResult` | `=complianceStatus = "CLEAR"` / `=complianceStatus != "CLEAR"` |
| All documents valid? *(subprocess)* | `Gateway_DocumentsValid` | `=every result in documentResults satisfies result.valid = true` / `=some ... = false` |
| Credit approved? | `Gateway_CreditDecision` | `=creditApproved = true` / `=creditApproved = false` |
| Manager decision? | `Gateway_ApprovalDecision` | `=approvalDecision = "APPROVED"` / `=approvalDecision = "REJECTED"` |
| Terms correct? | `Gateway_TermsVerified` | `=termsVerified = true` / `=termsVerified = false` |

### End events

| Name | Element id |
|---|---|
| LoC issued | `End_LoCIssued` |
| LoC application rejected | `End_LoCRejected` |
| Sanctions hit *(error end, compliance)* | `End_SanctionsHit` |
| Discrepancy found *(error end, subprocess)* | `End_DocumentDiscrepancy` |

## 5. Local test infrastructure

| What | Address |
|---|---|
| Mailpit inbox (all emails land here) | http://localhost:8025 |
| Start it | `docker compose -f docker/docker-compose.yml up -d` |
