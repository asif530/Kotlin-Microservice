# MiniMart

A minimal Kotlin/Spring Boot microservices order-placement platform — `identity-service`, `catalog-service`, `order-service`, `notification-service`, each with its own database and no shared schema. This README indexes the project's design and decision documents under `Archive/`.

## Design documents

- **[Architecture](Archive/Architecture/ARCHITECTURE.md)** — service inventory, gRPC/RabbitMQ contracts, Kong/Consul, the observability stack, multi-module build layout.
  - [Discussion](Archive/Architecture/Discussion.md) — the Q&A that preceded the architecture doc (microservices vs. multi-module, etc.)
  - [Change log 1](Archive/Architecture/Change-log-1) — post-lock amendment (added the `ReleaseStock` RPC catalog.proto was missing, for ORD-012)
- **[Business Rules](Archive/BusinessRules/BUSINESS_RULES.md)** — locked v1.0 business rules for all four services, plus what's explicitly out of scope.
- **[Database Design](Archive/Development/Database)** — per-service schema (SQL + NoSQL), indexing, replication, and the cross-service data-duplication register.
  - ER diagrams (published Artifacts): 
  - [identity-service](https://claude.ai/code/artifact/ff98646a-154c-4883-ab9a-d269fc582de1) · 
  - [catalog-service](https://claude.ai/code/artifact/ec4598df-2f39-462d-a2cc-cc61b2b49d05) · 
  - [order-service](https://claude.ai/code/artifact/d19b956a-dbdc-4526-a69f-9d5ff1f553d2) · 
  - [notification-service](https://claude.ai/code/artifact/a30c4309-a613-4396-9415-da5745ef1b15) · 
  - [combined](https://claude.ai/code/artifact/fa9d3012-d725-4747-928d-c301214d7cc8)
- **[Database proof scripts](Archive/Development/Database-Scripts/Sequence)** — runnable SQL/JS that creates the real schema and seeds business-scenario dummy data (register, place an order, cancel, insufficient stock, constraint guardrails) directly in the postgres/mongo containers. Not part of the services themselves — a proof the design in the Database Design doc above actually works.
- **[Kickstart Q&A](Archive/Development/Kickstart)** — Gradle version-catalog questions from initial project scaffolding.

## Planning & background

- [Plan](Archive/Plan/plan.md) — capabilities implied by the current `build.gradle.kts`.
- [Project ideas](Archive/Misc/project-ideas.md) — the stack-fit exercise that led to the MiniMart e-commerce domain.

## Issues encountered & resolved

Bugs found and fixed while building this project, filed by technical topic rather than by task:

- [Docker](Archive/Issues/Docker)
- [Shell](Archive/Issues/Shell)
- [Library](Archive/Issues/Library)

## Prompt log

The prompts that drove each stage of this project, kept for traceability between a decision and the instruction that produced it.

- [Sequence](Archive/Prompts/Sequence) — index of stages executed so far
- [Architecture prompt](Archive/Prompts/Architecture)
- [Business prompt](Archive/Prompts/Business)
- [Database-Development prompt](Archive/Prompts/Database-Development)
