# Technology Stack

## Programming Languages

| Linguagem | Versão | Uso |
|---|---|---|
| Kotlin | 2.1.21 | Linguagem de 100% do código-fonte (`src/main`, `src/test`). Compilada para `jvmTarget = JVM_21`, com `freeCompilerArgs = ["-Xjsr305=strict"]` (anotações de nulidade JSR-305 tratadas como estritas pelo compilador). |
| Kotlin Script (`.kts`) | 2.1.21 | DSL de build — `build.gradle.kts`, `settings.gradle.kts`. |
| Java (plataforma) | 21 (toolchain) | Plataforma de execução. Não há arquivos `.java` no projeto. |
| YAML | — | Configuração de aplicação e do Docker Compose. |

## Frameworks

| Framework | Versão | Propósito |
|---|---|---|
| Spring Boot | 3.5.4 | Framework de aplicação; auto-configuração e BOM que gerencia as versões de todas as dependências transitivas. |
| Spring Web MVC | via BOM (`spring-boot-starter-web`) | Stack HTTP servlet com Tomcat embarcado. **Nenhum controller implementado.** |
| Spring Data JPA | via BOM (`spring-boot-starter-data-jpa`) | Abstração de persistência sobre Hibernate. **Nenhum repositório ou entidade implementada.** |
| Hibernate ORM | via BOM (transitivo de `starter-data-jpa`) | Provedor JPA. Configurado com `ddl-auto: validate` (`main`) e `create-drop` (perfil `test`), `jdbc.time_zone: UTC`. |
| Bean Validation / Hibernate Validator | via BOM (`spring-boot-starter-validation`) | Validação declarativa. **Nenhuma anotação de validação em uso.** |
| Spring Boot Actuator | via BOM (`spring-boot-starter-actuator`) | Endpoints operacionais. Expostos: `health`, `info`. |
| Jackson (módulo Kotlin) | via BOM (`jackson-module-kotlin`) | Serialização JSON compatível com data classes Kotlin. |
| kotlin-reflect | 2.1.21 | Reflexão Kotlin, exigida pelo Spring para resolução de parâmetros de construtor. |

### Plugins de compilação Kotlin

| Plugin | Versão | Propósito |
|---|---|---|
| `kotlin("jvm")` | 2.1.21 | Compilação Kotlin/JVM. |
| `kotlin("plugin.spring")` (all-open) | 2.1.21 | Abre classes anotadas com estereótipos Spring (Kotlin gera classes `final` por padrão, incompatível com proxies CGLIB). |
| `kotlin("plugin.jpa")` (no-arg) | 2.1.21 | Gera construtores sem argumento para `@Entity`, `@MappedSuperclass`, `@Embeddable` — exigência do Hibernate. **Configurado, ainda não exercitado.** |

## Infrastructure

| Serviço | Propósito |
|---|---|
| PostgreSQL 16 (`postgres:16-alpine`) | Único data store. Provisionado localmente via `docker-compose.yml`: container `financial-control-db`, database `financial_control`, usuário/senha `financial`, porta `5432` publicada, volume nomeado `postgres_data`, healthcheck `pg_isready`, `restart: unless-stopped`. **Sem tabelas de negócio.** |
| Tomcat embarcado | Servidor HTTP, via `spring-boot-starter-web`. Porta configurável por `SERVER_PORT` (default `8080`). |
| HikariCP | Pool de conexões JDBC. Configurado com `maximum-pool-size: 10`. |
| Docker / Docker Compose | Runtime de containers, usado para o banco local e pelos Testcontainers. |

**Não presentes**: cloud provider (nenhum SDK AWS/Azure/GCP), IaC (CDK/Terraform/CloudFormation),
orquestração (Kubernetes/ECS), CDN, message broker, cache distribuído, object storage.
**Não existe `Dockerfile`** — a aplicação não é containerizada.

## Build Tools

| Ferramenta | Versão | Propósito |
|---|---|---|
| Gradle | 8.14.2 (via wrapper) | Automação de build. Configurado com `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m`. ⚠️ O `gradle-wrapper.jar` não está versionado neste commit — gerar com `gradle wrapper --gradle-version 8.14.2` antes do primeiro build. |
| Gradle Kotlin DSL | 8.14.2 | Scripts de build type-safe em Kotlin. |
| `org.springframework.boot` (plugin) | 3.5.4 | Empacotamento de fat JAR, `bootRun`, aplicação do BOM. |
| `io.spring.dependency-management` (plugin) | 1.1.7 | Gerenciamento de versões via BOM — permite declarar dependências sem versão explícita. |
| Java Toolchain | JDK 21 | Garante compilação e execução consistentes independentemente do JDK local. |

## Testing Tools

| Ferramenta | Versão | Propósito |
|---|---|---|
| JUnit 5 (Jupiter) | via BOM | Framework de testes. Habilitado por `tasks.withType<Test> { useJUnitPlatform() }`. |
| `kotlin-test-junit5` | 2.1.21 | Asserções idiomáticas Kotlin integradas ao JUnit 5. |
| Spring Boot Test | via BOM (`spring-boot-starter-test`) | `@SpringBootTest`, MockMvc, AssertJ, Mockito, Awaitility, JSONassert (transitivos). |
| Spring Boot Testcontainers | via BOM (`spring-boot-testcontainers`) | Integração `@ServiceConnection` — deriva propriedades de conexão a partir do container. |
| Testcontainers PostgreSQL | via BOM (`org.testcontainers:postgresql`) | Container PostgreSQL efêmero para testes. |
| Testcontainers JUnit 5 | via BOM (`org.testcontainers:junit-jupiter`) | Integração de ciclo de vida com JUnit 5. |
| `junit-platform-launcher` | via BOM | Runtime de execução (escopo `testRuntimeOnly`). |

**Pré-requisito de execução**: `./gradlew test` **exige Docker em execução**, pois os testes sobem
um container PostgreSQL real.

## Lacunas na stack (relevantes para as próximas fases)

| Ausente | Consequência |
|---|---|
| Ferramenta de migration (Flyway / Liquibase) | Com `ddl-auto: validate` no perfil default, a aplicação **deixará de subir assim que a primeira `@Entity` for criada**, pois não haverá schema correspondente. Decisão bloqueante para a Construction. |
| Spring Security | Todos os endpoints são anônimos. Também torna `show-details: when-authorized` inefetivo no `/actuator/health`. |
| springdoc-openapi (Swagger) | Sem documentação de API gerada. |
| ktlint / detekt | Sem lint ou análise estática. |
| JaCoCo / Kover | Sem medição de cobertura de testes. |
| Micrometer registry (Prometheus, OTLP) | Sem exportação de métricas. |
| Logging estruturado (JSON) | Logs em texto plano; dificulta agregação. |
| CI/CD (`.github/workflows`) | Build e testes rodam apenas localmente. |
