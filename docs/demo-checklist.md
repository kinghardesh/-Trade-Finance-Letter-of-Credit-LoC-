# Demo Checklist — Trade Finance LoC Issuance

> Educational project **inspired by** Commerzbank trade finance. No real bank system is connected:
> customer registry, sanctions list, AML engine and credit bureau are **mocks** inside `loc-backend`.

Use this on demo day, top to bottom. Each box should be ticked before you move on.

---

## 1. The day before

- [ ] `git pull` / `git push` — GitHub has the latest commit
- [ ] Full test run passes (see Java setup in section 2): `mvnw.cmd test` → `BUILD SUCCESS`
- [ ] Run every scenario in [`demo-scenarios.md`](demo-scenarios.md) once
- [ ] Screenshots saved for the report (section 6)
- [ ] Teammate's React app runs against `http://localhost:8081`
- [ ] PC set to **not sleep** (Camunda 8 Run stops when the PC sleeps)

---

## 2. Start-up (in this order)

Open a **CMD** window for each step.

**1. Camunda 8 Run**
```bat
cd C:\Users\hp\Downloads\c8run-8.8.32
.\c8run.exe start
```
- [ ] http://localhost:8080/operate opens (login `demo` / `demo`)

**2. Docker (PostgreSQL + Mailpit)** — Docker Desktop must be running
```bat
cd C:\projects\loc-issuance
docker compose -f docker/docker-compose.yml up -d
```
- [ ] `docker ps` shows `loc-postgres` (healthy) and `mailpit`
- [ ] http://localhost:8025 opens (Mailpit)

**3. Backend** — Java 21 must be set in **this** window first
```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
cd C:\projects\loc-issuance\loc-backend
mvnw.cmd spring-boot:run
```
- [ ] `java -version` says **21**
- [ ] Log shows `Started LocBackendApplication`
- [ ] http://localhost:8081/api/ping → `"status": "UP"`
- [ ] Operate → Processes shows **Letter of Credit Issuance** and **Compliance Screening**

**4. Frontend (teammate)**
```bat
cd <frontend folder>
npm run dev
```
- [ ] http://localhost:5173 opens and shows the application list

---

## 3. Browser tabs to keep open

| Tab | URL |
|---|---|
| React app | http://localhost:5173 |
| Operate | http://localhost:8080/operate |
| Tasklist | http://localhost:8080/tasklist |
| Mailpit | http://localhost:8025 |
| Camunda Modeler | `loc-backend/src/main/resources/bpmn/loc-issuance.bpmn` |

---

## 4. Demo script (≈ 15 minutes)

Use the standard request from [`api-contract.md`](api-contract.md). Tip: submit the timeout scenarios **first** so the 30-second timers have fired by the time you show them.

| # | Show | Input | What to point at | Done |
|---|---|---|---|---|
| 0 | The model | — | Modeler: main process, call activity, embedded subprocess, boundary events, connectors | [ ] |
| 1 | Happy path | standard request | Operate: token path; Tasklist: *Manager approval* → APPROVED, *Verify LoC terms* → true; Mailpit: issue email with LoC number `LC-2026-…` | [ ] |
| 2 | Correction | `expiryDate: 2026-12-20` | Tasklist: *Correct application* form shows the validation error → fix date → re-validated | [ ] |
| 3 | Sanctions hit | `beneficiaryName: Blocked Trading Co` | Operate: called compliance instance, **parallel** MI (3 parties), error boundary `SANCTIONS_HIT` | [ ] |
| 4 | Credit declined | `amount: 6000000` | REST connector to mock credit bureau → `REJECTED`; rejection email in Mailpit | [ ] |
| 5 | Escalation | `approvalTimeout: PT30S`, don't approve | Timer boundary → *Escalated approval (senior manager)* | [ ] |
| 6 | Document discrepancy | a document with reference `BL-DISCREPANCY-1` | **Sequential** MI in subprocess, error `DOCUMENT_DISCREPANCY` → correction | [ ] |
| 7 | Correction timeout | as 6 + `correctionTimeout: PT30S`, don't correct | Timer boundary → `REJECTED` | [ ] |
| 8 | Status history | any application | `GET /api/applications/{id}/history` — written by the `update-status` execution listeners | [ ] |
| 9 | Persistence | — | Stop and restart the backend → applications still listed (PostgreSQL) | [ ] |

---

## 5. Talking points (likely viva questions)

- **Why Camunda 8 / Zeebe?** Job workers pull work over gRPC; the engine never calls our code directly.
- **Where is the data?** Business data in PostgreSQL (Flyway schema); workflow state in Camunda.
- **How does the DB stay in sync?** `update-status` start listeners + `sync-corrections` end listener — works from React *and* Tasklist.
- **Why does the UI poll?** Camunda's search index lags 1–3 s behind the engine; a stale task completion returns 409.
- **What is mocked?** Customer registry, sanctions list, AML engine, credit bureau (REST connector to our own mock endpoint), email (Mailpit).
- **FEEL examples:** gateway conditions, `parties` input mapping for the call activity, AML output mapping to `complianceStatus`, timer durations.

---

## 6. Screenshots for the report

- [ ] Main BPMN (`loc-issuance.bpmn`) in Modeler
- [ ] Compliance BPMN (`loc-compliance-screening.bpmn`)
- [ ] Operate: completed instance with the call activity
- [ ] Operate: parallel multi-instance (3 sanctions checks)
- [ ] Tasklist: *Correct application* form with validation errors
- [ ] Tasklist: *Manager approval* form
- [ ] Mailpit: LoC issued email
- [ ] React app: application list and detail with status history

---

## 7. If something goes wrong

| Problem | Fix |
|---|---|
| `Port 8081 was already in use` | An old backend is still running: `netstat -ano \| findstr :8081` → `taskkill /PID <pid> /F` |
| Backend fails, Mockito / class version errors | Wrong Java — run the two `set` lines from section 2 again |
| Operate empty / connection refused on 26500 | Camunda stopped (PC slept) → `.\c8run.exe start` |
| `Connection refused` to database | Docker Desktop not running → start it, then `docker compose ... up -d` |
| Task list empty right after submit | Wait 2–3 s and refresh (search index lag) |
| No email in Mailpit | `docker ps` — is `mailpit` running? |
| Incident in Operate | Click it to read the error, fix the cause, then **Retry** |

---

## 8. After the demo

- [ ] Stop the backend (`Ctrl + C`)
- [ ] `docker compose -f docker/docker-compose.yml down` (data is kept in the volume)
- [ ] `.\c8run.exe stop`
