plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":proto:catalog-proto"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.grpc.kotlin.stub)

    // RS256 JWT verification (CAT-006's admin-only gate). catalog-service never signs
    // tokens — only identity-service holds the private key (RsaKeyPairProvider) — so only
    // jjwt-api plus the two runtime providers are needed here, same as identity-service's own
    // choice of jjwt over Nimbus JOSE+JWT (see identity-service/build.gradle.kts).
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Mongo schema/data migrations (Flamingock, per Archive/Architecture/ARCHITECTURE.md §9).
    // Only the core library dependency is wired here. Flamingock's Gradle plugin +
    // annotation-processor setup (needed to actually run @Change migrations) could not be
    // verified with confidence in this session — its quick-start docs use unresolved
    // "[VERSION]" placeholders. Confirm the current plugin/DSL at https://docs.flamingock.io
    // before writing real migrations; do not copy this comment's absence of a plugin block
    // as evidence migrations will work without further setup.
    implementation(libs.flamingock.core)

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 moved TestRestTemplate out of spring-boot-test into this module (see
    // identity-service/build.gradle.kts for the same two additions and why they're needed).
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    // Phase 5's gRPC server methods are `suspend fun`s — tests call them via
    // `runBlocking` (see identity-service/build.gradle.kts for why this
    // isn't already transitively visible from grpc-kotlin-stub).
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
