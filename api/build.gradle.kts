import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.security.MessageDigest

plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.goldpet"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

configurations.all {
    exclude(group = "commons-logging", module = "commons-logging")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.amazonaws:aws-java-sdk-s3:1.12.559")
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.hibernate.orm:hibernate-spatial:6.4.4.Final")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")
    implementation("com.warrenstrange:googleauth:1.5.0")
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.github.usefulness:webp-imageio:0.10.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.bucket4j:bucket4j-redis:8.10.1")
    // BLOCKER #9 — Sentry 에러 모니터링. DSN 부재/공백 시 SDK no-op (이벤트 미전송).
    // 정확 patch 버전 고정 (range 금지). 착수 시점 latest-stable: 8.28.0.
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.28.0")
    // ShedLock — 멀티인스턴스(스케일아웃) 환경에서 @Scheduled 잡 중복발화 방지.
    // LockProvider = JDBC(shedlock 테이블, V82). Redis 캐시는 maxmemory-policy=allkeys-lru(인스턴스 전역)라
    // TTL 락 키가 evict → 락 증발 위험 → DB 영속(eviction 면역) JDBC provider 채택.
    // Spring Boot 3.4 호환 5.x 핀 (6.x 점프 금지 — 잠재 호환 변경).
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // 통합 테스트는 deploy-local의 PostGIS 컨테이너(localhost:5433)를 직접 사용한다.
    // TestContainers는 macOS docker-java unix socket 호환 이슈로 도입 보류.
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

openApi {
    // Use a non-standard port so `generateOpenApiDocs` does not collide with
    // the deployed API on 8081 when Jenkins runs on the same host.
    apiDocsUrl.set("http://localhost:18081/v3/api-docs")
    outputDir.set(file("$projectDir"))
    outputFileName.set("openapi.json")
    waitTimeInSeconds.set(60)
    customBootRun {
        args.set(listOf(
            "--spring.profiles.active=codegen",
            "--server.port=18081",
        ))
    }
}

/**
 * OpenAPI drift gate.
 *
 * Regenerates openapi.json and compares a canonicalized sha256 against the committed
 * snapshot staged at `build/openapi-committed.json`. Intended to run in CI after the
 * Jenkins shell stashes the committed version:
 *
 *   mkdir -p api/build && cp api/openapi.json api/build/openapi-committed.json
 *   ./gradlew -p api verifyOpenApiSpec
 *
 * Can also be invoked locally the same way to prove your branch's spec matches the
 * regenerated output.
 */
tasks.register("verifyOpenApiSpec") {
    group = "openapi"
    description = "Fails if the committed openapi.json disagrees with a freshly generated one."
    dependsOn("generateOpenApiDocs")

    doLast {
        val committed = file("build/openapi-committed.json")
        val generated = file("openapi.json")
        if (!committed.exists()) {
            throw GradleException(
                "Missing build/openapi-committed.json. In CI, Jenkins must run:\n" +
                "  cp api/openapi.json api/build/openapi-committed.json\n" +
                "before invoking ./gradlew verifyOpenApiSpec."
            )
        }
        if (!generated.exists()) {
            throw GradleException("openapi.json missing after generateOpenApiDocs — plugin failed silently?")
        }

        fun canonicalize(f: File): String {
            // Normalize via jq: sort object keys recursively + sort arrays by toString.
            // Eliminates JVM-reflection-order nondeterminism (parameters/tags/enum arrays).
            val tmp = file("build/${f.nameWithoutExtension}.canonical.json")
            val filter = "walk(if type == \"array\" then sort_by(tostring) else . end)"
            val exitCode = project.exec {
                commandLine("jq", "-S", filter)
                standardInput = f.inputStream()
                standardOutput = tmp.outputStream()
                isIgnoreExitValue = true
            }.exitValue
            if (exitCode != 0) throw GradleException("jq canonicalization failed (exit $exitCode) for ${f.path}")
            val digest: ByteArray = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes())
            return digest.joinToString("") { b -> "%02x".format(b) }
        }

        val committedHash = canonicalize(committed)
        val generatedHash = canonicalize(generated)

        if (committedHash != generatedHash) {
            throw GradleException(
                "openapi.json drift detected.\n" +
                "  committed sha256 = $committedHash\n" +
                "  generated sha256 = $generatedHash\n" +
                "Fix: run `./gradlew -p api generateOpenApiDocs` and commit the updated api/openapi.json + regenerated schema.d.ts files."
            )
        }
        logger.lifecycle("✓ verifyOpenApiSpec: committed spec matches freshly-generated (sha256=${committedHash.take(12)}…)")
    }
}
