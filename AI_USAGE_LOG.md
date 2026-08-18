# AI Usage Log

This document records AI assistance used during the Tamper-Evident Audit Log Service assignment. Entries should be added whenever an AI tool materially assists with planning, code, tests, documentation, debugging, or review.

| Date | Tool | Purpose | Files or areas affected | Review and validation |
| --- | --- | --- | --- | --- |
| 18 August 2026 | OpenAI Codex | Inspected the initial project structure and drafted the repository baseline documentation. | `README.md`, `ATTESTATION.md`, `AI_USAGE_LOG.md` | Saini Sagar must review the documents for accuracy and confirm that they satisfy the assignment requirements before committing. |
| 18 August 2026 | OpenAI Codex | Extracted and analyzed the assignment; normalized its scope, identified ambiguities, proposed assumptions and acceptance criteria, and checked the requested Java/Spring stack. | `README.md`, `ATTESTATION.md`, `AI_USAGE_LOG.md`, `docs/REQUIREMENTS.md` | Saini Sagar must approve the requirements before implementation. Assumptions may be revised during development and should remain traceable. |
| 18 August 2026 | OpenAI Codex | Created a minimal Java 21 and Spring Boot 4.1.0 Maven structure with controller, DTO, entity, repository, service, security, H2/PostgreSQL profiles, JWT validation, Swagger/OpenAPI, and a context test. | `pom.xml`, `src/`, `README.md`, `.gitignore` | Saini Sagar must review the package structure and security assumptions. Maven tests are used as the initial quality gate. |
| 18 August 2026 | OpenAI Codex | Decomposed the assignment into API contracts, data-model changes, dependencies, ordered milestones, acceptance criteria, and quality gates. | `docs/TASK_DECOMPOSITION.md`, `README.md`, `AI_USAGE_LOG.md` | Saini Sagar should review the proposed global chain and database-backed chain-head approach before implementation continues. |

## Entry guidelines

For each use of an AI tool, record:

1. The date and tool used.
2. The task or prompt's purpose without including secrets or sensitive data.
3. The files, code, or design areas affected.
4. How the generated output was reviewed, tested, or changed.
