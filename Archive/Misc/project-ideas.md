# Project Ideas — KotlinCrud Stack

Based on the full stack: PostgreSQL, MongoDB, RabbitMQ, Redis, GraphQL, REST, OpenTelemetry, Prometheus/Grafana.

---

## 1. E-Commerce Platform
**The most natural fit for this stack.**

| Tool       | Role                                                               |
|------------|--------------------------------------------------------------------|
| PostgreSQL | Orders, payments, users, inventory                                 |
| MongoDB    | Product catalog (flexible attributes per category)                 |
| RabbitMQ   | Order placed → trigger email, inventory update, invoice generation |
| Redis      | Shopping cart, session, product page cache                         |
| GraphQL    | Flexible product search/filtering queries from frontend            |
| REST       | Checkout, payment, admin APIs                                      |

---

## 2. Real-Time Notification & Alert System 
**RabbitMQ + Redis shine here.**

| Tool       | Role                                                        |
|------------|-------------------------------------------------------------|
| PostgreSQL | User preferences, alert rules, subscription plans           |
| MongoDB    | Notification history (schema varies per alert type)         |
| RabbitMQ   | Fan-out delivery: one event → email + push + SMS workers    |
| Redis      | Online presence tracking, deduplication of duplicate alerts |
| REST       | API to create/manage alert subscriptions                    |

---

## 3. Logistics & Delivery Tracking
**Event-heavy, great for async + observability.**

| Tool               | Role                                                                     |
|--------------------|--------------------------------------------------------------------------|
| PostgreSQL         | Orders, drivers, routes                                                  |
| MongoDB            | Tracking events (high-write, flexible per-carrier schema)                |
| RabbitMQ           | Status updates (picked up → in transit → delivered) fan out to customers |
| Redis              | Live location cache (updated every few seconds, expires fast)            |
| GraphQL            | Driver app queries — fetch only the fields needed per screen             |
| Prometheus/Grafana | Monitor delivery SLA breaches, queue depth                               |

---

## 4. Job Board / Recruitment Platform
**GraphQL is a perfect fit for flexible job queries.**

| Tool       | Role                                                              |
|------------|-------------------------------------------------------------------|
| PostgreSQL | Users, applications, companies, interviews                        |
| MongoDB    | Job listings (each company has different required fields)         |
| RabbitMQ   | Application submitted → notify recruiter, send confirmation email |
| Redis      | Cache popular search results, rate-limit applications per user    |
| GraphQL    | Job search with dynamic filters (location, salary, stack, remote) |

---

## 5. IoT Sensor Data Platform
**MongoDB + RabbitMQ are purpose-built for this.**

| Tool               | Role                                                                   |
|--------------------|------------------------------------------------------------------------|
| PostgreSQL         | Devices registry, users, alert thresholds                              |
| MongoDB            | Time-series sensor readings (high volume, schema-free per device type) |
| RabbitMQ           | Devices publish readings → consumers persist + evaluate thresholds     |
| Redis              | Real-time latest reading per device, rolling aggregations              |
| Prometheus/Grafana | Full observability on ingestion pipeline throughput                    |
| REST               | Device registration, historical data export                            |

---

## 6. Multi-Tenant SaaS CMS
**GraphQL + MongoDB are ideal for flexible content.**

| Tool       | Role                                                                 |
|------------|----------------------------------------------------------------------|
| PostgreSQL | Tenants, users, roles, billing                                       |
| MongoDB    | Content pages, blog posts, media metadata (schema per content type)  |
| RabbitMQ   | Publish event → CDN cache invalidation, search index update          |
| Redis      | Page-level output cache, rate limiting per tenant                    |
| GraphQL    | Headless CMS API — clients query exactly the content shape they need |

---

## Recommendation

**Start with the E-Commerce Platform** — it uses every tool naturally, the domain is well-understood, and it covers all patterns you'll need (CRUD, async events, caching, dual-DB, GraphQL). The others are good follow-ups once you're comfortable with the stack.
