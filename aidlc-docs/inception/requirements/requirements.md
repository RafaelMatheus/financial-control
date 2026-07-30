# Requirements — financial-control

**Stage**: INCEPTION - Requirements Analysis
**Timestamp**: 2026-07-30T16:11:59Z
**Depth**: Comprehensive
**Fonte das respostas**: `requirement-verification-questions.md` (17 perguntas respondidas)

---

## 1. Intent Analysis

| Dimensão | Avaliação |
|---|---|
| **User Request** | *"Gostaria de construir um sistema de controle de gastos financeiros que me permita cadastrar gastos e parcelas de cartão de crédito"* — ampliado durante o esclarecimento para incluir multi-usuário, categorias, receitas, orçamento e **compartilhamento de gastos numa casa** |
| **Request Type** | **New Project** (domínio de negócio construído do zero sobre um esqueleto Spring Boot existente) |
| **Request Clarity** | **Incompleto na origem** → resolvido para **Claro** após 17 perguntas de esclarecimento |
| **Initial Scope Estimate** | **System-wide** — todo o modelo de domínio, camada de persistência, camada de API e autenticação |
| **Initial Complexity Estimate** | **Complex** — 7+ agregados de domínio, regras de rateio configurável, cálculo de competência de fatura a partir do ciclo do cartão, aritmética monetária com resíduo, e um modelo de autorização com dois eixos (usuário e casa) |
| **Depth Justification** | **Comprehensive**. O escopo cresceu significativamente durante o esclarecimento: o pedido original ("cadastrar gastos e parcelas") tornou-se um sistema multi-usuário com compartilhamento de despesas domésticas. Dinheiro, rateio entre pessoas e isolamento de dados são áreas onde ambiguidade custa caro. |

### Evolução do escopo durante a análise

O pedido inicial sugeria um app pessoal simples. O esclarecimento revelou três ampliações
materiais:

1. **Multi-usuário com isolamento de dados** (não era mencionado no pedido original)
2. **Compartilhamento de gastos numa casa, com rateio configurável** (requisito novo, trazido pelo
   usuário na resposta à Question 2) — este é o requisito de maior complexidade do sistema
3. **Receitas e orçamento por categoria** (além de gastos)

O front-end, por outro lado, foi **removido** do escopo deste repositório.

---

## 2. Escopo

### 2.1 Dentro do escopo

- API REST (JSON) cobrindo todo o domínio de controle financeiro
- Autenticação e autorização de usuários
- Casas (grupos domésticos) com múltiplos membros
- Gastos pessoais e compartilhados, com rateio configurável
- Receitas
- Categorias de gastos
- Orçamento mensal por categoria
- Cartões de crédito (pessoais ou da casa) com ciclo de fechamento/vencimento
- Compras parceladas com geração automática de parcelas
- Consolidação de faturas mensais com marcação de pagamento
- Schema de banco versionado via Flyway
- Testes automatizados (exemplo + property-based parcial)
- **Infraestrutura como código (Terraform)** para deploy em **AWS EC2**, no diretório
  `infra/terraform/` deste mesmo repositório

### 2.2 Fora do escopo

| Item | Justificativa |
|---|---|
| **Interface web / front-end** | Será implementada em **outro repositório**, consumindo esta API (resposta à Question 3) |
| **Limite do cartão de crédito** | Question 8 — usuário escolheu a modelagem sem limite |
| **Edição de parcela individual** | Question 11 — apenas a compra inteira é editável |
| **Integração bancária / Open Finance** | Não solicitado |
| **Importação de extrato ou fatura (OFX, CSV)** | Não solicitado |
| **App mobile nativo** | Não solicitado |
| **Multi-moeda** | Não solicitado; assume-se **BRL** (ver premissa P-01) |
| **Relatórios avançados / dashboards analíticos** | Não solicitado no MVP; consultas agregadas cobrem o essencial |
| **Acerto de contas entre membros ("quem deve a quem")** | Não solicitado. O sistema calcula as cotas de cada gasto (RF-19), mas **não** consolida saldos entre pessoas nem registra pagamentos entre membros — ver premissa P-06 |
| **Baseline de resiliência (HA, DR, RTO/RPO)** | Extensão desligada (Question 15). A arquitetura alvo é instância única — ver risco R-04 |
| **RDS ou qualquer banco gerenciado** | Decisão D-10 — PostgreSQL roda na própria instância EC2 |
| **Kubernetes, ECS, Fargate ou autoscaling** | Deploy alvo é uma instância EC2 única |
| **Checklist bloqueante de hardening de segurança** | Extensão desligada (Question 14) — **mas autenticação e isolamento permanecem como requisitos funcionais** (Question 17) |

---

## 3. Requisitos Funcionais

Numeração `RF-nn`. Prioridade: **M** (must), **S** (should), **C** (could).

### 3.1 Usuários e Autenticação

| ID | Prioridade | Requisito |
|---|---|---|
| RF-01 | M | O sistema deve permitir o cadastro de um usuário com identificação única (e-mail) e credencial de acesso. |
| RF-02 | M | O sistema deve autenticar o usuário antes de permitir qualquer operação sobre dados financeiros. |
| RF-03 | M | O sistema deve garantir que um usuário só acesse dados dos quais é proprietário ou aos quais tem visibilidade por pertencer a uma casa ou por compartilhamento avulso. |
| RF-04 | M | O sistema deve rejeitar, com resposta apropriada, qualquer tentativa de acesso a recurso de outro usuário fora das regras de visibilidade. |
| RF-05 | S | O sistema deve permitir que o usuário consulte e atualize seus próprios dados de perfil. |

> **Nota**: o *mecanismo* de autenticação (JWT stateless, sessão, OAuth2/OIDC) é uma decisão de
> stack, deliberadamente adiada para a stage **NFR Requirements** (ver Seção 8, decisão D-02).

### 3.2 Casa (grupo doméstico)

| ID | Prioridade | Requisito |
|---|---|---|
| RF-06 | M | O sistema deve permitir criar uma casa com nome identificador. |
| RF-07 | M | O sistema deve permitir que um usuário pertença a **zero, uma ou várias** casas. |
| RF-08 | M | O sistema deve permitir adicionar e remover membros de uma casa. |
| RF-09 | M | O sistema deve tornar visível a todos os membros de uma casa qualquer gasto marcado com escopo daquela casa. |
| RF-10 | S | O sistema deve permitir que um membro saia de uma casa, preservando o histórico dos gastos já lançados. |

### 3.3 Compartilhamento e Rateio

| ID | Prioridade | Requisito |
|---|---|---|
| RF-11 | M | O sistema deve permitir marcar um gasto com escopo **PESSOAL** (visível apenas ao autor) ou **CASA** (visível a todos os membros da casa indicada). |
| RF-12 | M | O sistema deve permitir, adicionalmente, o **compartilhamento avulso** de um gasto com usuários específicos que não pertencem à casa (Question 5 — opção "Ambos"). |
| RF-13 | M | O sistema deve dividir o valor de um gasto compartilhado entre os participantes, com **divisão igual como padrão**. |
| RF-14 | M | O sistema deve permitir sobrescrever a divisão padrão de um gasto, por **percentual** ou por **valor absoluto**, individualmente por participante. |
| RF-15 | M | O sistema deve garantir que a soma das cotas de um gasto compartilhado seja **exatamente igual** ao valor total do gasto — invariante de integridade monetária. |
| RF-16 | M | O sistema deve permitir que **qualquer membro da casa** edite ou exclua um gasto de escopo CASA daquela casa (Question 7). |
| RF-17 | M | O sistema deve registrar quem lançou cada gasto (autoria), mesmo que outros membros possam editá-lo. |

### 3.4 Gastos

| ID | Prioridade | Requisito |
|---|---|---|
| RF-18 | M | O sistema deve permitir cadastrar um gasto com, no mínimo: descrição, valor, data e categoria. |
| RF-19 | M | O sistema deve permitir associar um gasto a uma forma de pagamento — à vista ou em cartão de crédito. |
| RF-20 | M | O sistema deve permitir editar e excluir gastos, respeitando as regras de permissão (RF-16). |
| RF-21 | M | O sistema deve permitir consultar gastos por período, com filtros por categoria, casa e escopo. |
| RF-22 | S | O sistema deve totalizar os gastos consultados, no total e por categoria. |

### 3.5 Cartões de Crédito

| ID | Prioridade | Requisito |
|---|---|---|
| RF-23 | M | O sistema deve permitir cadastrar cartões de crédito com apelido, **dia de fechamento** e **dia de vencimento** (Question 8). |
| RF-24 | M | O sistema deve permitir que um cartão pertença a um **usuário** ou a uma **casa**; a fatura de um cartão da casa é visível a todos os seus membros (Question 12). |
| RF-25 | M | O sistema deve determinar automaticamente em qual **fatura de competência** cada compra ou parcela cai, a partir da data da compra e do dia de fechamento do cartão. |
| RF-26 | M | O sistema deve consolidar a fatura mensal de um cartão, listando todos os lançamentos e parcelas que nela incidem, com o valor total. |
| RF-27 | M | O sistema deve permitir marcar uma fatura como **paga**, registrando a data do pagamento. |
| RF-28 | S | O sistema deve permitir consultar faturas futuras, projetando as parcelas já comprometidas. |

### 3.6 Compras Parceladas

| ID | Prioridade | Requisito |
|---|---|---|
| RF-29 | M | O sistema deve permitir lançar uma compra parcelada informando **valor da parcela** e **número de parcelas**, calculando o valor total (Question 4). |
| RF-30 | M | O sistema deve gerar automaticamente as N parcelas da compra, cada uma com sua competência de fatura, a partir da data da compra e do ciclo do cartão. |
| RF-31 | M | Quando o valor total não dividir exatamente pelo número de parcelas, o sistema deve aplicar a convenção brasileira: as **primeiras N-1 parcelas** recebem o valor informado e a **última parcela absorve** o resíduo (decisão AI-DLC — Question 10 respondida como "sem preferência"). |
| RF-32 | M | O sistema deve garantir a invariante **soma das parcelas = valor total da compra**, em toda operação de criação ou edição. |
| RF-33 | M | O sistema deve permitir editar uma compra parcelada apenas **por inteiro**, recalculando todas as parcelas; parcelas individuais não são editáveis (Question 11). |
| RF-34 | M | O sistema deve excluir todas as parcelas de uma compra ao excluir a compra. |
| RF-35 | S | O sistema deve identificar cada parcela por sua posição (ex.: "3/12"). |

### 3.7 Categorias

| ID | Prioridade | Requisito |
|---|---|---|
| RF-36 | M | O sistema deve permitir cadastrar, editar e excluir categorias de gastos. |
| RF-37 | S | O sistema deve impedir a exclusão de uma categoria que possua gastos vinculados, ou oferecer realocação. |
| RF-38 | C | O sistema deve prover um conjunto inicial de categorias comuns no primeiro acesso. |

### 3.8 Receitas

| ID | Prioridade | Requisito |
|---|---|---|
| RF-39 | M | O sistema deve permitir cadastrar receitas com descrição, valor e data. |
| RF-40 | M | O sistema deve permitir consultar receitas por período. |
| RF-41 | S | O sistema deve apresentar o balanço do período (receitas − gastos). |

### 3.9 Orçamento por Categoria

| ID | Prioridade | Requisito |
|---|---|---|
| RF-42 | M | O sistema deve permitir definir um teto de orçamento mensal por categoria. |
| RF-43 | M | O sistema deve comparar o orçado com o realizado do mês, por categoria. |
| RF-44 | S | O sistema deve sinalizar categorias que ultrapassaram o teto orçado. |

### 3.10 Infraestrutura e Deploy

| ID | Prioridade | Requisito |
|---|---|---|
| RF-45 | M | A infraestrutura de execução deve ser descrita como código em **Terraform**, versionada em `infra/terraform/` **neste mesmo repositório**. |
| RF-46 | M | A infraestrutura deve provisionar a aplicação em uma instância **AWS EC2**. |
| RF-47 | M | O **PostgreSQL deve rodar na própria instância EC2** (container Docker), não em serviço gerenciado. |
| RF-48 | M | A aplicação deve ser empacotada em imagem Docker (hoje não existe `Dockerfile` no repositório). |
| RF-49 | M | O Terraform deve provisionar os recursos de rede e acesso necessários: VPC/subnet, security group e chave/role de acesso à instância. |
| RF-50 | M | Os dados do PostgreSQL devem residir em **volume EBS separado** do volume raiz da instância, para sobreviver à substituição da instância. |
| RF-51 | M | O estado do Terraform deve ser **remoto** (backend S3), com lock, e nunca versionado no repositório. |
| RF-52 | M | A configuração deve ser parametrizada por ambiente (ex.: `dev`, `prod`), sem duplicação de módulos. |
| RF-53 | M | Credenciais de banco e demais segredos devem ser injetados por variável de ambiente ou parameter store — **nunca** hardcoded no Terraform nem versionados. |
| RF-54 | S | Deve existir rotina de **backup do PostgreSQL** (dump periódico para S3, ou snapshot do volume EBS) com procedimento de restauração documentado. |

---

## 4. Requisitos Não-Funcionais

| ID | Categoria | Requisito |
|---|---|---|
| RNF-01 | Integridade monetária | Valores monetários devem usar aritmética decimal exata (`BigDecimal` com escala 2), **nunca** ponto flutuante. Toda operação de divisão deve ter política de arredondamento explícita. |
| RNF-02 | Integridade transacional | Operações que criam múltiplos registros relacionados (compra + N parcelas; gasto + cotas de rateio) devem ser atômicas. |
| RNF-03 | Data/hora | Timestamps persistidos em **UTC** (já configurado: `hibernate.jdbc.time_zone: UTC`). Datas de negócio (competência, vencimento) são datas civis sem fuso. |
| RNF-04 | Versionamento de schema | O schema deve ser criado e versionado via **Flyway**, com `ddl-auto: validate` mantido para detectar divergência entre entidades e schema (Question 13). |
| RNF-05 | Isolamento de dados | Toda consulta deve ser escopada ao usuário autenticado e às suas visibilidades. Não deve existir endpoint que retorne dados sem esse filtro. |
| RNF-06 | Testabilidade | Testes de integração devem rodar contra PostgreSQL real via Testcontainers (padrão já estabelecido no repositório). |
| RNF-07 | Property-based testing | Aplicar PBT em modo **Parcial** (regras PBT-02, PBT-03, PBT-07, PBT-08, PBT-09). Framework: **Kotest Property Testing** (Kotlin — PBT-09). Alvos naturais: divisão de parcelas (RF-31/RF-32) e rateio de gastos (RF-15). |
| RNF-08 | Contrato de API | A API é consumida por um front-end web **em outro repositório**. O contrato precisa ser estável, versionado e documentado (OpenAPI). |
| RNF-09 | Tratamento de erros | Respostas de erro devem seguir formato consistente, com código e mensagem acionáveis. |
| RNF-10 | Validação de entrada | Toda entrada da API deve ser validada (`spring-boot-starter-validation` já está no classpath, hoje sem uso). |
| RNF-11 | Manutenibilidade | Separação clara de camadas (API / aplicação / domínio / persistência), a ser definida na Application Design. |
| RNF-12 | Escala | Uso pessoal/doméstico: dezenas de usuários, milhares de lançamentos. **Não** há requisito de alta escala, alta disponibilidade ou baixa latência agressiva. |
| RNF-13 | Reprodutibilidade da infra | Toda a infraestrutura deve ser recriável a partir do Terraform versionado, sem passos manuais no console AWS. |
| RNF-14 | Isolamento de CI | Com app e IaC no mesmo repositório, o pipeline deve usar **filtro de path** — `terraform plan` não deve rodar em mudanças que tocam apenas código Kotlin, e vice-versa. |
| RNF-15 | Segredos | `*.tfstate`, `*.tfvars` com valores sensíveis e arquivos `.env` não podem ser versionados. O `.gitignore` deve ser estendido para cobri-los. |
| RNF-16 | Superfície de exposição | O security group deve expor apenas as portas necessárias. A porta do PostgreSQL (5432) **não** deve ser acessível pela internet — apenas localmente na instância. |
| RNF-17 | Durabilidade dos dados | Como o PostgreSQL roda no próprio EC2 (sem backup gerenciado), a persistência depende de volume EBS separado (RF-50) e de rotina de backup própria (RF-54). Ver risco R-01. |

---

## 5. Cenários de Usuário Principais

**C-01 — Lançar um gasto compartilhado da casa com rateio desigual**
Rafael faz uma compra de mercado de R$ 400. Ele lança o gasto com escopo CASA (Apartamento 42),
categoria "Alimentação". O sistema propõe divisão igual (R$ 200 / R$ 200). Rafael ajusta para
70% / 30%, resultando em R$ 280 para ele e R$ 120 para Ana. Ana, ao consultar seus gastos do
período, enxerga o lançamento e sua cota de R$ 120.

**C-02 — Lançar uma compra parcelada no cartão**
Rafael compra um notebook em 12x de R$ 100 no cartão Nubank (fechamento dia 28, vencimento dia 5),
em 30/07/2026. O sistema calcula o total de R$ 1.200, gera 12 parcelas e, como a compra ocorreu
após o fechamento, aloca a parcela 1/12 na fatura de **setembro/2026**, e as demais nos meses
subsequentes.

**C-03 — Conferir e pagar a fatura**
Rafael consulta a fatura de agosto/2026 do Nubank. O sistema lista todos os lançamentos e parcelas
com competência naquela fatura, apresenta o total e permite marcá-la como paga com a data do
pagamento.

**C-04 — Compartilhamento avulso fora da casa**
Rafael paga R$ 900 de uma viagem e compartilha o gasto com João e Maria, que não são membros da
casa. O sistema divide o valor entre os três e torna o lançamento visível a eles, sem envolver Ana.

**C-05 — Acompanhar o orçamento**
Rafael define um teto de R$ 1.500/mês para "Alimentação". Ao consultar o orçamento de agosto, vê o
realizado de R$ 1.720 e a categoria sinalizada como estourada.

**C-06 — Correção de uma compra parcelada**
Rafael percebe que lançou o notebook em 12x de R$ 100 quando o correto era 10x de R$ 120. Ele edita
a compra; o sistema descarta as 12 parcelas anteriores e regenera 10 parcelas, mantendo a
invariante soma = R$ 1.200.

### Cenários de borda e erro identificados

| # | Cenário | Tratamento esperado |
|---|---|---|
| E-01 | Compra parcelada com resíduo de centavos (R$ 100 em 3x) | RF-31: última parcela absorve |
| E-02 | Rateio configurado cuja soma difere do total do gasto | RF-15: rejeitar a operação |
| E-03 | Compra no exato dia de fechamento do cartão | Regra de fronteira a definir na Functional Design (ver decisão D-04) |
| E-04 | Cartão com fechamento no dia 31 e mês com 30 dias | Regra de fronteira a definir na Functional Design |
| E-05 | Membro sai da casa com gastos compartilhados em aberto | RF-10: preservar histórico |
| E-06 | Exclusão de categoria com gastos vinculados | RF-37: bloquear ou realocar |
| E-07 | Compartilhamento avulso com usuário inexistente | Rejeitar com erro de validação |
| E-08 | Usuário tenta acessar fatura de cartão de outra casa | RF-04: negar acesso |

---

## 6. Configuração de Extensões

| Extensão | Habilitada | Modo | Decidido em |
|---|---|---|---|
| `security/baseline` | **Não** | — | Requirements Analysis (Question 14) |
| `resiliency/baseline` | **Não** | — | Requirements Analysis (Question 15) |
| `testing/property-based` | **Sim** | **Parcial** (PBT-02, PBT-03, PBT-07, PBT-08, PBT-09 bloqueantes; demais advisory) | Requirements Analysis (Question 16) |

> **Ressalva importante sobre a extensão Security**: desligá-la remove o *checklist bloqueante de
> hardening* das stages do AI-DLC. **Não** remove autenticação, isolamento de dados ou permissões
> de casa — confirmado pelo usuário na Question 17, esses permanecem como requisitos funcionais
> de primeira classe (RF-01 a RF-05, RF-16, RF-24).

---

## 7. Premissas

| ID | Premissa | Impacto se incorreta |
|---|---|---|
| P-01 | Moeda única: **BRL**. Sem conversão nem multi-moeda. | Alto — mudaria o modelo de valores monetários |
| P-02 | Fuso horário de negócio: **America/Sao_Paulo**, com persistência em UTC. | Médio — afetaria cálculo de competência de fatura |
| P-03 | Volume: dezenas de usuários, milhares de lançamentos. Sem requisito de escala. | Baixo — afetaria decisões de índice e paginação |
| P-04 | ~~Deploy: nenhuma decisão tomada.~~ **Revisada**: deploy em **AWS EC2**, IaC em Terraform no mesmo repositório, PostgreSQL na própria instância. Ver Seção 8 (D-08 a D-10) e Seção 8.1 (riscos). | — (decidido) |
| P-05 | Receitas são individuais, **não** compartilháveis entre membros da casa. | Médio — o usuário pediu compartilhamento explicitamente para *gastos* |
| P-06 | O sistema calcula as cotas de cada gasto, mas **não** consolida "quem deve a quem" nem registra acertos entre membros. | Médio — é a extensão natural do rateio; fora do escopo declarado |
| P-07 | Um gasto pago com cartão de crédito da casa também pode ter rateio próprio, independente da propriedade do cartão. | Médio — afeta o modelo de domínio |

---

## 8. Decisões Técnicas e Pontos em Aberto

| ID | Item | Status |
|---|---|---|
| D-01 | **Flyway** como ferramenta de migration, mantendo `ddl-auto: validate` | ✅ Decidido (Question 13). Resolve o débito bloqueante apontado na engenharia reversa |
| D-02 | Mecanismo de autenticação (JWT stateless / sessão / OAuth2-OIDC) | ⏳ Adiado para **NFR Requirements** (seleção de stack) |
| D-03 | Estrutura de pacotes e separação de camadas | ⏳ Adiado para **Application Design** |
| D-04 | Regra de fronteira do fechamento de fatura (compra no exato dia do fechamento; fechamento dia 29–31 em meses curtos) | ⏳ Adiado para **Functional Design** |
| D-05 | Framework PBT: **Kotest Property Testing** (recomendação PBT-09 para Kotlin) | ✅ Pré-decidido; confirmar em NFR Requirements |
| D-06 | Documentação de API: springdoc-openapi | ⏳ Adiado para **NFR Requirements** — necessário por RNF-08 |
| D-07 | Modelagem de "participante" de um gasto compartilhado (membro da casa vs. usuário avulso — entidade única ou distinta) | ⏳ Adiado para **Application Design** |
| D-08 | **Terraform no mesmo repositório**, em `infra/terraform/` | ✅ Decidido. Critério: um único serviço consome a infra, um único mantenedor, infra pequena. App e IaC mudam no mesmo PR, sem sincronização entre repositórios. Repo separado passaria a valer com múltiplos serviços na mesma infra ou separação real de permissões de deploy |
| D-09 | **IaC dentro deste ciclo AI-DLC** — a stage Infrastructure Design será executada | ✅ Decidido |
| D-10 | **PostgreSQL na própria instância EC2** (container Docker), não em RDS | ✅ Decidido. Menor custo e menor complexidade; em contrapartida, backup e recuperação passam a ser responsabilidade própria — ver risco R-01 |
| D-11 | Dimensionamento da instância EC2, AMI, região e detalhes de rede | ⏳ Adiado para **Infrastructure Design** |
| D-12 | Mecanismo de deploy da aplicação no EC2 (user-data, systemd + docker compose, ou pipeline) | ⏳ Adiado para **Infrastructure Design** |

### Estrutura de diretórios acordada

```
financial-control/
+-- src/                          # aplicacao Kotlin
+-- infra/
|   +-- terraform/
|       +-- main.tf
|       +-- variables.tf
|       +-- outputs.tf
|       +-- modules/              # vpc, ec2, security-group, ebs
|       +-- envs/
|           +-- dev/              # tfvars + backend
|           +-- prod/             # tfvars + backend
+-- Dockerfile                    # a criar (RF-48)
+-- build.gradle.kts
```

---

## 8.1 Riscos

| ID | Risco | Severidade | Mitigação acordada |
|---|---|---|---|
| R-01 | **PostgreSQL no EC2 sem backup gerenciado**: são dados financeiros pessoais de múltiplos usuários. Sem RDS, não há snapshot automático, patching gerenciado nem restauração point-in-time. Perda da instância ou corrupção do volume implica perda dos dados. | **Alta** | Decisão consciente do usuário (D-10), motivada por custo e simplicidade. Mitigada por RF-50 (volume EBS separado do volume raiz) e RF-54 (rotina de backup com procedimento de restauração documentado). **RF-54 deve ser tratado como obrigatório na prática, apesar da prioridade "S"** — a Infrastructure Design deve detalhar a rotina antes de qualquer deploy com dados reais. |
| R-02 | **Extensão Security desligada com deploy em cloud pública**: o sistema deixa de ter o checklist bloqueante de hardening justamente ao ganhar exposição à internet. | **Média** | Autenticação e isolamento permanecem como requisitos funcionais (RF-01 a RF-05). RNF-16 restringe a superfície do security group. Registrado para reavaliação: a extensão pode ser reativada a qualquer momento antes da Construction. |
| R-03 | **App e IaC no mesmo repositório sem filtro de CI**: mudanças de código Kotlin disparando `terraform plan` (ou o contrário) geram ruído e risco de apply indevido. | **Baixa** | RNF-14 exige filtro de path no pipeline. |
| R-04 | **Instância única sem redundância**: qualquer falha da EC2 derruba aplicação e banco simultaneamente. | **Baixa** | Aceito. Coerente com RNF-12 (uso doméstico, sem requisito de disponibilidade) e com o opt-out da extensão de resiliência. |

---

## 9. Critérios de Aceitação do Ciclo

O ciclo será considerado bem-sucedido quando:

1. Um usuário conseguir se autenticar e enxergar apenas os próprios dados e os que lhe são
   compartilhados
2. Uma compra parcelada lançada por valor de parcela gerar N parcelas com soma exatamente igual ao
   total
3. Cada parcela cair na fatura correta segundo o ciclo de fechamento do cartão
4. Um gasto de casa aparecer para todos os membros com as cotas corretas, e a soma das cotas
   igualar o valor do gasto
5. A fatura mensal consolidar corretamente os lançamentos e puder ser marcada como paga
6. O schema for criado por migrations Flyway versionadas, com `ddl-auto: validate` passando
7. Os testes rodarem contra PostgreSQL via Testcontainers, incluindo PBT para divisão de parcelas e
   rateio
8. A infraestrutura (EC2 + PostgreSQL em container + volume EBS + security group) for provisionável
   a partir do Terraform versionado em `infra/terraform/`, sem passos manuais no console AWS

---

## 10. Rastreabilidade

| Requisito | Origem |
|---|---|
| RF-01 a RF-05 | Question 1 (multi-usuário) + Question 17 (auth como requisito funcional) |
| RF-06 a RF-10 | Question 2 (texto livre do usuário sobre casa) + Question 5 |
| RF-11 a RF-17 | Questions 5, 6, 7 |
| RF-18 a RF-22 | Pedido original + Question 2 |
| RF-23 a RF-28 | Questions 8, 9, 12 |
| RF-29 a RF-35 | Pedido original + Questions 4, 10, 11 |
| RF-36 a RF-38 | Question 2 (opção A) |
| RF-39 a RF-41 | Question 2 (opção C) |
| RF-42 a RF-44 | Question 2 (opção D) |
| RNF-04 | Question 13 + achado bloqueante da engenharia reversa |
| RNF-07 | Question 16 + `extensions/testing/property-based/property-based-testing.md` |
| RNF-08 | Question 3 (front-end em outro repositório) |
| RNF-03, RNF-06 | Práticas já estabelecidas no repositório (engenharia reversa) |
| RF-45 a RF-54 | Rodada de esclarecimento sobre infraestrutura (deploy em AWS EC2, Terraform no mesmo repo, PostgreSQL na instância) |
| RNF-13 a RNF-17 | Mesma rodada — decorrências não-funcionais das decisões D-08, D-09 e D-10 |
| R-01 a R-04 | Análise de risco das decisões de infraestrutura |
