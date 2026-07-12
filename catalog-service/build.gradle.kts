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
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.grpc.kotlin.stub)

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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
