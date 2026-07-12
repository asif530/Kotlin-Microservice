# MiniMart — Microservices Architecture Document

**Status:** Proposed target architecture (no code changes yet)
**Author role:** Project architect (this document)
**Date:** 2026-07-12
**Deployment target for this document:** Docker Compose, single host, local/dev environment . 
Kubernetes is a deliberately deferred concern; §12 states exactly what changes if/when this moves to K8s.

This document is deliberately conservative: every technology claim below was either read from this repository's existing files 
or checked against current (July 2026) vendor documentation in this session. 
Where I could not verify a fact (e.g., an exact library version), I say so explicitly instead of inventing a number. 
Sources are listed at the end of each section that relies on external verification.

---

## 1. Domain: "MiniMart" — a minimal order-placement platform

**Decision:** A scaled-down e-commerce ordering flow: a buyer registers, browses a product catalog, and places an order.

**Why this domain and not something else:**
- It's the domain already reasoned about in this repo (`project-ideas.md` independently arrived at e-commerce as the top recommendation for this exact tech stack), 
so it's not an arbitrary pick — it's continuous with prior thinking here.
- It naturally produces **services with different data shapes**, which is the whole point of exercising both Postgres and MongoDB: 
user accounts and orders are rigidly structured (relational), while product attributes vary by category (documents fit better than a rigid schema).
- It naturally produces **both sync and async cross-service calls**: placing an order *must* synchronously know the buyer is real and the product is in stock (gRPC), 
but *notifying* someone about it doesn't need to block the checkout response (RabbitMQ).
- It's small enough to hold in your head: 4 services, not 12. I explicitly did **not** split out a Cart service, a Payment service, or a Shipping service — 
see the "**what I left out**" callout below.

**What I left out, and why:**
| Left out | Reasoning |
|---|---|
| Payment service | Real payment integration (Stripe/etc.) or PCI-scope handling is a distraction from the infrastructure goals of this exercise. Order status includes a `PLACED` state that stands in for "payment accepted" without implementing a payment processor. Flagged as future work in §13. |
| Cart service | A cart is just a client-side or Redis-backed draft order. Making it a 5th service with its own DB would be a service *for the sake of having a service*, not because it has a distinct bounded context or scaling profile. It lives as ephemeral state in `order-service`'s Redis usage instead. |
| Shipping/fulfillment service | No physical fulfillment logic exists yet to justify a bounded context — would be speculative. |

---

## 2. Service inventory

| Service                  | Owns data in                                        | Exposes (external, via Kong)                                                  | Exposes (internal)                | Consumes                                                               |
|--------------------------|-----------------------------------------------------|-------------------------------------------------------------------------------|-----------------------------------|------------------------------------------------------------------------|
| **identity-service**     | PostgreSQL (`identity` db)                          | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/me` (REST) | gRPC `GetUser`                    | —                                                                      |
| **catalog-service**      | MongoDB (`catalog` db) + Redis (cache)              | `GET /api/products`, `GET /api/products/{id}`, `POST /api/products` (REST)    | gRPC `GetProduct`, `ReserveStock` | —                                                                      |
| **order-service**        | PostgreSQL (`orders` db) + Redis (idempotency keys) | `POST /api/orders`, `GET /api/orders/{id}`, `GET /api/orders` (REST)          | —                                 | gRPC → identity-service, gRPC → catalog-service; publishes to RabbitMQ |
| **notification-service** | MongoDB (`notifications` db)                        | `GET /api/notifications` (REST, own history only)                             | —                                 | consumes RabbitMQ `order.placed`                                       |

Each service is a **separate Gradle module and a separate deployable/container** — no shared JAR of domain code, no shared database. The only shared build artifacts are generated gRPC stub modules (see §8), which are contracts, not logic.

**Database-per-service** is the load-bearing rule here: 
no service is ever allowed to query another service's Postgres schema or Mongo collection directly. 
All cross-service data access is either a gRPC call (synchronous, strongly-typed) or a RabbitMQ event (asynchronous). 
This is what actually makes these "microservices" rather than "one app split across processes that all read the same tables."

---

## 3. Language & framework: Kotlin + Spring Boot

**Decision:** Every service is Kotlin on Spring Boot, using coroutines where calls are naturally async (gRPC clients, RabbitMQ listeners), 
and Kotlin idioms (data classes for DTOs, null-safety on nullable JPA/Mongo fields, extension functions for mapping) — not Java code that happens to compile as Kotlin.

**Why Spring Boot over a lighter framework (e.g., Ktor):**
- This repo already has real, working investment in the Spring ecosystem for the exact observability stack this task requires: 
`spring-boot-starter-actuator`, 
`spring-boot-starter-opentelemetry`, 
`micrometer-registry-prometheus`, 
`micrometer-registry-otlp`, and 
`datasource-micrometer` 
are already declared in `build.gradle.kts` and wired to a running Prometheus/Grafana/Jaeger stack in `docker-compose.yml`. 
Rebuilding that on Ktor means re-doing already-solved integration work for no functional gain.
- Spring Data JPA and Spring Data MongoDB give database-per-service CRUD "for free" with minimal boilerplate, which matters across 4 services rather than 1.
- Spring's Kotlin support is first-class (`spring-boot-starter` provides Kotlin extensions, `kotlinx-coroutines-reactor` interop is standard), 
so choosing Spring Boot does not mean writing Java-in-Kotlin.

**What this decision does *not* settle:** 
whether the existing single Java module in this repo becomes one of these four services or is discarded. 
Per your earlier answer, that's an explicit next step, not decided by this document (see §14).
=> discard and start fresh.

---

## 4. Inter-service synchronous communication: gRPC

**Decision:** `order-service` calls `identity-service.GetUser` and `catalog-service.GetProduct` / `ReserveStock` synchronously via gRPC when a checkout request comes in. 
It needs the answer *before* it can decide whether the order is valid, so this cannot be async.

**Why gRPC instead of internal REST/JSON:**
- Contract-first: a `.proto` file is a single source of truth for the request/response shape, shared via a generated-stub module (§8), 
so `order-service` and `catalog-service` cannot silently drift on field names/types the way two REST services with hand-written DTOs can.
- Binary protobuf + HTTP/2 multiplexing is materially cheaper than JSON-over-HTTP/1.1 for the volume of small, frequent internal calls this checkout path makes 
(one order can mean N `GetProduct` calls, one per line item).
- This was an explicit requirement ("they will communicate with grpc"), read as service-to-service communication — not client-to-gateway. 
External clients still get REST (see §6), because Kong's plugin ecosystem (auth, rate limiting, CORS, transformations — see §7) is built around HTTP/JSON, 
and because browsers/most HTTP clients can't natively speak gRPC without gRPC-Web, which adds complexity with no benefit for this MVP.

**Illustrative contracts** (shape only, not final — actual `.proto` files are implementation work):

```protobuf
// identity.proto
rpc GetUser(GetUserRequest) returns (UserResponse);
message GetUserRequest { string user_id = 1; }
message UserResponse { string user_id = 1; string email = 2; bool active = 3; }

// catalog.proto
rpc GetProduct(GetProductRequest) returns (ProductResponse);
rpc ReserveStock(ReserveStockRequest) returns (ReserveStockResponse);
message ProductResponse { string product_id = 1; string name = 2; double price = 3; int32 stock_available = 4; }
message ReserveStockRequest { string product_id = 1; int32 quantity = 2; }
message ReserveStockResponse { bool success = 1; string message = 2; }
```

**Libraries:** official 
`io.grpc:grpc-kotlin-stub` + `io.grpc:grpc-netty` (or `grpc-netty-shaded`) + the `com.google.protobuf` 
Gradle plugin, with the gRPC `Server` started/stopped from a Spring `SmartLifecycle` bean. 

I'm deliberately **not** recommending a third-party Spring-gRPC starter library here, 
because I could not verify the current maintenance status/Spring Boot 4 compatibility of any specific one in this session, 
and wiring the official grpc-java/grpc-kotlin libraries directly into Spring's lifecycle is a well-understood, 
low-risk ~20-line integration that doesn't add an unverified dependency. 
Verified: `io.grpc` latest published version is `1.82.1` on Maven Central as of this session; pin the exact version at implementation time.

**Consistency model:** 
`ReserveStock` performs an atomic decrement in `catalog-service`'s own database and returns success/failure — it is the authority on stock, 
not `order-service`. This keeps the "owns its data" rule intact even for a cross-service write.

Sources: [io.grpc Maven Repository](https://mvnrepository.com/artifact/io.grpc), [grpc-kotlin GitHub](https://github.com/grpc/grpc-kotlin)

---

## 5. Inter-service asynchronous communication: RabbitMQ

**Decision:** After `order-service` commits an order in Postgres, it publishes an `order.placed` event to a topic exchange. `notification-service` consumes it to record/simulate a confirmation notification.

**Why this is async and not another gRPC call:**
- The checkout response does not need to wait on a notification being sent — coupling the two synchronously would make checkout latency and availability depend on a system (notifications) that has nothing to do with whether the order is valid.
- This is a genuine "fire domain event, let other bounded contexts react" case — the textbook use for a message broker, and the one place in this domain where eventual consistency is actually the right consistency model, not just a performance shortcut.

**Design:**
- Exchange: `order.events` (topic), routing key `order.placed`.
- Queue: `notification.order-placed`, bound to that routing key, with manual ack and a dead-letter exchange for messages that fail processing repeatedly (standard resilience pattern — a bad message shouldn't block the queue forever).
- Publish happens **after** the Postgres transaction commits (transactional outbox is the textbook-correct way to guarantee this without a distributed transaction; flagged as a hardening item in §13 rather than built into the MVP, since Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` gives "good enough" ordering for a demo without the complexity of an outbox table).

---

## 6. External API surface & the role of Kong

**Decision:** Kong is the single entry point for all external HTTP traffic. No service is reachable directly from outside the Docker network. Kong runs in **DB-less (declarative) mode** — configuration is a YAML file checked into the repo, not stored in a separate Kong-managed Postgres database.

**Why DB-less instead of DB-backed Kong:**
- DB-backed Kong needs its own Postgres instance/schema just to hold routing config — that's operational surface area (another DB to back up, migrate, keep healthy) for information that is, in this project, static and small enough to live in a config file.
- Declarative config is reviewable in a PR and reproducible from a clean checkout — directly serves "verify your work": the gateway's entire routing/security config is inspectable as text, not hidden in a database only Kong's Admin API can read back.
- Verified: Kong Gateway OSS supports DB-less/declarative YAML config as a first-class deployment mode; current stable line is Kong 3.x (3.15.0 as the most recent release found in this session, July 2026).

**Route mapping:**
| External path | Routed to |
|---|---|
| `/api/auth/*`, `/api/users/*` | identity-service |
| `/api/products/*` | catalog-service |
| `/api/orders/*` | order-service |
| `/api/notifications/*` | notification-service |

Sources: [Kong Gateway configuration reference](https://developer.konghq.com/gateway/configuration/), [Kong Gateway releases](https://github.com/Kong/gateway-changelog/releases)

---

## 7. Security at the API gateway

This was an explicit requirement: enforce security at Kong, not re-implemented per service.

**Authentication — JWT, RS256, verified at Kong:**
- `identity-service` holds an RSA keypair. On successful login it signs a JWT (RS256) with the private key. The private key never leaves `identity-service`.
- Kong's **JWT plugin** (confirmed free/OSS, part of Kong's 100+-plugin catalog with no license gate) is configured with the corresponding public key against a Kong "consumer," and verifies signature + expiry on every request to a protected route — invalid/expired tokens are rejected with `401` **before** they ever reach a service. Services therefore don't need to re-implement token verification; they trust the `X-Consumer-*` / forwarded-claims headers Kong injects.
- `POST /api/auth/register` and `POST /api/auth/login` are excluded from the JWT plugin (you can't present a token before you have one).

**Rate limiting:**
- Kong's **rate-limiting plugin** supports a `redis` policy, meaning limits are tracked in Redis rather than per-Kong-node memory — this matters the moment there's more than one Kong replica, and it's the same Redis already required for caching (§9), so no new infrastructure is introduced. Tighter limits are applied to `/api/auth/login` specifically to slow credential-stuffing attempts.

**CORS:**
- Kong's **CORS plugin**, restricted to a configured allow-list of real frontend origins — not `*`.

**Transport & network boundary:**
- Kong terminates TLS for external traffic (self-signed cert for local Compose dev; a real cert/ACME flow is a production concern, explicitly out of scope here — flagged, not solved).
- All services, Postgres, MongoDB, RabbitMQ, Redis, and Consul sit on an internal-only Docker network with no host-published ports beyond what's needed for local debugging (e.g., RabbitMQ's management UI, Grafana). Only Kong is reachable from outside. This — not application code — is the actual security boundary for internal gRPC traffic, which is not authenticated service-to-service in this MVP.
- **Explicitly deferred, not silently ignored:** service-to-service mTLS. Because Consul is already in this stack for discovery (§9), Consul Connect is the natural future path to internal mTLS without adding a new product — noted in §13 as a roadmap item, not built now, since it wasn't asked for and adds real operational complexity (sidecar proxies) beyond this document's scope.

Sources: [Kong Plugin Hub](https://developer.konghq.com/plugins/), [Kong Rate Limiting plugin](https://developer.konghq.com/plugins/rate-limiting/), [Kong CORS plugin](https://developer.konghq.com/plugins/cors/)

---

## 8. Service discovery: Consul

**Decision:** HashiCorp Consul, one agent per Docker Compose network, each service self-registering on startup with an HTTP health check against its own Spring Actuator `/actuator/health`.

**Why Consul over the alternatives:**
| Alternative | Why not |
|---|---|
| Netflix Eureka (Spring Cloud) | Locks discovery to the Spring/JVM ecosystem specifically via Spring Cloud Netflix client libraries. Kong (the thing that actually needs to *resolve* services) is not a Spring app, so Eureka would need a bridge Kong doesn't natively speak. Consul is discoverable by anything that can do a DNS lookup or hit its HTTP API. |
| Kubernetes DNS-based discovery | Not applicable — you explicitly chose Docker Compose, not K8s, as the deployment target for this document (§10 covers what changes if that changes). |
| ZooKeeper | Heavier operationally, and its ecosystem association is mostly Kafka/Hadoop, not a natural fit here where nothing else in the stack needs it. |

**How Kong actually finds services via Consul (verified mechanism, not assumed):** Consul exposes a DNS interface (confirmed default port `8600`, resolving `<service>.service.consul` queries against its service catalog, returning only currently-healthy instances). Kong supports DNS-based upstream resolution including SRV records. So Kong's `dns_resolver` is pointed at the Consul agent's DNS interface, and Kong upstream targets are configured as SRV lookups against `<service-name>.service.consul` — Kong then only ever routes to instances Consul currently considers healthy, without a custom Kong plugin.

**Service registration mechanism:** each service registers itself via a plain HTTP call to its local Consul agent's Agent API (`PUT /v1/agent/service/register`) from a Spring `ApplicationRunner`, rather than pulling in Spring Cloud Consul. I'm not recommending Spring Cloud Consul here because I could not verify its current release train's compatibility with Spring Boot 4.x in this session, and a ~15-line HTTP registration call has no such unverified dependency risk. If you want Spring Cloud Consul instead, that compatibility should be checked explicitly before adopting it.

Sources: [Consul ports reference](https://developer.hashicorp.com/consul/docs/reference/architecture/ports), [Consul DNS configuration](https://developer.hashicorp.com/consul/docs/discover/dns/configure), [Kong load balancing / DNS reference](https://developer.konghq.com/gateway/traffic-control/load-balancing-reference/)

---

## 9. Data & caching

**PostgreSQL — `identity-service` and `order-service`:**
- Database-per-service even though, for local Compose simplicity, both logical databases run on the *same* Postgres 17 container (matching the existing `docker-compose.yml`) with separate database names/credentials/Flyway history tables. In production these would typically be separate instances — noted as a scaling item, not required for this MVP's resource footprint.
- **Migrations: Flyway.** Verified and important: **Spring Boot 4.x changed Flyway auto-configuration** — adding `flyway-core` alone is no longer sufficient; you need the explicit `spring-boot-starter-flyway` plus `flyway-database-postgresql` for PostgreSQL support. This matters directly because the existing `build.gradle.kts` is already on Spring Boot `4.0.6` — any Flyway setup here must account for this or migrations silently won't run.
- **Seed data**, done correctly (i.e., not fake user data baked into a migration that's supposed to be immutable forever): reference/lookup tables. `order-service` seeds an `order_status` lookup table (`PLACED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`) via `V2__seed_order_statuses.sql`; `identity-service` seeds a `roles` lookup table (`ADMIN`, `CUSTOMER`) via `V2__seed_roles.sql`. This is the standard, defensible use of Flyway seed migrations — static reference data the app logic depends on existing, versioned like schema.

**MongoDB — `catalog-service` and `notification-service`:**
- **Migrations: Flamingock**, not Mongock — per your decision above, given Mongock's confirmed end-of-life at the end of 2026. Verified via Flamingock's own "Coming from Mongock" migration guide: Flamingock uses `@Change` on the migration class (replacing Mongock's `@ChangeUnit`), `@Apply` (replacing `@Execution`), and `@Rollback` (replacing `@RollbackExecution`); Spring Boot integration is `@EnableFlamingock` on the application class plus the `flamingock-spring-boot-starter` dependency (Maven coordinates confirmed; exact version to pin at implementation time — I found artifacts like `flamingock-ce-mongodb-springdata-v3` still labeled `-beta` on Maven Central as of this session, which is worth knowing before committing to it for anything beyond a demo).
- Seed: `catalog-service` seeds a handful of sample products via a `@Change`-annotated migration — the same "static data the app needs to exist" justification as above, just via code instead of SQL since that's how Flamingock/Mongock migrations work.

**Redis — cache, not source of truth, in two services plus Kong:**
| Consumer | Key pattern | TTL | Purpose |
|---|---|---|---|
| catalog-service | `catalog:product:{id}` | 5 min | Cache hot product-detail reads; evicted on `ReserveStock`/update |
| order-service | `order:idem:{idempotency-key}` | 24h | Makes `POST /api/orders` safe to retry without double-placing an order |
| Kong rate-limiting plugin | Kong-managed | — | Distributed rate-limit counters (redis policy), same Redis instance, separate logical DB index (e.g. DB 1) from app cache (DB 0) to avoid key collisions |

Sources: [Flyway + Spring Boot 4.x changes](https://pranavkhodanpur.medium.com/flyway-migrations-in-spring-boot-4-x-what-changed-and-how-to-configure-it-correctly-dbe290fa4d47), [Mongock sunsetting announcement](https://flamingock.io/blog/sunsetting-mongock/), [Flamingock "Coming from Mongock" guide](https://docs.flamingock.io/resources/coming-from-mongock)

---

## 10. Observability: metrics, logs, and traces correlated in one place

The requirement listed Grafana, Prometheus, Loki, Micrometer, and OpenTelemetry — that's metrics tooling (Prometheus/Micrometer), a viz layer (Grafana), and a logs backend (Loki), but no trace *storage* backend. OpenTelemetry is the SDK/protocol, not a place traces live.

**Decision: replace the existing `jaeger` container with Grafana Tempo**, keeping everything else already in `docker-compose.yml` (Prometheus, Grafana) and adding Loki.

**Why Tempo instead of keeping Jaeger:** Tempo, Loki, and Grafana are the same product family (Grafana Labs), designed to cross-link: a metric spike in Grafana can jump to the exact trace in Tempo, and a trace can jump to the exact log lines in Loki that share its trace ID — this three-way correlation is the actual point of "distributed logging," not just having three separate dashboards. Jaeger is a fine trace backend on its own, but doesn't get you that native cross-correlation with Loki/Grafana the way Tempo does. This is a specific, deliberate call — flag it if you'd rather keep Jaeger; nothing else in the architecture depends on which one is used, since both speak OTLP.

**How the pieces connect:**
- **Metrics:** Micrometer (already a dependency in the existing `build.gradle.kts`) exposes `/actuator/prometheus` per service; Prometheus (already running) scrapes it — plus new scrape targets for Kong's Prometheus plugin, Consul's telemetry endpoint, and RabbitMQ's Prometheus plugin, so the infra layer is monitored, not just the app layer.
- **Traces:** OpenTelemetry SDK (already a dependency: `spring-boot-starter-opentelemetry`) exports spans via OTLP to Tempo instead of Jaeger — this is a one-line endpoint change from what's already configured, not new integration work.
- **Logs → Loki:** container stdout is scraped by Promtail (or Grafana Alloy, Loki's newer unified agent) and shipped to Loki. The correlation that makes this "distributed" logging rather than four separate log streams: Micrometer Tracing injects the current `trace_id`/`span_id` into the logging MDC, so every log line carries the trace ID that produced it — Grafana can jump from a slow trace in Tempo straight to the exact log lines across all four services that were emitted while handling that request.
- **Grafana:** single pane of glass querying all three backends (Prometheus, Loki, Tempo), which is why it's positioned as the viz layer and not a data store itself.

---

## 11. Multi-module build layout (target state)

```
KotlinCrud/
├── settings.gradle.kts          # includes every module below
├── build.gradle.kts             # shared plugin versions / conventions only
├── proto/
│   ├── identity-proto/          # identity.proto + generated grpc-kotlin stubs
│   └── catalog-proto/           # catalog.proto + generated grpc-kotlin stubs
├── identity-service/
├── catalog-service/
├── order-service/                # depends on identity-proto, catalog-proto (stubs only, never on the service modules themselves)
├── notification-service/
├── gateway/
│   └── kong.decl.yaml            # Kong declarative config, checked in
└── observability/
    ├── prometheus.yml
    ├── loki-config.yml
    └── grafana/provisioning/
```

The `proto` modules are the *only* thing one service module is allowed to depend on from another — this is what makes "database-per-service, no shared code" actually enforceable at the build-graph level rather than just a convention someone can quietly violate.

---

## 12. If this ever moves to Kubernetes (explicitly not now)

You chose Docker Compose for now, but since this trade-off is easy to get wrong later, stating it here rather than leaving it implicit:
- **Consul would likely become redundant** — Kubernetes' built-in DNS-based Service discovery does what Consul is doing here, natively, with no extra component.
- **Kong would move from a standalone container to the Kong Ingress Controller**, which manages Kong configuration from Kubernetes `Ingress`/CRD resources instead of a hand-maintained declarative YAML file.
- Everything else in this document (gRPC contracts, RabbitMQ topology, Redis usage, database-per-service, the observability stack) is deployment-target-agnostic and would not change.

---

## 13. Explicitly deferred (roadmap, not built now)

- **Transactional outbox** for the RabbitMQ publish in `order-service`, to remove the small window where the DB commits but the broker publish fails.
- **Service-to-service mTLS** via Consul Connect, since Consul is already present.
- **Payment and shipping bounded contexts** (see §1).
- **Real TLS certs / ACME** at Kong for anything beyond local dev.
- Pin exact dependency versions (`io.grpc`, `flamingock-spring-boot-starter`, Kong image tag, Consul image tag) at implementation time — this document names the libraries and confirms they exist and do what's claimed, but did not pin every version number, since some (Flamingock in particular) are moving quickly enough that a version pinned today would likely already be stale by the time code is written.

---

## 14. Open item carried over from this conversation

The existing `build.gradle.kts`/`src/` in this repo is a single Java (not Kotlin) Spring Boot module and does not yet match the multi-module Kotlin layout in §11. Per your answer, this document intentionally doesn't resolve whether that becomes `identity-service`, gets discarded, or something else — that's the next decision, separate from this architecture doc.
