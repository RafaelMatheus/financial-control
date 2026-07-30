# AI-DLC State Tracking

## Project Information
- **Project Name**: financial-control
- **Project Type**: Brownfield (esqueleto executável sem domínio de negócio)
- **Start Date**: 2026-07-30T16:11:59Z
- **Current Phase**: INCEPTION
- **Current Stage**: Requirements Analysis (aguardando aprovação do usuário)

## Workspace State
- **Existing Code**: Yes
- **Programming Languages**: Kotlin 2.1.21 (JVM 21)
- **Build System**: Gradle 8.14.2 (Kotlin DSL)
- **Project Structure**: Monolito Spring Boot (single module)
- **Reverse Engineering Needed**: Yes (nenhum artefato existente)
- **Workspace Root**: /Users/rafaelmatheuspereiradecastro/IdeaProjects/financial-control

## Code Location Rules
- **Application Code**: Workspace root (NEVER in aidlc-docs/)
- **Documentation**: aidlc-docs/ only
- **Structure patterns**: See code-generation.md Critical Rules

## Extension Configuration

| Extension | Enabled | Enforcement Mode | Decided At |
|---|---|---|---|
| security/baseline | **No** | — | Requirements Analysis (Question 14) |
| resiliency/baseline | **No** | — | Requirements Analysis (Question 15) |
| testing/property-based | **Yes** | **Partial** | Requirements Analysis (Question 16) |

**Property-Based Testing — modo Parcial**: apenas as regras **PBT-02** (round-trip), **PBT-03**
(invariantes), **PBT-07** (qualidade de geradores), **PBT-08** (shrinking e reprodutibilidade) e
**PBT-09** (seleção de framework) são bloqueantes. As demais (PBT-01, PBT-04, PBT-05, PBT-06,
PBT-10) são advisory (não-bloqueantes). Regras carregadas de
`.aidlc-rule-details/extensions/testing/property-based/property-based-testing.md`.

**Ressalva sobre a extensão Security desligada**: remove apenas o checklist bloqueante de hardening
das stages. **Não** remove autenticação, isolamento de dados por usuário nem permissões de casa —
confirmado pelo usuário na Question 17, permanecem como requisitos funcionais (RF-01 a RF-05,
RF-16, RF-24).

## Stage Progress

### INCEPTION PHASE
- [x] Workspace Detection — COMPLETED (2026-07-30T16:11:59Z)
- [x] Reverse Engineering — COMPLETED e APROVADO (2026-07-30T16:11:59Z)
- [x] Requirements Analysis — ARTEFATOS GERADOS (2026-07-30T16:11:59Z), aguardando aprovação
- [ ] User Stories — PENDING (avaliação condicional)
- [ ] Workflow Planning — PENDING
- [ ] Application Design — PENDING (avaliação condicional)
- [ ] Units Generation — PENDING (avaliação condicional)

### CONSTRUCTION PHASE
- [ ] Per-Unit Loop — PENDING
- [ ] Build and Test — PENDING

### OPERATIONS PHASE
- [ ] Operations — PLACEHOLDER

## Reverse Engineering Status
- [x] Reverse Engineering — Artefatos gerados em 2026-07-30T16:11:59Z (commit analisado: f1d7060)
- **Artifacts Location**: `aidlc-docs/inception/reverse-engineering/`
- **Aprovação do usuário**: ✅ APROVADO em 2026-07-30T16:11:59Z

### Achado bloqueante para a Construction — RESOLVIDO
`spring.jpa.hibernate.ddl-auto: validate` (perfil default) sem ferramenta de migration
(Flyway/Liquibase ausentes do classpath). A aplicação deixaria de inicializar assim que a primeira
`@Entity` fosse criada.
**Resolução**: adotado **Flyway** (Requirements Analysis, Question 13). `ddl-auto` permanece em
`validate` para detectar divergência entre entidades e schema. Registrado como RNF-04 e D-01.

## Requirements Analysis Status
- [x] Requirements Analysis — Artefatos gerados em 2026-07-30T16:11:59Z
- **Artifacts Location**: `aidlc-docs/inception/requirements/`
  - `requirement-verification-questions.md` (17 perguntas respondidas + análise de contradições)
  - `requirements.md` (44 requisitos funcionais, 12 não-funcionais, 6 cenários, 8 casos de borda)
- **Depth**: Comprehensive
- **Aprovação do usuário**: PENDENTE

### Ampliação de escopo detectada
O pedido original ("cadastrar gastos e parcelas de cartão de crédito") foi ampliado durante o
esclarecimento para: multi-usuário com autenticação, casas (grupos domésticos) com compartilhamento
de gastos e rateio configurável, receitas, categorias e orçamento por categoria. O front-end web
foi **excluído** deste repositório (será implementado separadamente, consumindo a API).

### Decisões de infraestrutura (rodada adicional de esclarecimento)
| ID | Decisão | Status |
|---|---|---|
| D-08 | Terraform **no mesmo repositório**, em `infra/terraform/` | ✅ Decidido |
| D-09 | IaC **dentro deste ciclo** — stage Infrastructure Design será executada | ✅ Decidido |
| D-10 | **PostgreSQL na própria instância EC2** (container Docker), sem RDS | ✅ Decidido |

Alvo de deploy: **AWS EC2**, instância única. Adicionados requisitos RF-45 a RF-54 (infraestrutura)
e RNF-13 a RNF-17. Registrados 4 riscos (R-01 a R-04) na Seção 8.1 de `requirements.md`.

**Risco R-01 (Alta severidade)**: PostgreSQL no EC2 sem backup gerenciado, com dados financeiros de
múltiplos usuários. Mitigação obrigatória antes de qualquer deploy com dados reais: volume EBS
separado do volume raiz (RF-50) e rotina de backup com procedimento de restauração documentado
(RF-54).

### Decisões adiadas (a resolver em stages posteriores)
| ID | Item | Stage alvo |
|---|---|---|
| D-02 | Mecanismo de autenticação (JWT / sessão / OAuth2-OIDC) | NFR Requirements |
| D-03 | Estrutura de pacotes e separação de camadas | Application Design |
| D-04 | Regra de fronteira do fechamento de fatura | Functional Design |
| D-06 | springdoc-openapi para documentação de API | NFR Requirements |
| D-07 | Modelagem de participante de gasto compartilhado | Application Design |
| D-11 | Dimensionamento da EC2, AMI, região e detalhes de rede | Infrastructure Design |
| D-12 | Mecanismo de deploy da aplicação no EC2 | Infrastructure Design |
