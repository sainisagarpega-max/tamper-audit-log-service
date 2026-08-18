# AI Usage Log

This document records AI assistance used during the Tamper-Evident Audit Log Service assignment. Entries should be added whenever an AI tool materially assists with planning, code, tests, documentation, debugging, or review.

| Date | Tool | Purpose | Files or areas affected | Review and validation |
| --- | --- | --- | --- | --- |
| 18 August 2026 | OpenAI Codex | Inspected the initial project structure and drafted the repository baseline documentation. | `README.md`, `ATTESTATION.md`, `AI_USAGE_LOG.md` | Saini Sagar must review the documents for accuracy and confirm that they satisfy the assignment requirements before committing. |
| 18 August 2026 | OpenAI Codex | Extracted and analyzed the assignment; normalized its scope, identified ambiguities, proposed assumptions and acceptance criteria, and checked the requested Java/Spring stack. | `README.md`, `ATTESTATION.md`, `AI_USAGE_LOG.md`, `docs/REQUIREMENTS.md` | Saini Sagar must approve the requirements before implementation. Assumptions may be revised during development and should remain traceable. |
| 18 August 2026 | OpenAI Codex | Created a minimal Java 21 and Spring Boot 4.1.0 Maven structure with controller, DTO, entity, repository, service, security, H2/PostgreSQL profiles, JWT validation, Swagger/OpenAPI, and a context test. | `pom.xml`, `src/`, `README.md`, `.gitignore` | Saini Sagar must review the package structure and security assumptions. Maven tests are used as the initial quality gate. |
| 18 August 2026 | OpenAI Codex | Decomposed the assignment into API contracts, data-model changes, dependencies, ordered milestones, acceptance criteria, and quality gates. | `docs/TASK_DECOMPOSITION.md`, `README.md`, `AI_USAGE_LOG.md` | Saini Sagar should review the proposed global chain and database-backed chain-head approach before implementation continues. |
| 18 August 2026 | OpenAI Codex | Drafted the Scenario A architecture covering SQL storage, service components, data flows, SHA-256 canonical hashing, API schemas, JWT authorization, trade-offs, risks, and mitigations. | `docs/ARCHITECTURE.md`, `README.md`, `AI_USAGE_LOG.md` | Saini Sagar must review and be able to defend the PostgreSQL, global-chain, canonicalization, and JWT resource-server decisions. |
| 18 August 2026 | OpenAI Codex | Implemented Scenario A: Flyway schema, database-locked chain state, append API, composable query filters and pagination, versioned canonical SHA-256 hashing, verification endpoint, role-based JWT authorization, problem-detail errors, and integration tests. | `pom.xml`, `src/main/`, `src/test/`, `README.md`, `AI_USAGE_LOG.md` | The test suite exposed and corrected a timestamp-precision hash mismatch by normalizing timestamps to database-safe microseconds before hashing. Final result: 5 tests passed. Saini Sagar must review and explain the chain transaction, canonical format, and security model. |
| 18 August 2026 | OpenAI Codex | Implemented Scenario B with scheduled/manual soft archival, archived-query control, salted-placeholder structured redaction, chain-anchored redaction receipts, redaction-aware verification, actor/resource export bundles, chain metadata, bundle hashing, documentation, and integration tests. | `src/main/`, `src/test/`, `docs/SCENARIO_B.md`, `README.md`, `AI_USAGE_LOG.md` | A failed retention test revealed that direct service construction bypassed Spring transactions; the test was corrected to use the managed service. Final suite: 8 tests passed. Saini Sagar must review the salted-hash guessing risk, backup-erasure limitation, unsigned export limitation, and soft-archive storage trade-off. |
| 18 August 2026 | OpenAI Codex | Clarified and implemented Scenario C as application-mediated client-account access evidence with allowed/denied outcomes, bounded evidence fields, account/actor/time reporting, compliance JWT authorization, self-auditing report access, assumptions, questions, risks, and explicit scope boundaries. | `src/main/`, `src/test/`, `docs/SCENARIO_C.md`, `README.md`, `AI_USAGE_LOG.md` | The scoped implementation deliberately excludes direct database, UI, cache, third-party, and organization-wide access coverage. Final suite: 9 tests passed. Saini Sagar must be able to explain why the prototype does not claim regulatory completeness. |

## Entry guidelines

For each use of an AI tool, record:

1. The date and tool used.
2. The task or prompt's purpose without including secrets or sensitive data.
3. The files, code, or design areas affected.
4. How the generated output was reviewed, tested, or changed.
