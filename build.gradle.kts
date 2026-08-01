import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.rafaelmatheus"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Versoes fora do BOM do Spring Boot, reunidas aqui para que a atualizacao seja
// um lugar so. Versao fixada e divida com prazo — foi o que derrubou o apply do
// RDS em engine_version = "16.6".
val jjwtVersion = "0.12.5"
val kotestVersion = "5.9.1"
val springdocVersion = "2.8.6"
val archunitVersion = "1.3.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Migrations (RNF-04, D-01). O suporte a PostgreSQL saiu do core no Flyway 10:
    // sem o segundo artefato a aplicacao sobe e falha com "Unsupported Database".
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // JWT stateless (D-02). api em compileOnly-like, impl e jackson so em runtime.
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // Contrato de API gerado do codigo (D-06, RNF-08).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")

    // Property-based testing (RNF-07, D-05, PBT-09).
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    // Imposicao estrutural do isolamento (D-66). O teste de arquitetura reprova
    // o build quando uma entidade com dono nasce fora do padrao — em U3 sao seis
    // entidades novas, e a garantia deixa de caber na disciplina de quem escreve.
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
