# Code Quality Assessment

> **Calibração**: este é um esqueleto de ~14 arquivos, com 3 arquivos Kotlin e nenhum código de
> negócio. A avaliação abaixo julga o repositório **pelo que ele se propõe a ser** — uma base
> executável para o domínio ser gerado pelo AI-DLC — e não como um sistema em produção. Vários
> itens marcados como "ausente" são esperados neste estágio; o valor deles é servir de checklist
> para as próximas fases.

## Test Coverage

- **Overall**: **Nenhuma cobertura de negócio** (não há negócio para cobrir). A cobertura de
  infraestrutura é adequada ao estágio.
- **Unit Tests**: Nenhum teste unitário. Não há unidade lógica a testar — só a classe de bootstrap.
- **Integration Tests**: 1 teste — `FinancialControlApplicationTests.contextLoads()`. Sobe o
  contexto Spring completo com `@ActiveProfiles("test")` contra um PostgreSQL real efêmero
  (Testcontainers). É um smoke test de valor real: valida que o wiring do Spring, o datasource e a
  configuração de JPA estão coerentes.
- **Medição de cobertura**: **Não configurada.** Nem JaCoCo nem Kover estão no `build.gradle.kts` —
  não há como quantificar cobertura hoje.
- **Testes de contrato / carga / e2e**: Nenhum. Não existem source sets separados para eles.

## Code Quality Indicators

- **Linting**: **Não configurado.** Ausentes: ktlint, detekt, Spotless. Não há verificação
  automatizada de estilo ou análise estática no build.
- **Code Style**: **Consistente.** `gradle.properties` define `kotlin.code.style=official`. Os 3
  arquivos Kotlin seguem convenções idiomáticas: expression bodies onde cabe
  (`fun postgresContainer(): PostgreSQLContainer<*> = ...`), imports explícitos sem wildcards,
  ausência de código morto. A base é pequena demais para que a consistência seja significativa,
  mas nada destoa.
- **Documentation**: **Boa para o estágio.** O `README.md` é completo e preciso — cobre stack com
  versões, pré-requisitos, comandos de execução, tabela de variáveis de ambiente e o próprio método
  AI-DLC. **Nenhum KDoc** no código, o que é aceitável dado que os 3 arquivos são autoexplicativos.
- **Configuração**: **Boa.** Configuração externalizada com defaults de desenvolvimento
  (`${DB_URL:...}`), `.env.example` versionado como template e `.env` no `.gitignore`.
  Perfil `test` separado.

## Technical Debt

Itens em ordem de impacto sobre as próximas fases:

### 🔴 Alto — `ddl-auto: validate` sem ferramenta de migration
- **Localização**: `src/main/resources/application.yml:15-16`; ausência de Flyway/Liquibase no
  `build.gradle.kts`.
- **Descrição**: O perfil default valida o schema contra as entidades JPA na inicialização, mas
  nada cria esse schema — não há migrations, nem `schema.sql`. Hoje a aplicação sobe porque não
  existem entidades. **Assim que a primeira `@Entity` for criada, `bootRun` falhará no startup.**
- **Impacto**: Bloqueante para a Construction. Exige decisão explícita (Flyway, Liquibase ou outra
  abordagem) antes da geração de código do domínio.

### 🟠 Médio — `gradle-wrapper.jar` não versionado
- **Localização**: `gradle/wrapper/` contém apenas o `.properties`; o `.gitignore` tem a exceção
  `!gradle/wrapper/gradle-wrapper.jar`, mas o JAR nunca foi adicionado.
- **Descrição**: `./gradlew` não funciona em um clone limpo. O README contorna instruindo
  `gradle wrapper --gradle-version 8.14.2`, o que exige um Gradle pré-instalado e quebra o
  propósito do wrapper.
- **Impacto**: Atrito no onboarding; impede um pipeline de CI que dependa de `./gradlew`.

### 🟠 Médio — Ausência total de segurança
- **Localização**: `build.gradle.kts` (sem `spring-boot-starter-security`); `application.yml:31-32`.
- **Descrição**: Todos os endpoints são públicos e anônimos. Consequência secundária:
  `management.endpoint.health.show-details: when-authorized` é inefetivo — sem noção de usuário
  autenticado, os detalhes nunca aparecem, então a configuração transmite uma intenção que o
  sistema não pode cumprir.
- **Impacto**: Aceitável hoje (só há health check público). Torna-se crítico quando dados
  financeiros pessoais forem persistidos. Deve ser endereçado na NFR Requirements.

### 🟡 Baixo — Credenciais de desenvolvimento em texto plano
- **Localização**: `application.yml:7-8` (`financial`/`financial`), `docker-compose.yml:8-9`,
  `.env.example`.
- **Descrição**: São defaults de desenvolvimento local, sobrescrevíveis por variável de ambiente —
  padrão aceito na comunidade. Não há segredo de produção vazado no repositório.
- **Impacto**: Baixo, mas exige disciplina de que produção **sempre** use variáveis de ambiente ou
  um gerenciador de segredos.

### 🟡 Baixo — Ausência de CI/CD
- **Localização**: diretório `.github/` inexistente.
- **Descrição**: Build e testes rodam apenas localmente; nada impede que código quebrado seja
  commitado.
- **Impacto**: Baixo em projeto individual, crescente conforme o domínio se desenvolve.

### 🟡 Baixo — Sem lock nem varredura de dependências
- **Localização**: `build.gradle.kts` — sem `dependencyLocking`, sem `gradle.lockfile`, sem
  Dependency-Check/Snyk/Dependabot.
- **Descrição**: Builds não são reproduzíveis ao longo do tempo e vulnerabilidades conhecidas não
  são detectadas automaticamente.
- **Impacto**: Baixo hoje (poucas dependências, todas de fornecedores confiáveis).

### 🟡 Baixo — Observabilidade mínima
- **Localização**: `application.yml:25-32`.
- **Descrição**: Apenas `health` e `info`. Sem registry Micrometer, sem tracing, sem logging
  estruturado, sem `/actuator/metrics` exposto.
- **Impacto**: Irrelevante para desenvolvimento local; relevante se houver deploy.

## Patterns and Anti-patterns

### Good Patterns

- **Testcontainers com `@ServiceConnection`** (`TestcontainersConfiguration.kt`) — testa contra
  PostgreSQL real em vez de H2, eliminando divergência de dialeto entre teste e produção. É a
  abordagem recomendada pelo Spring Boot 3.1+, e o uso aqui está correto (`@TestConfiguration` com
  `proxyBeanMethods = false`).
- **`open-in-view: false`** (`application.yml:16`) — desabilita explicitamente o anti-pattern
  Open Session In View, que o Spring Boot habilita por default. Decisão deliberada e correta;
  evita lazy loading acidental na camada de apresentação.
- **`jdbc.time_zone: UTC`** (`application.yml:19-20`) — normaliza timestamps em UTC. Decisão
  especialmente acertada para um sistema financeiro, onde datas de vencimento de fatura e
  competência de parcelas são sensíveis a fuso.
- **Configuração externalizada com defaults** — `${DB_URL:jdbc:postgresql://...}` funciona out of
  the box em desenvolvimento e é sobrescrevível em qualquer ambiente. `.env.example` versionado,
  `.env` ignorado.
- **Escopos de dependência corretos** — `runtimeOnly` para o driver JDBC, `testRuntimeOnly` para o
  `junit-platform-launcher`. Mantém o classpath de compilação enxuto.
- **Versões delegadas ao BOM** — nenhuma versão hardcoded para dependências gerenciadas pelo Spring
  Boot; evita conflitos transitivos.
- **Java Toolchain (JDK 21)** — garante build consistente independentemente do JDK instalado
  localmente.
- **`-Xjsr305=strict`** — faz o compilador Kotlin tratar anotações de nulidade Java como estritas,
  reduzindo `NullPointerException` na fronteira com bibliotecas Java.
- **`plugin.jpa` (no-arg) já configurado** — preparação correta para entidades JPA futuras.

### Anti-patterns

- **`ddl-auto: validate` sem migration** — detalhado acima como débito de alto impacto. É a
  combinação errada: ou se adota uma ferramenta de migration (correto), ou `ddl-auto` cria o schema
  (aceitável só em desenvolvimento). Estar em `validate` sem nenhuma das duas resulta em uma
  aplicação que não sobe.
- **`show-details: when-authorized` sem mecanismo de autorização** — configuração cuja precondição
  não existe no sistema. Não causa falha, mas expressa uma intenção não realizável e pode induzir
  a crer que há proteção onde não há.
- **Dependência declarada sem consumidor** — `spring-boot-starter-validation` está no classpath
  sem uso. Neste caso é preparação deliberada para o domínio futuro, não desleixo; registrado
  apenas para rastreio.

### Ausências estruturais (não são anti-patterns, mas decisões pendentes)

- **Sem arquitetura em camadas definida** — não há separação `controller` / `service` /
  `repository` / `domain`. A estrutura de pacotes é uma decisão em aberto para a Application Design.
- **Sem tratamento global de exceções** — não existe `@ControllerAdvice`; o formato de resposta de
  erro da API está indefinido.
- **Sem contrato de API** — sem OpenAPI/springdoc.

## Resumo

| Dimensão | Avaliação |
|---|---|
| Qualidade do que existe | **Boa** — decisões técnicas deliberadas e bem justificadas (UTC, `open-in-view: false`, Testcontainers, escopos de dependência) |
| Completude funcional | **Nenhuma** — por decisão de projeto |
| Cobertura de testes | **Adequada ao estágio** (1 smoke test de integração), sem medição configurada |
| Automação de qualidade | **Ausente** — sem lint, sem cobertura, sem CI |
| Débito bloqueante | **1 item**: `ddl-auto: validate` sem ferramenta de migration |

O repositório é uma base sólida e enxuta. O único item que **precisa** de decisão antes da geração
de código do domínio é a estratégia de migration de schema.
