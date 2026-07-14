# KotlinCrud — Project Capabilities (from build.gradle.kts)

## Stack Overview

**Spring Boot 4.0.6** on **Java 25** — a modern, production-grade setup.

---

## What You Can Build

### REST API (Web MVC)
- `spring-boot-starter-webmvc` — build standard REST controllers (`@RestController`, `@GetMapping`, etc.)
- `springdoc-openapi-starter-webmvc-ui` — auto-generates **Swagger/OpenAPI UI** at `/swagger-ui.html` so your API is self-documenting

### GraphQL API
- `spring-boot-starter-graphql` — build a **GraphQL API** alongside or instead of REST using `@QueryMapping`, `@MutationMapping`, schema files, etc.

### CRUD with Two Databases
- `spring-boot-starter-data-jpa` + `postgresql` (runtime) — **relational/SQL CRUD** using JPA/Hibernate with PostgreSQL
- `spring-boot-starter-mongodb` — **NoSQL/document CRUD** using MongoDB with Spring Data repositories

### Message Queue (Async/Event-Driven)
- `spring-boot-starter-amqp` — **RabbitMQ** integration for publishing and consuming messages asynchronously (event-driven patterns, decoupled services)

### Observability & Monitoring
- `spring-boot-starter-actuator` — health checks, metrics, info endpoints (`/actuator/*`)
- `spring-boot-starter-opentelemetry` — **distributed tracing** with OpenTelemetry (send traces to Jaeger, Zipkin, OTLP backends)
- `micrometer-registry-prometheus` — expose metrics to **Prometheus** (scraping at `/actuator/prometheus`)
- `micrometer-registry-otlp` — push metrics via OTLP protocol
- `datasource-micrometer-*` — traces and metrics for **JDBC/database calls** specifically

### Code Generation
- `lombok` (compile + annotation processor) — reduces boilerplate (`@Data`, `@Builder`, `@Slf4j`, etc.)

---

## Summary Table

| Capability                     | Dependency                |
|--------------------------------|---------------------------|
| REST API                       | `webmvc` + `springdoc`    |
| GraphQL API                    | `graphql`                 |
| SQL/Relational DB (PostgreSQL) | `data-jpa` + `postgresql` |
| NoSQL/Document DB (MongoDB)    | `mongodb`                 |
| Message Queue (RabbitMQ)       | `amqp`                    |
| Health & Metrics               | `actuator` + `prometheus` |
| Distributed Tracing            | `opentelemetry` + `otlp`  |
| DB query tracing               | `datasource-micrometer`   |
| Boilerplate reduction          | `lombok`                  |

This is a **full-stack backend microservice** setup — capable of dual-database CRUD (SQL + MongoDB), REST + GraphQL APIs, async messaging, and full observability out of the box.

---

## MongoDB Migration Best Practices

### 1. Use a Migration Tool
Use **Mongock** (the standard for Spring Boot + MongoDB migrations) instead of manual scripts. (Deprecated)

**This project actually uses Flamingock, not Mongock** — Mongock's own maintainers are sunsetting it in favor of Flamingock (its official successor; see Sources). `io.flamingock:flamingock-core:1.4.4` is already wired into `catalog-service` and `notification-service` via the `flamingock` entry in `gradle/libs.versions.toml` (see `Archive/Issues/Library`). Everything below is Flamingock's real API and setup, checked against its current docs and the Gradle Plugin Portal in this session — not Mongock's, and not guessed.

```kotlin
// build.gradle.kts — core dependency already present via the version catalog:
implementation(libs.flamingock.core)

// Not yet added to this project. Archive/Issues/Library previously flagged the
// Gradle plugin block as unverifiable because Flamingock's own quick-start docs
// show a literal unresolved "[VERSION]" placeholder. Checked directly against
// the Gradle Plugin Portal in this session: "io.flamingock" 1.4.4 is the latest
// published version there, matching flamingock-core's own version — safe to use.
plugins {
    id("io.flamingock") version "1.4.4"
}

flamingock {
    community()
}
```

### 2. Structure Migration Classes
Each migration is a `@Change` class — Flamingock's rename of Mongock's `@ChangeUnit` — one class per version, never edited after it runs. The execution/rollback annotations were renamed too: `@Execution` → `@Apply`, `@RollbackExecution` → `@Rollback`. All of these, plus `@EnableFlamingock`, import from `io.flamingock.api.annotations`.

```kotlin
import io.flamingock.api.annotations.Change
import io.flamingock.api.annotations.Apply
import io.flamingock.api.annotations.Rollback
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Updates
import org.bson.Document

@Change(id = "migration-001-add-user-status", author = "dev")
class Migration001AddUserStatus {

    @Apply
    fun apply(db: MongoDatabase) {
        db.getCollection("users")
          .updateMany(Document(), Updates.set("status", "ACTIVE"))
    }

    @Rollback
    fun rollback(db: MongoDatabase) {
        db.getCollection("users")
          .updateMany(Document(), Updates.unset("status"))
    }
}
```

Enable Flamingock once, on the service's main application class:
```kotlin
import io.flamingock.api.annotations.EnableFlamingock
import org.springframework.boot.autoconfigure.SpringBootApplication

@EnableFlamingock
@SpringBootApplication
class CatalogServiceApplication
```

### 3. Key Rules
- **Never modify** a `@Change` class after it has been applied — create a new one instead.
- **Always implement rollback** (`@Rollback`) for every migration.
- **Use additive changes** — add fields with defaults, never drop or rename fields in a single step.
- **Test on a copy** of production data before applying to prod.
- **Version your IDs** clearly (`migration-001-`, `migration-002-`) to enforce order.

### 4. Safe Schema Change Patterns

| Change              | Safe Approach                                                       |
|---------------------|---------------------------------------------------------------------|
| Add a field         | Add with a default value, backfill existing docs                    |
| Rename a field      | Add new field → backfill → remove old field (3 separate migrations) |
| Remove a field      | Deprecate in code first, then drop in a later migration             |
| Add an index        | Use `background: true` (non-blocking) on large collections          |
| Change a field type | Migrate data to a new field, swap references, then clean up         |

### 5. Index Management
Create indexes in migrations, not in `@Document` annotations, to keep control explicit.

```kotlin
@Apply
fun apply(db: MongoDatabase) {
    db.getCollection("orders")
      .createIndex(Indexes.ascending("userId", "createdAt"),
                   IndexOptions().name("idx_orders_user_date").background(true))
}
```

### 6. Application Config
```yaml
# application.yml
flamingock:
  runner-type: application_runner   # confirmed key; runs after the Spring context is fully initialized
```
Mongock's `migration-scan-package` and `transaction-enabled` keys don't have a confirmed Flamingock `application.yml` equivalent as of this check — stage/package location is declared via `@EnableFlamingock(stages = [Stage(location = "...")])` in code instead (see §2). Not invented here; confirm against Flamingock's current docs before relying on either key.

**Sources:** [Flamingock quick start](https://docs.flamingock.io/get-started/quick-start) · [Spring Boot integration](https://docs.flamingock.io/frameworks/springboot-integration/introduction) · [Sunsetting Mongock](https://flamingock.io/blog/sunsetting-mongock/) · [Gradle Plugin Portal: io.flamingock](https://plugins.gradle.org/plugin/io.flamingock)

---

## Redis Implementation

### 1. Add Dependency
```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

### 2. Configuration
```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password   # omit if no auth
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 1
```

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

### 3. Use Cases in This Project

| Use Case                        | How                                  |
|---------------------------------|--------------------------------------|
| Cache JPA/MongoDB query results | `@Cacheable` on service methods      |
| Session storage                 | Spring Session with Redis            |
| Rate limiting                   | Increment a counter key with TTL     |
| RabbitMQ deduplication          | Store processed message IDs with TTL |
| Pub/Sub messaging               | `RedisMessageListenerContainer`      |

### 4. Caching with Annotations
Enable caching once, then annotate service methods.

```java
// Main class or config
@EnableCaching

// Service
@Cacheable(value = "users", key = "#id")
public User findById(String id) { ... }

@CachePut(value = "users", key = "#user.id")
public User update(User user) { ... }

@CacheEvict(value = "users", key = "#id")
public void delete(String id) { ... }
```

```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000   # 10 minutes in ms
```

### 5. Manual Cache Operations (RedisTemplate)
Use when you need fine-grained TTL or data structure control.

```java
@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void store(String token, Object data, long ttlSeconds) {
        redisTemplate.opsForValue().set("token:" + token, data, ttlSeconds, TimeUnit.SECONDS);
    }

    public Object get(String token) {
        return redisTemplate.opsForValue().get("token:" + token);
    }

    public void invalidate(String token) {
        redisTemplate.delete("token:" + token);
    }
}
```

### 6. Redis Data Structures

| Structure     | Spring API      | Best For                       |
|---------------|-----------------|--------------------------------|
| String / JSON | `opsForValue()` | Cache objects, counters        |
| List          | `opsForList()`  | Queues, recent activity logs   |
| Set           | `opsForSet()`   | Unique tags, online users      |
| Sorted Set    | `opsForZSet()`  | Leaderboards, priority queues  |
| Hash          | `opsForHash()`  | User sessions, partial updates |

### 7. Key Naming Convention
```
{service}:{entity}:{id}        → users:profile:42
{service}:{operation}:{param}  → orders:summary:user-7
token:{value}                  → token:abc123xyz
rate:{ip}:{endpoint}           → rate:192.168.1.1:POST/api/orders
```

### 8. Best Practices
- Always set a **TTL** — never store without expiry unless intentional.
- Use **connection pooling** (Lettuce pool config above) for high-throughput.
- Serialize values as **JSON** (not Java serialization) for debuggability.
- Keep keys **short but readable** — Redis stores keys in memory.
- Use **`@CacheEvict(allEntries = true)`** sparingly — prefer targeted eviction by key.
