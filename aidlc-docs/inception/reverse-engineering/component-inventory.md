# Component Inventory

> **Contexto**: `financial-control` é um projeto Gradle **single-module**
> (`settings.gradle.kts` contém apenas `rootProject.name`, sem nenhum `include(...)`). Não existem
> subprojetos. O inventário abaixo trata o módulo raiz como o único "pacote" de build, e detalha os
> source sets internos.

## Application Packages

- **`financial-control` (módulo raiz Gradle)** — Aplicação Spring Boot monolítica. Único artefato
  de build do repositório.
  - **Source set `main`** — Contém a classe de bootstrap e a configuração de runtime.
    - Pacote Kotlin: `com.rafaelmatheus.financialcontrol`
    - Arquivos: `FinancialControlApplication.kt`, `application.yml`
    - **Estado**: esqueleto — nenhum código de negócio (sem controllers, services, repositories ou
      entidades).

## Infrastructure Packages

- **Nenhum.** O repositório não contém infraestrutura como código: sem CDK (`package.json` com
  dependências CDK), sem Terraform (`.tf`), sem CloudFormation, sem manifests Kubernetes, sem Helm
  charts.
- **Único artefato relacionado a infraestrutura**: `docker-compose.yml` na raiz — provisiona
  **apenas o PostgreSQL 16 para desenvolvimento local**. Não empacota nem implanta a aplicação
  (não existe `Dockerfile`).

## Shared Packages

- **Nenhum.** Não há módulos de modelos, utilitários ou clientes compartilhados. Sendo
  single-module, não existe compartilhamento entre projetos.

## Test Packages

- **Source set `test`** — Testes unitários e de integração do módulo raiz.
  - Pacote Kotlin: `com.rafaelmatheus.financialcontrol`
  - Arquivos: `FinancialControlApplicationTests.kt` (smoke test de contexto),
    `TestcontainersConfiguration.kt` (fixture PostgreSQL via Testcontainers),
    `application-test.yml` (perfil `test`, `ddl-auto: create-drop`)
  - **Tipo**: Integração leve (sobe o contexto Spring completo contra um PostgreSQL real efêmero).
  - **Estado**: 1 teste, cobrindo apenas a inicialização do contexto. Nenhum teste de negócio.
- **Não existem** source sets separados para testes de integração, carga, contrato ou end-to-end.

## Documentation & Method Packages

Não são artefatos de build, mas compõem o repositório:

- **`.aidlc-rule-details/`** — Regras detalhadas da metodologia AI-DLC (versão 1.0.1). 33 arquivos
  markdown distribuídos em `common/`, `inception/`, `construction/`, `operations/` e `extensions/`.
- **`CLAUDE.md`** — Workflow AI-DLC core, carregado automaticamente.
- **`README.md`** — Documentação de stack, execução e método.
- **`aidlc-docs/`** — Artefatos gerados por este ciclo AI-DLC (criado nesta sessão).

## Total Count

| Categoria | Quantidade |
|---|---|
| **Total Packages (módulos Gradle)** | **1** |
| Application | 1 (módulo raiz, source set `main`) |
| Infrastructure | 0 |
| Shared | 0 |
| Test | 0 módulos separados (1 source set `test` dentro do módulo raiz) |

### Contagem de arquivos

| Tipo | Quantidade |
|---|---|
| Arquivos-fonte Kotlin (`.kt`) | 3 (1 em `main`, 2 em `test`) |
| Arquivos de build Kotlin DSL (`.kts`) | 2 (`build.gradle.kts`, `settings.gradle.kts`) |
| Arquivos de configuração YAML | 3 (`application.yml`, `application-test.yml`, `docker-compose.yml`) |
| Arquivos de propriedades | 2 (`gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`) |
| **Total analisado (excluindo `.aidlc-rule-details/`, `build/`, `.gradle/`, `.git/`)** | **~14** |

### Observações

- Nenhum pipeline de CI/CD: o diretório `.github/` não existe.
- O `gradle-wrapper.jar` não está versionado neste commit inicial (o `.gitignore` tem a exceção
  `!gradle/wrapper/gradle-wrapper.jar`, mas o arquivo ainda não foi adicionado). O README instrui
  gerar o wrapper com `gradle wrapper --gradle-version 8.14.2` antes do primeiro build.
