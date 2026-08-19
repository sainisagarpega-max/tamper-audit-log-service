# Tamper-Evident Audit Log Service

This repository contains the implementation and supporting documentation for the AI-Assisted Software Engineering System - Audit Log Service assignment.

## Project status

Scenarios A, B, and C are implemented: append-only writes, filtered queries, versioned SHA-256 chaining, tamper verification, retention, redaction, export, and scoped client-account access compliance reporting.

## Planned technology stack

- Java 21
- Spring Boot 4.1.0
- Maven
- Spring Web and Spring Data JPA
- H2 for local development and automated tests
- PostgreSQL for production
- Spring Security with JWT bearer authentication
- OpenAPI 3 and Swagger UI for API documentation

## Documentation

- [ATTESTATION.md](ATTESTATION.md) - authorship and integrity attestation
- [AI_USAGE_LOG.md](AI_USAGE_LOG.md) - record of AI-assisted work
- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) - normalized requirements, ambiguities, assumptions, and acceptance criteria
- [docs/TASK_DECOMPOSITION.md](docs/TASK_DECOMPOSITION.md) - API plan, data model, dependencies, milestones, and quality gates
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - components, storage, data flow, API schemas, security, trade-offs, and risks
- [docs/SCENARIO_B.md](docs/SCENARIO_B.md) - retention, salted-placeholder redaction, export verification, trade-offs, and limitations
- [docs/SCENARIO_C.md](docs/SCENARIO_C.md) - clarified compliance requirement, assumptions, questions, scoped design, implementation, and limitations
- [docs/TESTING.md](docs/TESTING.md) - test strategy, requirement coverage, and tampering simulations

The confidential assignment PDF must not be committed or redistributed.

## Author

- Name: Saini Sagar
- Email: saini.sagarpega@gmail.com

## Project structure

```text
src/main/java/com/sainisagar/auditlog/
|-- config/       OpenAPI configuration
|-- controller/   REST endpoints
|-- dto/          API request and response models
|-- entity/       JPA entities
|-- repository/   Database access
|-- security/     JWT security configuration
`-- service/      Application and hash-chain logic
```

## Run locally

Prerequisites: Java 21 and Maven 3.6.3 or newer.

```bash
mvn spring-boot:run
```

The default `local` profile uses an in-memory H2 database. API documentation is available at `http://localhost:8080/swagger-ui.html`. The local profile provides a development-only token endpoint described below. Production remains an OAuth2 resource server and validates tokens from the configured external identity provider.

### H2 console

The H2 console is available only with the `local` profile at `http://localhost:8080/h2-console/`.

Use these connection values:

```text
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:mem:auditlog
User Name: sa
Password: (leave blank)
```

The configured datasource URL contains compatibility options, but the console can connect using the shorter URL above because it addresses the same named in-memory database. In IntelliJ, set the active profile to `local`, or run on Windows PowerShell with:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

## Local JWT token

With the application running under the default `local` profile, request a 30-minute token:

```bash
curl -X POST http://localhost:8080/api/v1/dev/token \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "saini-sagar",
    "roles": ["AUDIT_WRITER", "AUDIT_READER", "AUDIT_ADMIN", "AUDIT_COMPLIANCE"]
  }'
```

Copy `accessToken` from the response. In Swagger UI, select **Authorize** and paste the token. For a command-line request:

```bash
curl http://localhost:8080/api/v1/audit-events/verify \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Allowed local roles are `AUDIT_WRITER`, `AUDIT_READER`, `AUDIT_ADMIN`, and `AUDIT_COMPLIANCE`. Local tokens contain `aud: ["audit-log-api"]`. The RSA signing key is generated in memory at startup, so local tokens become invalid when the application restarts. The token endpoint and signing key beans are restricted to the `local` Spring profile and are absent in production.

## Production database

Use the `prod` profile and provide the PostgreSQL connection values:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://localhost:5432/auditlog
DB_USERNAME=auditlog
DB_PASSWORD=change-me
JWT_ISSUER_URI=https://identity.example.com
JWT_JWK_SET_URI=https://identity.example.com/.well-known/jwks.json
JWT_AUDIENCE=audit-log-api
```

Swagger is disabled in production unless `SWAGGER_ENABLED=true`.

## Scenario A API

| Method | Endpoint | Purpose | Required JWT role |
| --- | --- | --- | --- |
| `POST` | `/api/v1/audit-events` | Append an immutable audit event | `AUDIT_WRITER` |
| `GET` | `/api/v1/audit-events` | Query using actor, resource, event type, time, and pagination filters | `AUDIT_READER` or `AUDIT_ADMIN` |
| `GET` | `/api/v1/audit-events/verify` | Verify the complete hash chain | `AUDIT_READER` or `AUDIT_ADMIN` |
| `POST` | `/api/v1/retention/archive` | Archive events outside the retention window | `AUDIT_ADMIN` |
| `POST` | `/api/v1/audit-events/{id}/redactions` | Redact a payload field and anchor its receipt | `AUDIT_ADMIN` |
| `GET` | `/api/v1/audit-events/export` | Export by actor or resource with chain metadata | `AUDIT_READER` or `AUDIT_ADMIN` |
| `POST` | `/api/v1/audit-events/export/verify` | Verify bundle hash, chain metadata, and exported event hashes | `AUDIT_READER` or `AUDIT_ADMIN` |
| `POST` | `/api/v1/compliance/account-access` | Record an allowed or denied client-account access attempt | `AUDIT_WRITER` |
| `GET` | `/api/v1/compliance/account-access` | Query the client-account access compliance report | `AUDIT_COMPLIANCE` or `AUDIT_ADMIN` |
| `POST` | `/api/v1/dev/token` | Generate a short-lived JWT for local development only | Public, local profile only |

JWT roles are read from the token's `roles` claim. Example:

```json
{
  "sub": "audit-user",
  "roles": ["AUDIT_WRITER", "AUDIT_READER"]
}
```

Write request example:

```json
{
  "eventType": "CLIENT_ACCOUNT_READ",
  "actorId": "user-123",
  "resourceType": "ACCOUNT",
  "resourceId": "account-456",
  "payload": {
    "outcome": "ALLOWED",
    "channel": "WEB"
  },
  "occurredAt": "2026-08-18T10:00:00Z"
}
```

Query parameters are `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, `page`, and `size`. Time filters apply to the server-assigned `recordedAt` value. Results are ordered by ascending chain sequence.

The verification response reports whether the chain is intact and identifies the first broken sequence and violation type when tampering is detected. Verification also compares the final stored event with the persisted chain head, so deletion of the last event or the complete event table produces `CHAIN_HEAD_MISMATCH`.

## Tests

```bash
mvn test
```

The current suite covers application startup, local H2 console security, local RS256 token issuance and validation, generated Swagger/OpenAPI bearer configuration, JWT role-claim conversion, authenticated API behavior, validation and authorization, Flyway schema validation, linked and concurrent writes, canonical hashing, chain-head deletion detection, filters, pagination, multiple tampering patterns, retention and redaction edge cases, export generation and verification, client-account access recording, compliance reporting, and report self-auditing.

PostgreSQL tests use Testcontainers. Start Docker Desktop before `mvn test` to execute them. Without Docker, JUnit reports the two PostgreSQL tests as skipped while all H2 tests continue to run.
