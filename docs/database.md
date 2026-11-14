# Database — PostgreSQL

**Proposal by Member 1 (backend) for review by Member 2 (database design).**
The schema lives in `loc-backend/src/main/resources/db/migration/` and is applied
automatically by **Flyway** when the backend starts.

## Connection (local development only)

| Setting | Value |
|---|---|
| Host / port | `localhost:5440` (5432–5434 are used by other projects on this PC) |
| Database | `loc_issuance` |
| User / password | `loc_app` / `loc_app` |
| Container | `loc-postgres` (`docker/docker-compose.yml`), data in volume `loc-postgres-data` |
| psql shell | `docker exec -it loc-postgres psql -U loc_app -d loc_issuance` |

## Tables

```
loc_application 1 ──< trade_document                 (ordered list of documents)
                1 ──< application_correction_reason  (why correction is needed)
                1 ──< application_status_history     (audit trail / timeline)
```

### `loc_application`
| Column | Type | Notes |
|---|---|---|
| `application_id` | varchar(30) **PK** | `LOC-APP-2026-1001` (from sequence `loc_application_seq`) |
| `customer_id` | varchar(30) | indexed |
| `applicant_name`, `applicant_email`, `applicant_country` | varchar | country = ISO alpha-2 |
| `beneficiary_name`, `beneficiary_country`, `beneficiary_bank_name` | varchar | |
| `amount` | numeric(18,2) | |
| `currency` | varchar(3) | ISO 4217 |
| `goods_description` | varchar(1000) | |
| `latest_shipment_date`, `expiry_date` | date | |
| `status` | varchar(30) | see `process-variables.md` §3; indexed |
| `process_instance_key` | bigint, unique | link to the Camunda process instance |
| `rejection_reason` | varchar(1000) | set when `REJECTED` |
| `loc_number` | varchar(30), unique | `LC-2026-000001` (sequence `loc_number_seq`), set when `ISSUED` |
| `created_at`, `updated_at` | timestamptz | |

### `trade_document`
PK (`application_id`, `sort_order`) — `document_type`, `document_reference`, `file_name`.

### `application_correction_reason`
PK (`application_id`, `sort_order`) — `reason`.

### `application_status_history`
`id` (identity PK), `application_id`, `status`, `changed_at` — one row per status change.
Exposed as `GET /api/applications/{id}/history`.

## What lives where

| Data | Stored in |
|---|---|
| Application form data, status, LoC number, rejection reason, history | **PostgreSQL** (this schema) |
| Workflow state, process variables, user tasks, incidents | **Camunda** (its own Elasticsearch storage) |

The link between the two is `loc_application.process_instance_key`.

## How to change the schema

1. **Never edit `V1__initial_schema.sql`** after it has run — Flyway stores a checksum and the backend
   will refuse to start.
2. Add a new file, e.g. `V2__add_incoterms.sql`:
   ```sql
   alter table loc_application add column incoterms varchar(3);
   ```
3. Add the matching field to `LocApplication.java` (Hibernate runs in `validate` mode and stops the
   app if entity and table do not match).
4. Restart the backend → Flyway applies V2 automatically.

To wipe all local data and start from an empty database:
`docker compose -f docker/docker-compose.yml down -v` then `up -d` (deletes the volume).
