plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":proto:identity-proto"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.grpc.kotlin.stub)

    // Password hashing. spring-security-crypto alone (BCryptPasswordEncoder) is
    // deliberately used instead of the full spring-boot-starter-security filter
    // chain — per ARCHITECTURE.md §7, Kong owns authentication/JWT verification
    // at the gateway, so identity-service only needs a hashing primitive, not a
    // servlet security filter chain. Version is managed transitively by Spring
    // Boot's dependency-management BOM (same convention already used below for
    // org.postgresql:postgresql, which also has no explicit version here).
    implementation("org.springframework.security:spring-security-crypto")

    // RS256 JWT issuance (login). See RsaKeyPairProvider/JwtTokenIssuerAdapter for
    // why jjwt was chosen over Nimbus JOSE+JWT: a small, fluent, purpose-built API
    // for exactly "build and sign a JWT" / "parse and verify a JWT", with no extra
    // JOSE object-model concepts we don't need for this phase.
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 moved TestRestTemplate out of spring-boot-test into this
    // module (org.springframework.boot.resttestclient.TestRestTemplate), which
    // in turn needs RestTemplateBuilder from spring-boot-restclient (Boot 4
    // split the old spring-boot-autoconfigure web-client support out of the
    // web starter).
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
