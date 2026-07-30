# AI-DLC State Tracking

## Project Information
- **Project Name**: financial-control
- **Project Type**: Brownfield (esqueleto executável sem domínio de negócio)
- **Start Date**: 2026-07-30T16:11:59Z
- **Current Phase**: INCEPTION
- **Current Stage**: Application Design (artefatos gerados, aguardando aprovação)

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
- [x] Requirements Analysis — COMPLETED e APROVADO na revisão 6 (2026-07-30T16:11:59Z)
- [x] User Stories — COMPLETED e APROVADO (2026-07-30T16:11:59Z)
- [x] Workflow Planning — COMPLETED e APROVADO (2026-07-30T16:11:59Z)
- [x] Application Design — ARTEFATOS GERADOS (2026-07-30T16:11:59Z), aguardando aprovação
- [ ] Units Generation — **EXECUTE**

### CONSTRUCTION PHASE
- [ ] Functional Design — **EXECUTE** (por unidade)
- [ ] NFR Requirements — **EXECUTE** (primeira unidade apenas)
- [ ] NFR Design — **EXECUTE** (por unidade)
- [ ] Infrastructure Design — **EXECUTE** (unidade de infraestrutura apenas)
- [ ] Code Generation — **EXECUTE** (sempre, por unidade)
- [ ] Build and Test — **EXECUTE** (sempre, ao final)

### OPERATIONS PHASE
- [ ] Operations — PLACEHOLDER

## Application Design Status
- [x] Artefatos gerados em 2026-07-30T16:11:59Z
- **Artifacts Location**: `aidlc-docs/inception/application-design/`
  - `components.md` — 11 componentes de feature + common, 15 entidades, 12 agregados
  - `component-methods.md` — assinaturas por componente
  - `services.md` — camada de serviço, orquestrações e fronteiras transacionais
  - `component-dependency.md` — matriz, grafo, fluxos e ordem de implementação
  - `openapi.yaml` — **OpenAPI 3.1 validado**: 31 paths, 51 operações, 39 schemas
  - `application-design.md` — consolidação
- **Aprovação do usuário**: PENDENTE

### Decisões fechadas
| ID | Decisão |
|---|---|
| D-03 | Estrutura de pacotes **por feature** |
| D-29 | **Mesmo modelo** para escrita e leitura |
| D-30 | Cada item traz o dono; resposta traz `totalPessoal` e `totalGrupo` |
| D-31 | Fatura é **entidade persistida** |
| D-32 | Identificadores **UUID** |

**J-03 resolvida**; **J-01 extinta** pela revisão 8. **RF-78 a RF-80 atendidos** — o contrato
OpenAPI está entregue e utilizável pelo front, exceto o `securityScheme`, provisório até D-02.

### Achado: divergência com o plano de execução
O grafo de dependências mostra que `gasto` depende de `cartao` e `fatura`, mas o plano previa
U2 (Lançamentos) antes de U3 (Crédito). Sugestão para a Units Generation: dividir `gasto` em gasto
à vista (U2) e gasto em cartão (U3), ou antecipar `cartao`/`fatura`.

### Nova decisão em aberto
**D-33** — `Fatura.status = PAGA` é persistido ou derivado da `ContaAPagar` vinculada? Surgiu ao
modelar a fatura como entidade persistida (D-31). Recomendação: derivar. Functional Design decide.

## Execution Plan Summary
- **Artifact**: `aidlc-docs/inception/plans/execution-plan.md`
- **Stages a executar**: TODAS as condicionais — Application Design, Units Generation, Functional
  Design, NFR Requirements, NFR Design, Infrastructure Design, Code Generation, Build and Test
- **Stages puladas**: nenhuma
- **Nível de risco**: Médio (complexidade alta, impacto do erro contido — sem produção, sem dados
  reais, sem integração externa)
- **Rollback**: fácil até o primeiro `terraform apply` com dados reais
- **Complexidade de teste**: complexa (invariantes monetárias, PBT, Testcontainers)
- **Unidades previstas**: 5 (U1 Fundação, U2 Lançamentos, U3 Crédito, U4 Planejamento,
  U5 Infraestrutura) — decomposição definitiva sai na Units Generation
- **Núcleo mínimo**: U1 + U2 + U3
- **Gates de aprovação restantes**: ~20
- **Aprovação do plano**: ✅ APROVADO em 2026-07-30T16:11:59Z

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
  - `requirement-verification-questions.md` (17 perguntas respondidas + análise de contradições + revisões pós-gate)
  - `requirements.md` — **revisão 6**: 92 RF ativos (RF-01 a RF-93, RF-12 removido), 17 RNF,
    10 cenários, 16 casos de borda, 11 premissas, 26 decisões, 5 riscos
  - `bootstrap-runbook.md` (esboço — versão executável sai na Infrastructure Design)
- **Depth**: Comprehensive
- **Aprovação do usuário**: ✅ APROVADO na revisão 6 (2026-07-30T16:11:59Z) — *"pode partir para o próximo passo"*

### Histórico de revisões dos requisitos
| Rev. | RF ativos | Mudança |
|---|---|---|
| 1 | 44 | Versão inicial (17 perguntas de esclarecimento) |
| 2 | 54 | Infraestrutura: AWS EC2 + Terraform no mesmo repo + PostgreSQL na instância |
| 3 | 53 | "Casa" generalizada para "Grupo"; RF-12 (compartilhamento avulso) removido |
| 4 | 76 | Contas a pagar (RF-55 a RF-67) e Investimentos (RF-68 a RF-77) |
| 5 | 79 | Contrato de API como entregável: OpenAPI 3.1 YAML após a Application Design (RF-78 a RF-80) |
| 6 | 92 | CI/CD e provisionamento: GitHub Actions com OIDC, ECR, deploy por SSM, bootstrap manual (RF-81 a RF-93) |

## User Stories Status
- [x] Parte 1 — Planejamento: assessment + plano aprovado
- [x] Parte 2 — Geração: personas.md e stories.md
- **Artifacts Location**:
  - `aidlc-docs/inception/plans/user-stories-assessment.md`
  - `aidlc-docs/inception/plans/story-generation-plan.md`
  - `aidlc-docs/inception/user-stories/personas.md`
  - `aidlc-docs/inception/user-stories/stories.md`
- **Conteúdo**: 11 épicos, 57 histórias ativas, 3 jornadas transversais, 1 persona com 4 contextos,
  rastreabilidade 68/68 (revisão 8 — H-10, H-11 e H-12 removidas)
- **Núcleo mínimo**: 28 histórias marcadas
- **Aprovação do usuário**: ✅ APROVADO em 2026-07-30T16:11:59Z

### Decisões fechadas pelas User Stories
| ID | Decisão | Origem |
|---|---|---|
| D-04 | Compra no dia exato do fechamento vai para a **fatura seguinte** (corte exclusivo). Resta só o caso de fechamento em dia 29–31 (E-04) | E-03 |
| D-13 | Membro que entra num grupo enxerga **todo o histórico**; visibilidade desacoplada do rateio | E-10 |

Novos requisitos derivados: **RF-94** (desmarcar pagamento), **RF-95** (bloquear alteração em fatura
paga), **RF-96** (reabrir e recalcular fatura fechada não paga). Requisitos ativos: **95**.

### Pontos levantados pelas jornadas transversais
| # | Questão | Destino |
|---|---|---|
| 1 | O "realizado" do orçamento conta pela data da compra ou pela competência da fatura? (J-02) | ⏳ Functional Design |
| 2 | ~~Rateio incide sobre cada parcela~~ (J-01) | ✅ **Extinto** na rev. 8 — não há rateio |
| 3 | API distinguir "total do grupo" de "total pessoal" (J-03) | ✅ **Resolvido** na rev. 8 por RF-97 e D-28 |

### Revisão 8 — Rateio removido (D-27, D-28)
Esclarecimento do usuário durante a Application Design: *"Não vamos compartilhar contas do nível de
dividir gastos, só dividir as contas de uma casa... conta x o owner é minha esposa, conta y o owner
sou eu, mas todos dois conseguem ver suas contas de uma casa caso sejam membros do mesmo grupo"*.

O compartilhamento passa a ser **apenas de visibilidade**. Cada lançamento tem um **dono** e o valor
é integralmente dele. Ninguém deve nada a ninguém no sistema.

**Removidos**: RF-13 (divisão igual), RF-14 (divisão configurável), RF-15 (invariante das cotas),
histórias H-10, H-11 e H-12, caso de borda E-02, entidade `Cota` do modelo.
**Adicionado**: RF-97 — total pessoal e total do grupo são grandezas distintas, nunca somadas.
**Impacto no PBT**: dos três alvos de property-based testing, restam dois (H-28 e H-29, ambos de
parcelamento). O parcelamento passa a ser a única área com aritmética monetária de divisão.

### Documento de registro de pesquisa
`aidlc-docs/research-log.md` — registro cronológico e analítico do processo (decisões, alternativas
descartadas, dados quantitativos e observações metodológicas O-01 a O-10). Destina-se a servir de
base para um artigo científico ao final do ciclo. **Deve ser alimentado a cada alteração
relevante**, antes de considerar a tarefa concluída.

### Revisão pós-gate — "Casa" generalizada para "Grupo"
O usuário solicitou mudanças no gate de aprovação: o conceito **Casa** (grupo doméstico) foi
generalizado para **Grupo** — coleção nomeada de usuários que compartilham gastos (casa, república,
casal, viagem etc.). Participação é opcional (zero, um ou vários grupos por usuário) e um grupo tem
N membros. A cardinalidade já estava especificada; RF-07 e RF-08 foram reforçados para torná-la
explícita. Renomeação aplicada em RF-03, RF-06 a RF-12, RF-16, RF-21, RF-24, cenários C-01/C-04,
casos de borda E-05/E-08, premissas P-05/P-07 e decisão D-07.

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
| D-06 | ~~springdoc-openapi~~ **Decidido**: contrato OpenAPI 3.1 YAML entregue após a Application Design. Só a ferramenta de geração no backend segue em aberto | NFR Requirements |
| D-07 | Modelagem de participante de gasto compartilhado | Application Design |
| D-11 | Dimensionamento da EC2, AMI, região e detalhes de rede | Infrastructure Design |
| D-12 | ~~Mecanismo de deploy da aplicação no EC2~~ **Parcialmente resolvido** por D-24 (SSM Run Command). Restam os detalhes de execução | Infrastructure Design |

### Lacuna de provisionamento do método — RESOLVIDA por CI/CD
O AI-DLC **entrega o Terraform escrito, mas não provisiona**: a fase de Operations é um placeholder
vazio (`operations/operations.md`: *"The AI-DLC workflow currently ends after the Build and Test
phase in CONSTRUCTION"*). Lacuna fechada com **GitHub Actions** — o `terraform apply` roda no CI a
partir do merge em `main`.

| ID | Decisão | Status |
|---|---|---|
| D-21 | **GitHub Actions** como plataforma de CI/CD | ✅ Decidido |
| D-22 | Autenticação AWS por **OIDC**, sem credencial de longa duração | ✅ Decidido |
| D-23 | **Amazon ECR** como registry da imagem | ✅ Decidido |
| D-24 | Deploy via **SSM Run Command**; porta 22 fechada | ✅ Decidido |
| D-25 | `terraform apply` **automático no merge**, sem gate manual | ✅ Decidido — ver risco R-05 |
| D-26 | **Bootstrap manual e único** para o state remoto e a role OIDC | ✅ Decidido |

**Risco R-05 (Alta severidade)**: `apply` automático sem aprovação, combinado com PostgreSQL sem
backup gerenciado (R-01) e volume EBS sob o mesmo Terraform. Um `replace` no recurso do volume,
aprovado num PR lido às pressas, destruiria os dados financeiros sem ponto de recuperação.
Mitigações a detalhar na Infrastructure Design: `prevent_destroy` nos recursos com estado, plan
visível no PR (RF-84) e backup (RF-54) como pré-requisito de merge em `infra/**`.
