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

```kotlin
// build.gradle.kts
implementation("io.mongock:mongock-springboot-v3:5.4.4")
implementation("io.mongock:mongodb-springdata-v4-driver:5.4.4")
```

### 2. Structure Migration Classes
Each migration is a `@ChangeUnit` class — one class per version, never edited after it runs.
Flamingock is the new alternative

```java
@ChangeUnit(id = "migration-001-add-user-status", order = "001", author = "dev")
public class Migration001AddUserStatus {

    @Execution
    public void execute(MongoDatabase db) {
        db.getCollection("users")
          .updateMany(new Document(), Updates.set("status", "ACTIVE"));
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        db.getCollection("users")
          .updateMany(new Document(), Updates.unset("status"));
    }
}
```

### 3. Key Rules
- **Never modify** a `@ChangeUnit` after it has been applied — create a new one instead.
- **Always implement rollback** (`@RollbackExecution`) for every migration.
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

```java
@Execution
public void execute(MongoDatabase db) {
    db.getCollection("orders")
      .createIndex(Indexes.ascending("userId", "createdAt"),
                   new IndexOptions().name("idx_orders_user_date").background(true));
}
```

### 6. Application Config
```yaml
# application.yml
mongock:
  migration-scan-package: com.kotlin.crud.migrations
  transaction-enabled: false  # MongoDB transactions require replica set
```

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
