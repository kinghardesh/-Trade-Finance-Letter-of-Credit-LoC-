# Trade Finance Letter of Credit Issuance — Workflow Automation

Educational college project (course: Workflow Automation), inspired by Commerzbank's
trade finance services. It is **not** affiliated with, built for, or integrated into
any real banking system. Sanctions, AML and banking integrations are mocked.

| Folder         | Owner    | Tech                                   |
|----------------|----------|----------------------------------------|
| `loc-backend`  | Member 1 | Java 21, Spring Boot 3.5, Camunda 8.8  |
| `loc-frontend` | Member 2 | React + Vite                           |
| `docker`       | shared   | Docker Compose (PostgreSQL, ...)       |
| `docs`         | shared   | API contract, process variables        |

BPMN / DMN / form files live in `loc-backend/src/main/resources/bpmn`.

## Run locally

1. Start Camunda 8 Run: `.\c8run.exe start` (Operate: http://localhost:8080/operate, demo/demo)
2. Start the backend: `cd loc-backend` then `.\mvnw.cmd spring-boot:run`
3. Check: http://localhost:8081/api/ping
