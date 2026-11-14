# Demo Scenarios — which input triggers which path

All external systems are **mocked** in `loc-backend` (package `com.tradefinance.loc.mock`).
Start from the standard request in [`api-contract.md`](api-contract.md) and change **one** field:

| # | Change to the standard request | Path through the BPMN | Final status |
|---|---|---|---|
| 1 | *(nothing)* | all checks pass → manager approves → terms OK → issued → email | `ISSUED` |
| 2 | `"expiryDate": "2026-12-20"` | validation fails → **Correct application** (fix the date) → continues | depends on approval |
| 3 | `"customerId": "CUST-9001"` | customer not eligible (KYC expired) | `REJECTED` |
| 4 | `"beneficiaryName": "Blocked Trading Co"` | parallel sanctions screening → **error SANCTIONS_HIT** → error boundary on call activity | `REJECTED` |
| 4b | `"beneficiaryCountry": "KP"` | same as 4 (embargoed country) | `REJECTED` |
| 5 | `"goodsDescription": "Military vehicles"` | AML score ≥ 70 → compliance not clear | `REJECTED` |
| 6 | `"amount": 6000000` | rating HIGH → REST connector → credit bureau declines | `REJECTED` |
| 7 | `"approvalTimeout": "PT30S"` (don't approve) | **timer boundary** → Escalated approval (senior manager) | depends on approval |
| 8 | a document with `"documentReference": "BL-DISCREPANCY-1"` | sequential document checks → **error DOCUMENT_DISCREPANCY** → Correct application | `CORRECTION_REQUIRED` |
| 8b | as 8 plus `"correctionTimeout": "PT30S"` (don't correct) | **timer boundary** on Correct application → rejected | `REJECTED` |
| 9 | at *Verify LoC terms* send `termsVerified: false` | draft regenerated (`draftVersion` 2) → verify again | depends |
| 10 | at *Manager approval* send `approvalDecision: "REJECTED"` | rejected with the manager's comment | `REJECTED` |

Rejection and issue emails can be seen at **http://localhost:8025** (Mailpit).

## Mock rules (for the report)

| Mock | Rule |
|---|---|
| Business validation | latest shipment not in the past; expiry ≥ shipment + 21 days; applicant ≠ beneficiary country; amount ≤ 50,000,000; invoice + bill of lading required |
| Customer registry | `CUST-9…` → not eligible; risk rating: < 1M LOW, ≤ 5M MEDIUM, > 5M HIGH |
| Sanctions list | name contains BLOCKED / SANCTIONED, or country KP / IR / SY → match |
| AML engine | 10 base, +20 > 1M, +15 > 5M, +15 HIGH rating, +60 arms/weapon/ammunition/military goods; ≥ 70 → rejected |
| Trade documents | reference containing DISCREPANCY (or empty) → invalid |
| Credit bureau (REST) | LOW 780 / MEDIUM 690 / HIGH 590; −60 > 1M; −150 > 5M; approved ≥ 620 |
