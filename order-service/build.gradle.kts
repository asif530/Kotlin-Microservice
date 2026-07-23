plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Compile-time only: generated gRPC contract stubs, never the other services' code.
    implementation(project(":proto:identity-proto"))
    implementation(project(":proto:catalog-proto"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.grpc.netty)
    implementation(libs.grpc.kotlin.stub)
    // The generated gRPC *client* stubs (IdentityServiceCoroutineStub/
    // CatalogServiceCoroutineStub) are `suspend fun`s; order-service's own call chain
    // (Spring MVC -> OrderService) is plain/blocking, same style as every other service in
    // this codebase, so IdentityGrpcClientAdapter/CatalogGrpcClientAdapter bridge into them
    // via `runBlocking` — see those classes' kdoc. `identity-service`/`catalog-service` don't
    // need this in their own `implementation` deps because grpc-kotlin's *server* dispatch
    // mechanism already runs their suspend functions in a coroutine for them.
    implementation(libs.kotlinx.coroutines.core)

    // RS256 JWT verification (ORD-001/ORD-013's caller-identity resolution). order-service
    // never signs tokens — only identity-service holds the private key — so only jjwt-api plus
    // the two runtime providers are needed, same as catalog-service's identical choice.
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
    // Spring Boot 4 moved TestRestTemplate out of spring-boot-test into this module (see
    // identity-service/build.gradle.kts for the same two additions and why they're needed).
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
