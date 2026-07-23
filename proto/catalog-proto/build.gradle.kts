import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    `java-library`
}

dependencies {
    // `api`, not `implementation`: this module's whole purpose is generated
    // types (message classes, the CoroutineStub/CoroutineImplBase base
    // classes) that consuming service modules construct and extend
    // directly — `implementation` would hide com.google.protobuf.* /
    // io.grpc.kotlin.* from their compile classpath entirely (caught when
    // order-service/catalog-service first tried to actually implement a
    // gRPC server/client in Phase 5; nothing before that phase exercised
    // these generated types).
    api(libs.protobuf.kotlin)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.java.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.java.get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}
