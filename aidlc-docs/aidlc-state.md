# AI-DLC State Tracking

---

# 🔖 RETOMAR AQUI

## ✅ U1 — FUNDAÇÃO: CÓDIGO ENTREGUE E VERDE NO CI — 2026-08-01

As 4 stages de U1 executadas. Code Generation com os **28 passos concluídos** e a suíte completa
verde no CI — run `30713102231`, commit `cd310cb`, **69 testes**, incluindo os de integração com
Testcontainers que não rodam nesta máquina.

| Stage de U1 | Situação |
|---|---|
| Functional Design | ✅ Aprovada |
| NFR Requirements | ✅ Aprovada |
| NFR Design | ✅ Aprovada |
| Code Generation | ✅ Código gerado, CI verde — **gate de aprovação pendente** |

**Três defeitos encontrados pelo CI e corrigidos** (`cd310cb`), todos invisíveis sem banco real:

| # | Sintoma | Causa | Correção |
|---|---|---|---|
| 1 | Cadastros simultâneos: `[201, 500, 500…]` em vez de `409` | `jpa.save()` só envia o `INSERT` no commit, depois de o `try/catch` sair de cena | `saveAndFlush` no adaptador de `usuario` e no de `grupo` |
| 2 | `"  Ana@Exemplo.COM  "` dava `400` em vez de `409` | `@Email` roda antes da normalização e rejeita os espaços que RN-U01 manda remover | `@Email` fora do DTO; o domínio valida depois de normalizar |
| 3 | `NPE` nos testes após o de bloqueio | `RegistroDeTentativas` tem estado em memória; `TRUNCATE` não o alcança | `limparTudo()` no `SuporteDeIntegracao` |

**Próximo passo**: aprovar o gate de Code Generation de U1 e seguir para **U2 — Lançamentos**
(`categoria`, `gasto` à vista).

---

## 🗄️ AMBIENTE `dev` PROVISIONADO — 2026-07-31

Bootstrap e stack principal aplicados pelo CI. **U5 entregue na prática.**

| Saída | Valor |
|---|---|
| `api_url` | `http://52.73.89.203` (sem TLS — `domain_name` vazio em dev) |
| `instance_id` | `i-0151f919886de23ca` |
| `db_endpoint` | `financial-control-dev-db.cmjo0eeoyqhw.us-east-1.rds.amazonaws.com` |
| state | `s3://financial-control-tfstate-594116288641/` (`bootstrap/` e `dev/`) |

**Pendências imediatas**:

1. **Passo 5b do runbook** — criar o usuário `financial_app` no banco, por SQL via SSM. O Terraform
   não alcança o RDS em subnet privada
2. **Gate de aprovação do Code Generation de U5** — nunca foi dado
3. **Decisão pendente: retenção de backup em `prod`** — ver abaixo

## ⚠️ Risco R-01 reaberto para `prod`

A conta está no plano **Free Tier**, que recusou retenção de 7 dias com `FreeTierRestrictionError`.
Em `dev` a retenção caiu para **1 dia**, o que é inconsequente sem dado real.

Em `prod` isso reabre o R-01, declarado **fechado** na revisão 9 justamente porque o RDS gerenciado
traria backup de 7 dias. A diferença entre 1 e 7 dias é a diferença entre perder um dia de
lançamentos e perder uma semana. Duas saídas: subir o plano da conta, ou aceitar formalmente a
retenção menor e reabrir o R-01 no `requirements.md`.

## Correções aplicadas durante o provisionamento

| Sintoma | Causa | Correção |
|---|---|---|
| `Could not load credentials` | `role-session-name` no lugar de `role-to-assume` | smoke test corrigido |
| `EntityAlreadyExists` (previsto) | provider OIDC criado à mão | `import` blocks |
| `S3 bucket does not exist` | filtro de path engolia o bootstrap | exclusão `!.../bootstrap/**` |
| `non-printable control characters` | travessão e apóstrofo em `description` | texto ASCII |
| `FreeTierRestrictionError` | plano da conta | retenção 1 dia em dev |
| `Cannot find version 16.6` | versão menor fixada e fora do catálogo | só a maior (`"16"`) |

---

# 🗄️ Contexto anterior

**Última sessão**: 2026-07-30 · **Commit**: `e5a5a3c`

## Onde paramos

Fase de **Inception concluída** (7 stages, nenhuma pulada). Fase de **Construction** iniciada pela
unidade **U5 — Infraestrutura**:

| Stage | Situação |
|---|---|
| Infrastructure Design | ✅ Aprovada |
| Code Generation | ✅ Código gerado e no GitHub — **gate de aprovação ainda pendente** |
| Bootstrap manual | 🔴 **Em andamento, bloqueado** |

## ✅ OIDC RESOLVIDO em 2026-07-31

O smoke test ficou verde. Causa raiz: o sample da AWS trazia o ARN em `role-session-name` em vez de
`role-to-assume` — nenhuma role era assumida. A role em uso é `github-actions`, criada manualmente
no console, com **`AdministratorAccess`** anexado.

**Estado atual do bootstrap**: metade feita à mão (OIDC provider e role). Faltam o **bucket de state**
e o **repositório ECR**. O `oidc.tf` ganhou dois `import` blocks para adotar os recursos manuais no
state em vez de tentar criar duplicatas.

**Próximo passo concreto**: `terraform plan` no CloudShell e avaliar o diff da trust policy antes do
apply — decisão do usuário de confiar no plan em vez de comparar antes.

⚠️ **`AdministratorAccess` na role do CI amplifica o risco R-05** (apply automático sem gate).
Qualquer push na `main` que toque `infra/terraform/**` tem poder total sobre a conta.

## 🗄️ Histórico do bloqueio (resolvido)

O GitHub Actions falha ao assumir a role por OIDC:

```
Error: Could not assume role with OIDC: The web identity token provided
could not be validated.
```

**Já feito**: as 3 variables do repositório estão configuradas; a role parece existir (o Actions
tenta assumi-la 12 vezes antes de desistir).

**Correção já aplicada e commitada** (`e5a5a3c`): o `oidc.tf` fixava o thumbprint
`6938fd4d98bab03faadb97b34396831e3780aea1` — valor antigo, muito copiado de tutoriais. Passou a ler
o certificado atual via `data.tls_certificate`, mantendo os históricos na lista.

**Segunda correção, também aguardando o mesmo apply**: a permission policy da role do CI não
concedia nenhuma ação de `rds`. Foi escrita sob D-10 (Postgres em container) e não foi revisitada
quando D-37 adotou o RDS gerenciado — o bootstrap é outro stack, com outro state. O apply do CI
falharia ao criar `aws_db_instance`. Adicionados `rds:*`, `iam:CreateServiceLinkedRole`,
`kms:DescribeKey` e `kms:CreateGrant`.

## ⚠️ Pendência aberta pelo experimento de depuração

Os commits `0f2a224` e `f8ed6f0` (ambos com mensagem "teste") **apagaram os 4 workflows do projeto**
e puseram no lugar o `main.yml`, reprodutor mínimo do OIDC. Consequência: **o `main` está sem
pipeline algum**.

- Restaurar com `git checkout 8726974 -- .github/workflows/` — **só depois** do smoke test passar,
  para não reintroduzir ruído no diagnóstico
- O `main.yml` é temporário e sai quando o pipeline voltar
- Os 4 workflows originais estão corretos: declaram `permissions: id-token: write` e usam
  `configure-aws-credentials@v4` com a audience padrão `sts.amazonaws.com`

**Defeito encontrado no reprodutor** (já corrigido): o sample da AWS trazia o ARN em
`role-session-name` em vez de `role-to-assume`, então nenhuma role era assumida. O erro
`Could not load credentials from any providers` vinha daí — falha *anterior* ao OIDC, não o bloqueio
original. Também corrigidos `aws-region: east-1` → `us-east-1` e o step de S3 com placeholders.

**Role em uso no reprodutor**: `arn:aws:iam::594116288641:role/github-actions`, criada **manualmente
no console** pelo usuário. É diferente da role do Terraform (`financial-control-github-actions`) —
ver a nota de reconciliação abaixo.

**Ainda não verificado** — duas hipóteses em aberto:

1. **Thumbprint desatualizado** — resolvido pelo commit, mas o `terraform apply` ainda não foi
   reexecutado com a correção
2. **Capitalização do owner no `sub`** — a trust policy exige
   `repo:RafaelMatheus/financial-control:ref:refs/heads/main`. O `sub` do token usa a grafia exata
   que o GitHub guarda. Se o owner for `rafaelmatheus` minúsculo ou outra variação, nunca casa

## Reconciliação pendente entre o console e o Terraform

Recursos criados à mão no console **colidem com o bootstrap** no próximo `terraform apply`. O OIDC
provider é único por conta: se já existe, o apply falha com `EntityAlreadyExists`. A role manual
`github-actions` não colide (o Terraform cria `financial-control-github-actions`), mas vira recurso
órfão fora do state.

Antes do apply, importar o que já existe:

```bash
terraform import aws_iam_openid_connect_provider.github \
  arn:aws:iam::594116288641:oidc-provider/token.actions.githubusercontent.com
```

Se o provider manual foi criado com o thumbprint antigo copiado de tutorial, o import traz o defeito
junto — o apply seguinte corrige, porque o `oidc.tf` agora lê o certificado via `data.tls_certificate`.

## Próximos passos, na ordem

**1. Diagnóstico** — CloudShell na conta 594116288641:

```bash
aws iam get-role --role-name financial-control-github-actions \
  --query 'Role.AssumeRolePolicyDocument.Statement[0].Condition'
```

- Se responder: a role existe; comparar o `sub` com a grafia real do repositório
- Se der `NoSuchEntity`: o apply do bootstrap não chegou a criar

**2. Reaplicar o bootstrap com a correção**:

```bash
cd ~/financial-control && git pull
cd infra/terraform/bootstrap
terraform init -upgrade    # -upgrade: foi adicionado o provider tls
terraform apply
terraform output
```

**3.** Re-run all jobs na aba Actions do GitHub

**4.** Com o pipeline verde, aprovar o gate de Code Generation de U5

**5.** Seguir para **U1 — Fundação** (`common`, `usuario`, `grupo`)

## Comando de segurança — sempre antes de qualquer apply

```bash
aws sts get-caller-identity   # tem que responder 594116288641
```

A CLI local da máquina do usuário está autenticada em **490490484770** (`user/mt-clix`), conta
diferente. Usar CloudShell na conta correta, ou `AWS_PROFILE=pessoal`.

## Também pendente

- **`domain_name`** — sem ele não há TLS; a API responderia por HTTP no IP elástico
- **Passo 5b do runbook** — criar o usuário `financial_app` no banco, por SQL via SSM, depois que o
  RDS existir

---

## Project Information
- **Project Name**: financial-control
- **Project Type**: Brownfield (esqueleto executável sem domínio de negócio)
- **Start Date**: 2026-07-30T16:11:59Z
- **Current Phase**: CONSTRUCTION
- **Current Stage**: CONSTRUCTION — **U1 Fundação · Code Generation concluída, gate pendente**

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
- [x] Application Design — COMPLETED e APROVADO (2026-07-30T16:11:59Z)
- [x] Units Generation — COMPLETED e APROVADO (2026-07-30T16:11:59Z)

✅ **FASE DE INCEPTION ENCERRADA** — 7 stages executadas, nenhuma pulada.

### CONSTRUCTION PHASE

**Ordem de execução das unidades**: U5 → U1 → U2 → U3 → U4

#### U5 — Infraestrutura (EM ANDAMENTO)
- [ ] Functional Design — **SKIP** (sem lógica de negócio nem modelo de dados)
- [ ] NFR Requirements — **ADIADA para U1** (resolve D-02, D-05 e D-06 — decisões de stack da aplicação)
- [ ] NFR Design — **SKIP** (consequência da anterior)
- [x] Infrastructure Design — COMPLETED e APROVADO (2026-07-30T16:11:59Z)
- [x] Code Generation — CÓDIGO GERADO (2026-07-30T16:11:59Z), aguardando aprovação
      38 arquivos (36 criados, 2 modificados). `src/` não tocado.
      Resumo: `aidlc-docs/construction/u5-infraestrutura/code/code-summary.md`

**Achado bloqueante corrigido durante a geração**: `gradlew` e `gradle-wrapper.jar` não existem no
repositório — débito da engenharia reversa que quebraria o CI e o build da imagem. Corrigido
removendo a dependência do wrapper: `Dockerfile` usa a imagem oficial do Gradle; `ci-app.yml`
instala o Gradle 8.14.2 explicitamente.

**Decisões de U5**: D-11 (`us-east-1` · `t3.small`), D-12 (deploy por SSM sobre docker compose),
D-34 (VPC própria, sem NAT), D-35 (nginx + Let's Encrypt), **D-37 (RDS PostgreSQL gerenciado)**,
D-38 (2 subnets privadas), D-39 (database e usuário dedicados). Custo estimado ~US$ 35/mês.

✅ **Risco R-01 RESOLVIDO** na revisão 9. A migração para RDS gerenciado traz backup automático com
7 dias de retenção, point-in-time recovery, patching gerenciado e snapshot final ao destruir. D-36
(backup fora do escopo) e RF-50 (volume EBS) ficaram sem objeto. **RF-54 atendido nativamente.**

**Conta AWS**: 594116288641 (`rmpcastr`). Valores já preenchidos em `envs/*`. ⚠️ A CLI local estava
configurada para `490490484770` — confirmar com `aws sts get-caller-identity` antes de qualquer apply.

**Insumo pendente**: `domain_name` — sem ele não há TLS; a API responde por HTTP no IP elástico.

#### U1 — Fundação (CÓDIGO ENTREGUE — gate pendente)
- [x] Functional Design — COMPLETED e APROVADO (2026-07-31T16:20:00Z)
      3 artefatos · 6 decisões (D-42 a D-47) · 21 regras de negócio · 5 diagramas Mermaid
- [x] NFR Requirements — COMPLETED e APROVADO (2026-07-31T16:40:00Z)
      14 NFRs · **D-02 fechada** (JWT stateless, 24h, sem refresh) · D-05, D-06, D-48 a D-50
- [x] NFR Design — COMPLETED e APROVADO (2026-07-31T17:00:00Z)
      D-51 (hexagonal) · D-52 (Visibilidade por porta sem método cru) · D-53 (log com correlação)
- [x] Code Generation — **28/28 passos · CI VERDE · APROVADO** (2026-08-01T19:10:00Z)
      Plano: `aidlc-docs/construction/plans/u1-fundacao-code-generation-plan.md`
      Resumo: `aidlc-docs/construction/u1-fundacao/code/code-summary.md`
      Commits: `9cf27a1` · `f3c2aef` · `f3910fc` · `cd310cb`
      Suíte: **69 testes**, run `30713102231`. Primeira execução no CI reprovou 3 — §7 do plano

**Decisões fechadas em U1**: D-02 (JWT stateless 24h), D-05 (Kotest Property), D-06 (springdoc gera
do código), D-42 (autenticação própria com `senhaHash`), D-43 (`Dinheiro` HALF_UP escala 2), D-44
(ex-membro sofre corte total de visibilidade), D-45 (reentrada cria nova linha de `MembroGrupo`),
D-46 (e-mail normalizado), D-47 (grupo vazio permitido), D-48 (BCrypt força 12), D-49 (bloqueio de
5 tentativas por 15 min, contador em memória), D-50 (validade 24h), D-51 (hexagonal), D-52 (porta
sem método cru), D-53 (log em texto com id de correlação).

**Padrão central da unidade**: `RepositorioComVisibilidade` não expõe `findAll` nem `findById`.
Consulta sem filtro de visibilidade não vira bug — vira erro de compilação.

**Dívida conhecida, a revisitar se houver escala horizontal**: `RegistroDeTentativas` guarda estado
em memória. É o único componente com estado da unidade, e o CI confirmou a propriedade em teste.

#### U2 — Lançamentos
- [ ] Functional Design · NFR Design · Code Generation

#### U3 — Crédito
- [ ] Functional Design · NFR Design · Code Generation

#### U4 — Planejamento
- [ ] Functional Design · NFR Design · Code Generation

#### Fechamento
- [ ] Build and Test — **EXECUTE** (sempre, ao final)

### OPERATIONS PHASE
- [ ] Operations — PLACEHOLDER

## Units Generation Status
- [x] Parte 1 — Planejamento (5 questões respondidas; 3 defaults adotados e comunicados)
- [x] Parte 2 — Geração
- **Artifacts Location**: `aidlc-docs/inception/application-design/`
  - `unit-of-work.md` · `unit-of-work-dependency.md` · `unit-of-work-story-map.md`
- **Aprovação do usuário**: ✅ APROVADO em 2026-07-30T16:11:59Z

### Decomposição definitiva — 5 unidades

| Unidade | Componentes | Histórias | Depende de |
|---|---|---|---|
| **U1 — Fundação** | `common`, `usuario`, `grupo` | 8 | — |
| **U2 — Lançamentos** | `categoria`, `gasto` (à vista) | 9 | U1 |
| **U3 — Crédito** | `cartao`, `fatura`, `conta`, `compra`, `gasto` (cartão) | 25 + 2 jornadas | U1, U2 |
| **U4 — Planejamento** | `receita`, `orcamento`, `investimento` | 15 + 1 jornada | U1, U2, U3 (só J-02) |
| **U5 — Infraestrutura** | Terraform, Dockerfile, GitHub Actions | 0 | — (**paralelizável**) |

**Cobertura**: 57 histórias + 3 jornadas = 60, todas atribuídas. Nenhuma duplicada, nenhuma órfã.

**Caminho crítico**: U1 → U2 → U3. **Sequência recomendada**: U5 primeiro (CI verde desde o
início), depois U1 → U2 → U3 → U4.

### Divergência resolvida
O componente `gasto` depende de `cartao` e `fatura`, contradizendo a ordem U2-antes-de-U3 do plano.
**Resolução**: dividir `gasto` — à vista em U2, integração com cartão em U3. A entidade nasce em U2
já com `cartaoId` e `competencia` **nuláveis**, evitando `ALTER TABLE` em U3.

### Escopo do ciclo
**Todas as 5 unidades** entram neste ciclo AI-DLC.

## Application Design Status
- [x] Artefatos gerados em 2026-07-30T16:11:59Z
- **Artifacts Location**: `aidlc-docs/inception/application-design/`
  - `components.md` — 11 componentes de feature + common, 15 entidades, 12 agregados
  - `component-methods.md` — assinaturas por componente
  - `services.md` — camada de serviço, orquestrações e fronteiras transacionais
  - `component-dependency.md` — matriz, grafo, fluxos e ordem de implementação
  - `openapi.yaml` — **OpenAPI 3.1 validado**: 31 paths, 51 operações, 39 schemas
  - `application-design.md` — consolidação
- **Aprovação do usuário**: ✅ APROVADO em 2026-07-30T16:11:59Z

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
