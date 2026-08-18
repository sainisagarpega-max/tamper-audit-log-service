# Tamper-Evident Audit Log Service

This repository contains the implementation and supporting documentation for the AI-Assisted Software Engineering System - Audit Log Service assignment.

## Project status

Requirements analysis is complete. Application implementation is the next phase.

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

The default `local` profile uses an in-memory H2 database. API documentation is available at `http://localhost:8080/swagger-ui.html`. Protected API calls require a JWT issued by the configured `JWT_ISSUER_URI`; its public keys are loaded from `JWT_JWK_SET_URI`.

## Production database

Use the `prod` profile and provide the PostgreSQL connection values:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://localhost:5432/auditlog
DB_USERNAME=auditlog
DB_PASSWORD=change-me
JWT_ISSUER_URI=https://identity.example.com
JWT_JWK_SET_URI=https://identity.example.com/.well-known/jwks.json
```

Swagger is disabled in production unless `SWAGGER_ENABLED=true`.
