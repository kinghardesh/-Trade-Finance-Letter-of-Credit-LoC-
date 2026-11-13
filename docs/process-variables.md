# Process Variables & Task Identifiers — LoC Issuance

**Shared contract between backend (Member 1) and frontend/database (Member 2).**
If you rename anything here, tell the other team member first — BPMN, Java and React
must all use exactly the same names (they are case-sensitive).

- BPMN process id: **`loc-issuance`** (main process)
- Called process id: **`loc-compliance-screening`** (built in Step 4)
- BPMN file: `loc-backend/src/main/resources/bpmn/loc-issuance.bpmn`

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
| `tradeDocuments` | list of objects | see below | Documents to verify (sequential multi-instance) |

`tradeDocuments` element shape:

```json
{ "documentType": "COMMERCIAL_INVOICE", "documentReference": "INV-7781", "fileName": "invoice.pdf" }
```

`documentType` values: `COMMERCIAL_INVOICE`, `BILL_OF_LADING`, `PACKING_LIST`,
`CERTIFICATE_OF_ORIGIN`, `INSURANCE_CERTIFICATE`.

## 2. Variables produced by the workflow

| Variable | Type | Set by (task id) | Values / notes |
|---|---|---|---|
| `applicationValid` | boolean | `Task_ValidateApplication` | |
| `validationErrors` | list of string | `Task_ValidateApplication` | Empty when valid |
| `customerEligible` | boolean | `Task_CheckEligibility` | |
| `customerRiskRating` | string | `Task_CheckEligibility` | `LOW`, `MEDIUM`, `HIGH` |
| `complianceStatus` | string | `CallActivity_ComplianceScreening` | `CLEAR`, `REJECTED` |
| `sanctionsResults` | list of objects | compliance sub-process | One per screened party |
| `amlRiskScore` | number | compliance sub-process | 0–100 |
| `documentResults` | list of objects | `SubProcess_VerifyDocuments` | One per trade document |
| `documentsVerified` | boolean | `SubProcess_VerifyDocuments` | |
| `creditScore` | number | `Task_CreditAssessment` | 300–850 (mock) |
| `riskLevel` | string | `Task_CreditAssessment` | `LOW`, `MEDIUM`, `HIGH` |
| `creditApproved` | boolean | `Task_CreditAssessment` | |
| `approvalDecision` | string | `Task_ManagerApproval` (user) | `APPROVED`, `REJECTED` |
| `approverComments` | string | `Task_ManagerApproval` (user) | |
| `draftReference` | string | `Task_GenerateDraft` | e.g. `"DRAFT-LOC-APP-2026-0001-1"` |
| `termsVerified` | boolean | `Task_VerifyTerms` (user) | `false` → draft is regenerated |
| `termsComments` | string | `Task_VerifyTerms` (user) | |
| `locNumber` | string | `Task_IssueLoC` | e.g. `"LC-2026-000123"` |
| `issueDate` | string (date) | `Task_IssueLoC` | |
| `rejectionReason` | string | whichever step rejects | Shown to customer |

## 3. Application status (PostgreSQL `status` column / frontend badge)

The backend updates the status as the workflow moves forward:

`SUBMITTED` → `VALIDATING` → `CORRECTION_REQUIRED` → `COMPLIANCE_CHECK` →
`DOCUMENT_VERIFICATION` → `CREDIT_ASSESSMENT` → `PENDING_APPROVAL` →
`DRAFT_GENERATED` → `ISSUED` — or `REJECTED` at any point.

## 4. BPMN element identifiers

### Service tasks (Zeebe job workers in Spring Boot)

| Task name | Element id | Job type |
|---|---|---|
| Validate application details | `Task_ValidateApplication` | `validate-application` |
| Check customer eligibility | `Task_CheckEligibility` | `check-customer-eligibility` |
| Verify trade document | `Task_VerifyDocument` | `verify-trade-document` |
| Perform credit assessment | `Task_CreditAssessment` | `credit-assessment` *(becomes REST connector in Step 4)* |
| Generate LoC draft | `Task_GenerateDraft` | `generate-loc-draft` |
| Issue Letter of Credit | `Task_IssueLoC` | `issue-loc` |
| Record rejection | `Task_RecordRejection` | `record-rejection` |
| Notify customer | `Task_NotifyCustomer` | `notify-customer` *(becomes Email connector in Step 4)* |
| Notify customer of rejection | `Task_NotifyRejection` | `notify-rejection` *(becomes Email connector in Step 4)* |

### User tasks (shown in the React app / Camunda Tasklist)

| Task name | Element id | Candidate group | Variables the user submits |
|---|---|---|---|
| Correct application | `Task_CorrectApplication` | `trade-operations` | corrected input fields |
| Obtain manager approval | `Task_ManagerApproval` | `credit-managers` | `approvalDecision`, `approverComments` |
| Verify LoC terms | `Task_VerifyTerms` | `trade-operations` | `termsVerified`, `termsComments` |

### Gateways (FEEL conditions)

| Gateway | Element id | Outgoing conditions |
|---|---|---|
| Application valid? | `Gateway_ApplicationValid` | `=applicationValid = true` / `=applicationValid = false` |
| Customer eligible? | `Gateway_CustomerEligible` | `=customerEligible = true` / `=customerEligible = false` |
| Compliance clear? | `Gateway_ComplianceResult` | `=complianceStatus = "CLEAR"` / `=complianceStatus != "CLEAR"` |
| Credit approved? | `Gateway_CreditDecision` | `=creditApproved = true` / `=creditApproved = false` |
| Manager decision? | `Gateway_ApprovalDecision` | `=approvalDecision = "APPROVED"` / `=approvalDecision = "REJECTED"` |
| Terms correct? | `Gateway_TermsVerified` | `=termsVerified = true` / `=termsVerified = false` |

### End events

| Name | Element id |
|---|---|
| LoC issued | `End_LoCIssued` |
| LoC application rejected | `End_LoCRejected` |
