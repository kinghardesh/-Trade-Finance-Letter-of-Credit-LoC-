# Backend REST API Contract — LoC Issuance

Base URL (local): **`http://localhost:8081`**
All bodies are JSON (`Content-Type: application/json`). CORS allows `http://localhost:5173` (Vite).

> **Keys are strings.** `processInstanceKey` and `userTaskKey` are 64-bit numbers in Camunda.
> The API always sends them as **strings** — keep them as strings in React and send them back unchanged.

Variable names and enum values follow [`process-variables.md`](process-variables.md).

---

## Health

### `GET /api/ping`
```json
{ "status": "UP", "service": "loc-backend", "time": "2026-11-14T10:00:00+05:30" }
```

---

## Applications

### `POST /api/applications` — submit an application (starts the workflow)

Request:
```json
{
  "customerId": "CUST-1001",
  "applicantName": "Rhein Machinery GmbH",
  "applicantEmail": "buyer@example.com",
  "applicantCountry": "DE",
  "beneficiaryName": "Shanghai Precision Tools Ltd",
  "beneficiaryCountry": "CN",
  "beneficiaryBankName": "Bank of Example, Shanghai",
  "amount": 250000,
  "currency": "EUR",
  "goodsDescription": "CNC milling machines",
  "latestShipmentDate": "2026-12-15",
  "expiryDate": "2027-01-31",
  "tradeDocuments": [
    { "documentType": "COMMERCIAL_INVOICE", "documentReference": "INV-7781", "fileName": "invoice.pdf" },
    { "documentType": "BILL_OF_LADING", "documentReference": "BL-5521", "fileName": "bill-of-lading.pdf" }
  ],
  "correctionTimeout": "PT2M",
  "approvalTimeout": "PT1M"
}
```

| Field | Rule |
|---|---|
| all fields except `fileName`, `correctionTimeout`, `approvalTimeout` | required |
| `applicantEmail` | valid email |
| `applicantCountry`, `beneficiaryCountry` | 2 capital letters (`DE`) |
| `currency` | 3 capital letters (`EUR`) |
| `amount` | number > 0 |
| `latestShipmentDate`, `expiryDate` | `YYYY-MM-DD` |
| `tradeDocuments` | at least 1; `documentType` ∈ `COMMERCIAL_INVOICE`, `BILL_OF_LADING`, `PACKING_LIST`, `CERTIFICATE_OF_ORIGIN`, `INSURANCE_CERTIFICATE` |
| `correctionTimeout`, `approvalTimeout` | optional, demo only, ISO-8601 duration (`PT2M`). Defaults: 48 h / 24 h |

Business rules (e.g. expiry date after shipment date) are checked **by the workflow**, not here:
an invalid application is accepted (201) and then goes to the *Correct application* task.

Response **201 Created** (`Location: /api/applications/LOC-APP-2026-0001`):
```json
{
  "applicationId": "LOC-APP-2026-1001",
  "status": "SUBMITTED",
  "customerId": "CUST-1001",
  "applicantName": "Rhein Machinery GmbH",
  "...": "all request fields echoed back",
  "processInstanceKey": "2251799814225001",
  "createdAt": "2026-11-14T10:00:00.123+05:30",
  "updatedAt": "2026-11-14T10:00:00.456+05:30"
}
```

### `GET /api/applications` — list (newest first)
Response **200**: array of the same object as above (without `processVariables`).

### `GET /api/applications/{applicationId}` — details
Response **200**: same object **plus** `processVariables` = the workflow's current variables.

Fields filled by the workflow (available **immediately**, no index delay):

| Field | When |
|---|---|
| `status` | updated each time the workflow enters a new step |
| `correctionReasons` | list of reasons while status is `CORRECTION_REQUIRED` (validation errors or document discrepancy) — show them on the correction form |
| `rejectionReason` | set when status is `REJECTED` |
| `locNumber` | set when status is `ISSUED` (e.g. `LC-2026-000001`) |

Example:
```json
{
  "applicationId": "LOC-APP-2026-0001",
  "status": "PENDING_APPROVAL",
  "...": "...",
  "rejectionReason": null,
  "locNumber": null,
  "processVariables": {
    "applicationValid": true,
    "customerRiskRating": "LOW",
    "complianceStatus": "CLEAR",
    "documentResults": [ { "documentType": "COMMERCIAL_INVOICE", "valid": true, "remarks": "OK" } ],
    "creditScore": 780,
    "riskLevel": "LOW",
    "creditApproved": true
  }
}
```
Values may lag about one second behind the engine. Response **404** if the id is unknown.

### `GET /api/applications/{applicationId}/history` — status timeline
Response **200** (oldest first; read from PostgreSQL, no delay):
```json
[
  { "status": "SUBMITTED",        "changedAt": "2026-11-14T10:00:00.120+05:30" },
  { "status": "VALIDATING",       "changedAt": "2026-11-14T10:00:00.310+05:30" },
  { "status": "COMPLIANCE_CHECK", "changedAt": "2026-11-14T10:00:00.900+05:30" }
]
```

Status values: `SUBMITTED`, `VALIDATING`, `CORRECTION_REQUIRED`, `COMPLIANCE_CHECK`,
`DOCUMENT_VERIFICATION`, `CREDIT_ASSESSMENT`, `PENDING_APPROVAL`, `ESCALATED`,
`DRAFT_GENERATED`, `ISSUED`, `REJECTED` (updated by the job workers from Step 6 on).

### `DELETE /api/applications/{applicationId}` — delete an application

- Workflow still running (e.g. `CORRECTION_REQUIRED`, `PENDING_APPROVAL`) → the Camunda process
  instance is **canceled** first (Operate shows it as canceled), then the application is deleted.
- Workflow already finished (`ISSUED`, `REJECTED`) → only the application is deleted; the finished
  instance stays in Operate as history.
- Deletes the application, its trade documents, correction reasons and status history.

| Response | When |
|---|---|
| `204 No Content` | deleted |
| `404 NOT_FOUND` | unknown application id |
| `503 WORKFLOW_UNAVAILABLE` | Camunda could not cancel the workflow — nothing was deleted |

---

## User tasks (task inbox)

### `GET /api/tasks?candidateGroup={group}&applicationId={id}` — open tasks
Both query parameters are optional. Groups: `trade-operations`, `credit-managers`, `senior-managers`.

> **Refresh the inbox.** A new task appears in this list 1–3 seconds after the workflow reaches it
> (Camunda updates its search index asynchronously). Poll every ~3 seconds or add a "Refresh" button;
> after completing a task, wait ~2 seconds before reloading.

Response **200**:
```json
[
  {
    "userTaskKey": "2251799814225123",
    "taskDefinitionId": "Task_ManagerApproval",
    "name": "Obtain manager approval",
    "candidateGroups": ["credit-managers"],
    "createdAt": "2026-11-14T10:01:00+05:30",
    "processInstanceKey": "2251799814225001",
    "applicationId": "LOC-APP-2026-0001",
    "applicantName": "Rhein Machinery GmbH",
    "amount": 250000,
    "currency": "EUR"
  }
]
```

Use `taskDefinitionId` to decide which form to show:

| `taskDefinitionId` | Form fields to send | Rules |
|---|---|---|
| `Task_CorrectApplication` | corrected application fields (same names as POST body) | all optional; show `correctionReason` / `validationErrors` from `processVariables` |
| `Task_ManagerApproval` | `approvalDecision`, `approverComments` | `approvalDecision` must be `"APPROVED"` or `"REJECTED"` |
| `Task_EscalatedApproval` | `approvalDecision`, `approverComments` | same as above |
| `Task_VerifyTerms` | `termsVerified`, `termsComments` | `termsVerified` must be JSON `true`/`false` (not a string) |

### `POST /api/tasks/{userTaskKey}/complete` — complete a task
Request:
```json
{ "variables": { "approvalDecision": "APPROVED", "approverComments": "Within limits" } }
```
Response **204 No Content**.
Errors: **400** wrong/missing variables, **404** unknown task, **409** task already completed
(also happens if the inbox was not refreshed after completing — just reload the list).

---

## Mock external service (used by Camunda, not by React)

### `POST /api/mock/credit-bureau/assessments`
Called by the REST connector in BPMN task *Perform credit assessment*.
```json
Request:  { "applicationId": "LOC-APP-2026-0001", "customerId": "CUST-1001", "amount": 250000, "currency": "EUR", "customerRiskRating": "LOW" }
Response: { "creditScore": 780, "riskLevel": "LOW", "creditApproved": true }
```
Scoring: LOW 780 / MEDIUM 690 / HIGH 590; −60 if amount > 1,000,000; −150 if > 5,000,000;
approved when score ≥ 620. Demo: amount 6,000,000 + MEDIUM rating → declined.

---

## Error format (all endpoints)

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Some fields are invalid",
  "fieldErrors": { "applicantEmail": "must be a well-formed email address" },
  "timestamp": "2026-11-14T10:00:00+05:30"
}
```

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | field rules broken (`fieldErrors` lists them) |
| 400 | `BAD_REQUEST` | malformed JSON, wrong date format, wrong task variables |
| 404 | `NOT_FOUND` | unknown application or task |
| 409 | `CONFLICT` | task is no longer open |
| 503 | `WORKFLOW_UNAVAILABLE` | Camunda is not running / rejected the command |
