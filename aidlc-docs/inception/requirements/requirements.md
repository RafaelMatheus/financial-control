# Requirements — financial-control

**Stage**: INCEPTION - Requirements Analysis
**Timestamp**: 2026-07-30T16:11:59Z
**Depth**: Comprehensive
**Fonte das respostas**: `requirement-verification-questions.md` (17 perguntas respondidas)

---

## 1. Intent Analysis

| Dimensão | Avaliação |
|---|---|
| **User Request** | *"Gostaria de construir um sistema de controle de gastos financeiros que me permita cadastrar gastos e parcelas de cartão de crédito"* — ampliado durante o esclarecimento para incluir multi-usuário, categorias, receitas, orçamento e **compartilhamento de gastos num grupo** |
| **Request Type** | **New Project** (domínio de negócio construído do zero sobre um esqueleto Spring Boot existente) |
| **Request Clarity** | **Incompleto na origem** → resolvido para **Claro** após 17 perguntas de esclarecimento |
| **Initial Scope Estimate** | **System-wide** — todo o modelo de domínio, camada de persistência, camada de API e autenticação |
| **Initial Complexity Estimate** | **Complex** — 10+ agregados de domínio, cálculo de competência de fatura a partir do ciclo do cartão, motor de recorrência de contas, aritmética monetária com resíduo no parcelamento, e um modelo de visibilidade com dois eixos (dono e grupo). *Reduzida na revisão 8 com a remoção do rateio* |
| **Depth Justification** | **Comprehensive**. O escopo cresceu significativamente durante o esclarecimento: o pedido original ("cadastrar gastos e parcelas") tornou-se um sistema multi-usuário com compartilhamento de despesas em grupo. Dinheiro e isolamento de dados são áreas onde ambiguidade custa caro. |

### Evolução do escopo durante a análise

O pedido inicial sugeria um app pessoal simples. O esclarecimento revelou três ampliações
materiais:

1. **Multi-usuário com isolamento de dados** (não era mencionado no pedido original)
2. **Compartilhamento de gastos num grupo** (requisito novo, trazido pelo usuário na resposta à
   Question 2). Inicialmente especificado com rateio configurável; **simplificado na revisão 8**
   para visibilidade apenas
3. **Receitas e orçamento por categoria** (além de gastos)

E, numa segunda rodada de revisão (após o primeiro gate de aprovação), mais duas:

4. **Contas a pagar com vencimento próprio** — fatura de cartão, PIX, boleto e fatura de serviço
   numa visão única de vencimentos, com recorrência opcional
5. **Objetivos de investimento** — aportes por objetivo nomeado, com meta, prazo alvo e saldo
   atualizável manualmente

O front-end, por outro lado, foi **removido** do escopo deste repositório.

### Histórico de revisões deste documento

| Rev. | Mudança | Origem |
|---|---|---|
| 1 | Versão inicial — 44 RF, 12 RNF | 17 perguntas de esclarecimento |
| 2 | Infraestrutura: deploy em AWS EC2, Terraform no mesmo repo, PostgreSQL na instância (RF-45 a RF-54, RNF-13 a RNF-17, riscos R-01 a R-04) | Pergunta do usuário sobre onde colocar o Terraform |
| 3 | "Casa" generalizada para **"Grupo"**; **RF-12 removido** (compartilhamento avulso) — modelo único de compartilhamento | Mudança solicitada no gate de aprovação |
| 4 | **Contas a pagar** (RF-55 a RF-67) e **Investimentos** (RF-68 a RF-77); fatura de cartão unificada à visão de vencimentos | Pedido do usuário durante a revisão |
| 5 | **Contrato de API** como entregável explícito (RF-78 a RF-80): OpenAPI 3.1 YAML, gerado após a Application Design | Pedido do usuário: documento de endpoints para construir o front |
| 6 | **CI/CD e provisionamento** (RF-81 a RF-93): GitHub Actions com OIDC, ECR, deploy por SSM e bootstrap manual documentado | Pergunta do usuário sobre o momento do provisionamento, que revelou a lacuna da fase de Operations do AI-DLC |
| 8 | **Rateio removido** (D-27): RF-13, RF-14 e RF-15 eliminados. O grupo passa a servir apenas para **visibilidade** — cada lançamento tem um dono e o valor é integralmente dele. RF-97 adicionado (dois totais distintos) | Esclarecimento do usuário durante a Application Design |
| 7 | **Casos de borda resolvidos** (E-03, E-10, E-12, E-13) com retropropagação para RF-09, RF-25 e três requisitos novos (RF-94 a RF-96). Fecha D-13 e resolve parcialmente D-04 | Decisões tomadas no planejamento das User Stories |

---

## 2. Escopo

### 2.1 Dentro do escopo

- API REST (JSON) cobrindo todo o domínio de controle financeiro
- Autenticação e autorização de usuários
- Grupos com N membros (ex.: casa, república, viagem, casal)
- Gastos pessoais e de grupo — compartilhamento por **visibilidade**, sem rateio
- Receitas
- Categorias de gastos
- Orçamento mensal por categoria
- Cartões de crédito (pessoais ou do grupo) com ciclo de fechamento/vencimento
- Compras parceladas com geração automática de parcelas
- Consolidação de faturas mensais com marcação de pagamento
- **Contas a pagar** (fatura de cartão, PIX, boleto, fatura de serviço) com vencimento próprio,
  recorrência opcional e visão consolidada de vencimentos
- **Objetivos de investimento** com aportes, meta, prazo alvo e saldo atualizável manualmente
- Schema de banco versionado via Flyway
- Testes automatizados (exemplo + property-based parcial)
- **Infraestrutura como código (Terraform)** para deploy em **AWS EC2**, no diretório
  `infra/terraform/` deste mesmo repositório
- **Pipeline CI/CD em GitHub Actions** — build e teste da aplicação, `terraform plan` em PR,
  `terraform apply` no merge, build e push da imagem para ECR, e deploy na EC2 via SSM

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
| **Carteira de investimentos (ativos, cotações, rentabilidade por papel)** | O usuário optou por aportes + saldo manual. Sem CDB/tesouro/ações individualizados, sem indexadores, sem cotação |
| **Resgate / retirada de objetivo de investimento** | Não marcado pelo usuário na seleção de atributos do objetivo — ver premissa P-08 |
| **Pagamento efetivo (integração com banco, geração de PIX ou boleto)** | O sistema registra que a conta foi paga; não executa o pagamento |
| **Rateio / divisão de valores entre membros** | Decisão D-27 (revisão 8). O grupo serve para **visibilidade**, não para dividir dinheiro. Cada lançamento tem um dono e o valor é integralmente dele |
| **Acerto de contas entre membros ("quem deve a quem")** | Consequência direta da ausência de rateio — não há saldo entre pessoas a consolidar |
| **Baseline de resiliência (HA, DR, RTO/RPO)** | Extensão desligada (Question 15). A arquitetura alvo é instância única — ver risco R-04 |
| **RDS ou qualquer banco gerenciado** | Decisão D-10 — PostgreSQL roda na própria instância EC2 |
| **Kubernetes, ECS, Fargate ou autoscaling** | Deploy alvo é uma instância EC2 única |
| **Execução do `terraform apply` pelo fluxo AI-DLC** | O método entrega o código; a aplicação da infra roda no GitHub Actions (RF-85). O bootstrap inicial é manual, com runbook (RF-92) |
| **Acesso SSH à instância** | Substituído por SSM Run Command; porta 22 fechada (RF-90) |
| **Checklist bloqueante de hardening de segurança** | Extensão desligada (Question 14) — **mas autenticação e isolamento permanecem como requisitos funcionais** (Question 17) |

---

## 3. Requisitos Funcionais

Numeração `RF-nn`. Prioridade: **M** (must), **S** (should), **C** (could).

### 3.1 Usuários e Autenticação

| ID | Prioridade | Requisito |
|---|---|---|
| RF-01 | M | O sistema deve permitir o cadastro de um usuário com identificação única (e-mail) e credencial de acesso. |
| RF-02 | M | O sistema deve autenticar o usuário antes de permitir qualquer operação sobre dados financeiros. |
| RF-03 | M | O sistema deve garantir que um usuário só acesse dados dos quais é proprietário ou aos quais tem visibilidade **por pertencer a um grupo**. |
| RF-04 | M | O sistema deve rejeitar, com resposta apropriada, qualquer tentativa de acesso a recurso de outro usuário fora das regras de visibilidade. |
| RF-05 | S | O sistema deve permitir que o usuário consulte e atualize seus próprios dados de perfil. |

> **Nota**: o *mecanismo* de autenticação (JWT stateless, sessão, OAuth2/OIDC) é uma decisão de
> stack, deliberadamente adiada para a stage **NFR Requirements** (ver Seção 8, decisão D-02).

### 3.2 Grupo

> **Nota de terminologia**: o conceito foi originalmente levantado como **"Casa"** (grupo
> doméstico) e depois **generalizado para "Grupo"** a pedido do usuário. Um grupo é uma coleção
> nomeada de usuários que compartilham gastos — pode representar uma casa, uma república, um casal,
> uma viagem ou qualquer outro arranjo. A generalização não altera a cardinalidade, que já estava
> especificada: um usuário pertence a **zero, um ou vários** grupos, e um grupo tem **N membros**.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-06 | M | O sistema deve permitir criar um grupo com nome identificador. |
| RF-07 | M | O sistema deve permitir que um usuário pertença a **zero, um ou vários** grupos — a participação em grupo é **opcional**. Um usuário sem nenhum grupo usa o sistema normalmente, apenas sem gastos compartilhados por grupo. |
| RF-08 | M | O sistema deve permitir adicionar e remover membros de um grupo, sem limite fixo de membros (**N pessoas**). |
| RF-09 | M | O sistema deve tornar visível a todos os membros de um grupo qualquer gasto marcado com escopo daquele grupo, **inclusive os lançados antes da entrada do membro no grupo**. O membro vê todo o histórico do grupo; como não há rateio, não há cota a atribuir — os lançamentos anteriores simplesmente pertencem a seus respectivos donos (E-10). |
| RF-10 | S | O sistema deve permitir que um membro saia de um grupo, preservando o histórico dos gastos já lançados. |

### 3.3 Compartilhamento e Visibilidade

> **Modelo de compartilhamento — sem rateio**: o compartilhamento é **exclusivamente de
> visibilidade**. Toda conta ou gasto tem **um dono**, e o valor pertence **integralmente** a ele.
> Marcar um lançamento com escopo GRUPO torna-o visível a todos os membros daquele grupo — não
> divide o valor entre eles. **Ninguém deve nada a ninguém dentro do sistema.**
>
> Exemplo: numa casa com Rafael e Ana, a conta de mercado de R$ 400 cadastrada por Ana com escopo
> GRUPO é vista por Rafael, mas os R$ 400 são gasto da Ana, inteiros. Rafael não tem cota nela.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-11 | M | O sistema deve permitir marcar um lançamento com escopo **PESSOAL** (visível apenas ao dono) ou **GRUPO** (visível a todos os membros do grupo indicado). |
| ~~RF-12~~ | — | ~~Compartilhamento avulso com usuários fora do grupo.~~ **REMOVIDO** na revisão 3 — com grupos genéricos, o caso passou a ser atendido criando um grupo. |
| ~~RF-13~~ | — | ~~Divisão igual do valor entre os membros.~~ **REMOVIDO** na revisão 8. |
| ~~RF-14~~ | — | ~~Divisão configurável por percentual ou valor absoluto.~~ **REMOVIDO** na revisão 8. |
| ~~RF-15~~ | — | ~~Invariante soma das cotas igual ao valor total.~~ **REMOVIDO** na revisão 8 — não existem cotas. |
| RF-16 | M | O sistema deve permitir que **qualquer membro do grupo** edite ou exclua um lançamento de escopo GRUPO daquele grupo, mesmo sem ser o dono. |
| RF-17 | M | O sistema deve registrar o **dono** de cada lançamento — quem o cadastrou e a quem o valor pertence integralmente. O dono é preservado quando outro membro edita o lançamento (RF-16). |

> **Justificativa da remoção de RF-13 a RF-15** (revisão 8, decisão D-27): o usuário esclareceu que
> o propósito do grupo é *"dividir as contas de uma casa"* no sentido de **enxergar as contas uns
> dos outros**, não de ratear valores. Cada conta tem um dono claro e o valor é dele. Os números
> RF-13, RF-14 e RF-15 **não foram reaproveitados**, para preservar a rastreabilidade.

### 3.4 Gastos

| ID | Prioridade | Requisito |
|---|---|---|
| RF-18 | M | O sistema deve permitir cadastrar um gasto com, no mínimo: descrição, valor, data e categoria. |
| RF-19 | M | O sistema deve permitir associar um gasto a uma forma de pagamento — à vista ou em cartão de crédito. |
| RF-20 | M | O sistema deve permitir editar e excluir gastos, respeitando as regras de permissão (RF-16). |
| RF-21 | M | O sistema deve permitir consultar gastos por período, com filtros por categoria, grupo, escopo e **dono**. |
| RF-22 | S | O sistema deve totalizar os gastos consultados, no total e por categoria. |
| RF-97 | M | O sistema deve apresentar **dois totais distintos e nunca somados entre si**: o **total pessoal** — apenas lançamentos em que o usuário consultante é dono — e o **total do grupo** — todos os lançamentos de escopo GRUPO daquele grupo, de qualquer dono. |

### 3.5 Cartões de Crédito

| ID | Prioridade | Requisito |
|---|---|---|
| RF-23 | M | O sistema deve permitir cadastrar cartões de crédito com apelido, **dia de fechamento** e **dia de vencimento** (Question 8). |
| RF-24 | M | O sistema deve permitir que um cartão pertença a um **usuário** ou a um **grupo**; a fatura de um cartão do grupo é visível a todos os seus membros (Question 12). |
| RF-25 | M | O sistema deve determinar automaticamente em qual **fatura de competência** cada compra ou parcela cai, a partir da data da compra e do **dia de fechamento** do cartão — nunca do dia de vencimento (ver RF-61). O corte é **exclusivo**: uma compra no exato dia do fechamento pertence à **fatura seguinte** (E-03). |
| RF-26 | M | O sistema deve consolidar a fatura mensal de um cartão, listando todos os lançamentos e parcelas que nela incidem, com o valor total. A fatura fechada materializa-se como uma **conta a pagar** (RF-59). |
| RF-27 | M | O sistema deve permitir marcar uma fatura como **paga**, registrando a data do pagamento — operação equivalente a quitar a conta a pagar correspondente (RF-57). |
| RF-94 | M | O sistema deve permitir **desmarcar** o pagamento de uma fatura ou conta, revertendo-a para EM ABERTO. É a única via para corrigir lançamentos que afetariam uma fatura já paga (E-12, E-13). |
| RF-95 | M | O sistema deve **bloquear** qualquer operação que altere o valor de uma fatura ou conta já **PAGA** — inclusive lançamento retroativo e exclusão de compra (E-13). |
| RF-96 | M | Ao lançar uma compra retroativa cuja fatura de competência já fechou mas **não** foi paga, o sistema deve **reabrir e recalcular** essa fatura, mantendo a compra na competência correta pela data (E-12). |
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
| RF-41 | S | O sistema deve apresentar o balanço do período (receitas − gastos). Os **aportes em investimento contam como gasto** neste cálculo (RF-76). |

### 3.9 Orçamento por Categoria

| ID | Prioridade | Requisito |
|---|---|---|
| RF-42 | M | O sistema deve permitir definir um teto de orçamento mensal por categoria. |
| RF-43 | M | O sistema deve comparar o orçado com o realizado do mês, por categoria. |
| RF-44 | S | O sistema deve sinalizar categorias que ultrapassaram o teto orçado. |

### 3.10 Contas a Pagar (vencimentos)

> **Conceito**: uma **conta a pagar** é qualquer obrigação financeira com **vencimento próprio** e
> status de pagamento — fatura de cartão de crédito, PIX a fazer, boleto, ou fatura de serviço
> (energia, gás, água, internet). Todas convivem numa **visão única de vencimentos**.
>
> **Unificação com a fatura de cartão**: a fatura consolidada do cartão (RF-26) **é** uma conta a
> pagar, gerada automaticamente pelo sistema — não precisa ser cadastrada à mão.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-55 | M | O sistema deve permitir cadastrar uma conta a pagar com, no mínimo: descrição, valor, **data de vencimento própria**, tipo e categoria. |
| RF-56 | M | O sistema deve suportar os tipos de conta: **FATURA_CARTAO**, **PIX**, **BOLETO** e **FATURA_SERVICO** (energia, gás, água, internet e similares). |
| RF-57 | M | O sistema deve manter o status de cada conta como **EM ABERTO** ou **PAGA**, registrando a data do pagamento na quitação. |
| RF-58 | M | O sistema deve apresentar uma **visão consolidada de vencimentos** por período, reunindo todos os tipos de conta, ordenada por data de vencimento e com o total do período. |
| RF-59 | M | O sistema deve **gerar automaticamente** uma conta a pagar do tipo FATURA_CARTAO a cada fechamento de fatura, com valor igual ao total consolidado (RF-26) e vencimento igual ao dia de vencimento do cartão (RF-23). |
| RF-60 | M | Enquanto a fatura de um cartão estiver **aberta**, cada nova compra lançada naquele cartão deve **incrementar o valor** da fatura em formação. |
| RF-61 | M | O corte que determina em qual fatura a compra cai é o **dia de fechamento** do cartão, informado no seu cadastro (RF-23). Compras após o fechamento vão para a fatura do mês seguinte, ainda que a fatura corrente não tenha vencido. |
| RF-62 | M | Ao cadastrar uma conta, o sistema deve **perguntar se ela se repete**. Contas recorrentes e avulsas convivem no mesmo modelo. |
| RF-63 | M | Para contas recorrentes, o sistema deve gerar automaticamente a ocorrência de cada período a partir do cadastro (descrição, dia de vencimento e frequência). |
| RF-64 | M | O valor de uma ocorrência de conta recorrente deve ser **ajustável** no momento do pagamento — contas como energia e gás variam mês a mês. |
| RF-65 | M | O sistema deve permitir marcar uma conta a pagar com escopo **PESSOAL** ou **GRUPO**, seguindo as mesmas regras de visibilidade dos gastos (RF-11, RF-16, RF-17). O valor pertence integralmente ao dono da conta. |
| RF-66 | S | O sistema deve permitir consultar contas **a vencer** num horizonte configurável (ex.: próximos 7 ou 30 dias) e identificar contas **vencidas e não pagas**. |
| RF-67 | S | O sistema deve permitir encerrar uma conta recorrente, interrompendo a geração de novas ocorrências sem apagar o histórico. |

### 3.11 Investimentos

> **Conceito**: um **objetivo de investimento** é um bolso nomeado onde o usuário guarda dinheiro
> com um propósito — "Viagem", "Reserva de emergência", "Geral". O usuário registra **aportes** e
> pode atualizar o **saldo atual à mão** para refletir o rendimento, sem que o sistema precise
> conhecer ativos ou indexadores.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-68 | M | O sistema deve permitir criar objetivos de investimento com nome identificador (ex.: "Viagem", "Geral"). |
| RF-69 | M | O sistema deve permitir registrar **aportes** em um objetivo, com valor e data. |
| RF-70 | M | O sistema deve acumular o **total aportado** em cada objetivo. |
| RF-71 | M | O sistema deve permitir **atualizar manualmente o saldo atual** de um objetivo, para refletir rendimento sem exigir controle de ativos. |
| RF-72 | M | O sistema deve calcular o **rendimento implícito** de um objetivo como `saldo atual − total aportado`. |
| RF-73 | M | O sistema deve permitir definir um **valor de meta** para um objetivo, apresentando o progresso e quanto falta. A meta é **opcional** — objetivos abertos como "Geral" podem não ter alvo. |
| RF-74 | M | O sistema deve permitir definir um **prazo alvo** para um objetivo e calcular o **aporte mensal necessário** para atingir a meta dentro do prazo. |
| RF-75 | M | O sistema deve permitir que um objetivo de investimento pertença a um **grupo**, com todos os membros aportando e enxergando o progresso. Cada aporte registra seu dono; o saldo do objetivo é a soma de todos os aportes, sem rateio. |
| RF-76 | M | O **aporte deve ser contabilizado como gasto** no balanço do mês (RF-41), como qualquer outra saída de dinheiro. |
| RF-77 | S | O sistema deve apresentar a posição consolidada de todos os objetivos: total aportado, saldo atual e rendimento agregado. |

### 3.12 Contrato de API (entregável para o front-end)

> **Contexto**: o front-end web é implementado em **outro repositório** (Question 3). O contrato da
> API é a única interface entre os dois — precisa ser explícito, versionado e consumível por
> ferramenta.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-78 | M | O projeto deve publicar uma especificação **OpenAPI 3.1 em YAML** cobrindo todos os endpoints, schemas de request/response, códigos de status e formatos de erro. |
| RF-79 | M | A especificação deve ser **versionada no repositório** e mantida em sincronia com a implementação. |
| RF-80 | S | A especificação deve ser suficiente para gerar um cliente TypeScript por ferramenta (ex.: `openapi-generator`), sem edição manual. |

> **Momento de entrega**: por decisão do usuário, a especificação será gerada **após a Application
> Design** — quando entidades, serviços e fronteiras de componente estiverem definidos —, e não a
> partir dos requisitos. Evita que o front seja construído sobre um contrato provisório sujeito a
> retrabalho. Ver decisão D-06.

### 3.13 Infraestrutura e Deploy

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

### 3.14 CI/CD e Provisionamento

> **Contexto**: o AI-DLC entrega o Terraform escrito, mas **não provisiona** — a fase de Operations
> do método é um placeholder vazio (*"The AI-DLC workflow currently ends after the Build and Test
> phase in CONSTRUCTION"*). A lacuna é fechada por **GitHub Actions**: o `terraform apply` roda no
> CI a partir do merge em `main`, não da máquina de ninguém.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-81 | M | O projeto deve usar **GitHub Actions** como plataforma de CI/CD, com workflows versionados em `.github/workflows/`. |
| RF-82 | M | O Actions deve autenticar na AWS por **OIDC** (`AssumeRoleWithWebIdentity`), com token de curta duração. **Nenhuma credencial AWS de longa duração** pode existir nos GitHub Secrets. |
| RF-83 | M | Deve existir workflow de **CI da aplicação** — build Gradle e execução dos testes (incluindo Testcontainers) — disparado em PR que toca `src/**` ou arquivos de build. |
| RF-84 | M | Deve existir workflow de **`terraform plan`** disparado em PR que toca `infra/**`, publicando o diff de forma visível no PR. |
| RF-85 | M | Deve existir workflow de **`terraform apply`**, disparado automaticamente no merge para `main`, sem gate de aprovação manual (decisão do usuário — ver risco R-05). |
| RF-86 | M | Deve existir workflow que constrói a **imagem Docker** da aplicação e faz push para o **Amazon ECR**, na mesma conta AWS. |
| RF-87 | M | A imagem deve ser versionada por **tag imutável derivada do commit SHA**, permitindo rollback determinístico para qualquer versão anterior. |
| RF-88 | M | O deploy na EC2 deve ocorrer via **AWS Systems Manager Run Command** (`ssm send-command`), executando o pull da imagem e o restart do serviço. |
| RF-89 | M | A instância deve puxar a imagem do ECR pela sua **IAM role**, sem nenhuma credencial de registry armazenada na máquina. |
| RF-90 | M | A **porta 22 (SSH) deve permanecer fechada** no security group. Não pode existir chave SSH privada nos GitHub Secrets nem no repositório. |
| RF-91 | M | Deve existir um módulo **`infra/terraform/bootstrap/`** separado, com state local, criando os recursos que o próprio CI precisa para funcionar: bucket S3 do state, mecanismo de lock, provider OIDC do GitHub, IAM role assumida pelo Actions e repositório ECR. |
| RF-92 | M | O bootstrap deve ser aplicado **uma única vez, manualmente**, e o projeto deve entregar um **runbook** com o passo a passo (pré-requisitos, comandos na ordem, o que conferir e como reverter). |
| RF-93 | S | A trust policy da role OIDC deve restringir o acesso ao **repositório e à branch** específicos (`repo:RafaelMatheus/financial-control:ref:refs/heads/main`), não a qualquer repositório da organização. |

---

## 4. Requisitos Não-Funcionais

| ID | Categoria | Requisito |
|---|---|---|
| RNF-01 | Integridade monetária | Valores monetários devem usar aritmética decimal exata (`BigDecimal` com escala 2), **nunca** ponto flutuante. Toda operação de divisão deve ter política de arredondamento explícita. |
| RNF-02 | Integridade transacional | Operações que criam múltiplos registros relacionados (compra + N parcelas; fatura + lançamentos) devem ser atômicas. |
| RNF-03 | Data/hora | Timestamps persistidos em **UTC** (já configurado: `hibernate.jdbc.time_zone: UTC`). Datas de negócio (competência, vencimento) são datas civis sem fuso. |
| RNF-04 | Versionamento de schema | O schema deve ser criado e versionado via **Flyway**, com `ddl-auto: validate` mantido para detectar divergência entre entidades e schema (Question 13). |
| RNF-05 | Isolamento de dados | Toda consulta deve ser escopada ao usuário autenticado e às suas visibilidades. Não deve existir endpoint que retorne dados sem esse filtro. |
| RNF-06 | Testabilidade | Testes de integração devem rodar contra PostgreSQL real via Testcontainers (padrão já estabelecido no repositório). |
| RNF-07 | Property-based testing | Aplicar PBT em modo **Parcial** (regras PBT-02, PBT-03, PBT-07, PBT-08, PBT-09). Framework: **Kotest Property Testing** (Kotlin — PBT-09). Alvo natural: divisão de parcelas (RF-31, RF-32). *O alvo de rateio (ex-RF-15) deixou de existir na revisão 8.* |
| RNF-08 | Contrato de API | A API é consumida por um front-end web **em outro repositório**. O contrato deve ser publicado como especificação **OpenAPI 3.1 em YAML**, versionada, servindo de fonte única para o desenvolvimento do front (permite Swagger UI e geração de cliente TypeScript). Entregável definido em RF-78. |
| RNF-09 | Tratamento de erros | Respostas de erro devem seguir formato consistente, com código e mensagem acionáveis. |
| RNF-10 | Validação de entrada | Toda entrada da API deve ser validada (`spring-boot-starter-validation` já está no classpath, hoje sem uso). |
| RNF-11 | Manutenibilidade | Separação clara de camadas (API / aplicação / domínio / persistência), a ser definida na Application Design. |
| RNF-12 | Escala | Uso pessoal/doméstico: dezenas de usuários, milhares de lançamentos. **Não** há requisito de alta escala, alta disponibilidade ou baixa latência agressiva. |
| RNF-13 | Reprodutibilidade da infra | Toda a infraestrutura deve ser recriável a partir do Terraform versionado, sem passos manuais no console AWS. |
| RNF-14 | Isolamento de CI | Com app e IaC no mesmo repositório, o pipeline deve usar **filtro de path** — `terraform plan` não deve rodar em mudanças que tocam apenas código Kotlin, e vice-versa (RF-83, RF-84). |
| RNF-15 | Segredos | `*.tfstate`, `*.tfvars` com valores sensíveis e arquivos `.env` não podem ser versionados. O `.gitignore` deve ser estendido para cobri-los. |
| RNF-16 | Superfície de exposição | O security group deve expor apenas as portas necessárias. A porta do PostgreSQL (5432) **não** deve ser acessível pela internet — apenas localmente na instância. A **porta 22 (SSH) permanece fechada**, já que o acesso administrativo se dá por SSM (RF-88, RF-90). |
| RNF-17 | Durabilidade dos dados | Como o PostgreSQL roda no próprio EC2 (sem backup gerenciado), a persistência depende de volume EBS separado (RF-50) e de rotina de backup própria (RF-54). Ver risco R-01. |

---

## 5. Cenários de Usuário Principais

**C-01 — Lançar um gasto visível para o grupo**
Ana faz uma compra de mercado de R$ 400. Ela lança o gasto com escopo GRUPO (Apartamento 42),
categoria "Alimentação". Rafael, membro do mesmo grupo, enxerga o lançamento e vê que o dono é Ana.
Os R$ 400 contam no **total pessoal da Ana** e no **total do grupo**, mas não no total pessoal do
Rafael — ele não deve nada por causa dessa conta.

**C-02 — Lançar uma compra parcelada no cartão**
Rafael compra um notebook em 12x de R$ 100 no cartão Nubank (fechamento dia 28, vencimento dia 5),
em 30/07/2026. O sistema calcula o total de R$ 1.200, gera 12 parcelas e, como a compra ocorreu
após o fechamento, aloca a parcela 1/12 na fatura de **setembro/2026**, e as demais nos meses
subsequentes.

**C-03 — Conferir e pagar a fatura**
Rafael consulta a fatura de agosto/2026 do Nubank. O sistema lista todos os lançamentos e parcelas
com competência naquela fatura, apresenta o total e permite marcá-la como paga com a data do
pagamento.

**C-04 — Compartilhar gastos com pessoas fora do grupo doméstico**
Rafael vai acompanhar os gastos de uma viagem junto com João e Maria, que não pertencem ao grupo
"Apartamento 42". Ele cria o grupo **"Viagem Chapada"** com os três. Cada um lança suas próprias
despesas com escopo GRUPO nesse grupo, e todos enxergam o total gasto na viagem — mantendo claro
quem pagou o quê, sem que o sistema calcule quem deve a quem.

**C-05 — Acompanhar o orçamento**
Rafael define um teto de R$ 1.500/mês para "Alimentação". Ao consultar o orçamento de agosto, vê o
realizado de R$ 1.720 e a categoria sinalizada como estourada.

**C-06 — Correção de uma compra parcelada**
Rafael percebe que lançou o notebook em 12x de R$ 100 quando o correto era 10x de R$ 120. Ele edita
a compra; o sistema descarta as 12 parcelas anteriores e regenera 10 parcelas, mantendo a
invariante soma = R$ 1.200.

**C-07 — Ver tudo o que vence no mês, num lugar só**
Rafael abre a visão de vencimentos de agosto/2026. O sistema lista, ordenado por data: a fatura do
Nubank (R$ 539,90, vence 05/08 — gerada automaticamente no fechamento de 28/07), a conta de energia
(R$ 180, vence 10/08 — recorrente), o PIX do aluguel para Ana (R$ 800, vence 15/08) e o boleto do
IPVA (R$ 420, vence 20/08). Total de R$ 1.939,90. Ele marca a energia como paga em 09/08.

**C-08 — Compra entra na fatura aberta**
Em 20/07 Rafael compra R$ 150 em livros no Nubank (fechamento dia 28). Como a fatura de agosto
ainda está aberta, o valor entra nela — que sobe de R$ 389,90 para R$ 539,90. Em 30/07 ele compra
mais R$ 80: já passou o fechamento, então esse valor vai para a fatura de setembro, mesmo a de
agosto ainda não tendo vencido.

**C-09 — Guardar dinheiro para a viagem**
Rafael cria o objetivo "Viagem Europa" com meta de R$ 15.000 e prazo alvo julho/2027. O sistema
calcula o aporte mensal necessário. Ele aporta R$ 2.000 em junho, julho e agosto — R$ 6.000
acumulados, e cada aporte entra como gasto no balanço do mês. Em setembro ele consulta o extrato do
banco, vê R$ 6.240 e atualiza o saldo à mão; o sistema passa a mostrar R$ 240 de rendimento e
progresso de 41,6% da meta.

**C-10 — Objetivo compartilhado do grupo**
Rafael e Ana criam o objetivo "Reforma" no grupo "Apartamento 42", com meta de R$ 8.000. Ambos
aportam ao longo dos meses e os dois enxergam o total acumulado e o progresso.

### Cenários de borda e erro identificados

| # | Cenário | Tratamento esperado |
|---|---|---|
| E-01 | Compra parcelada com resíduo de centavos (R$ 100 em 3x) | RF-31: última parcela absorve |
| ~~E-02~~ | ~~Rateio com soma divergente~~ | **REMOVIDO** na revisão 8 — não há rateio |
| E-03 | Compra no exato dia de fechamento do cartão | ✅ **Resolvido** (User Stories): vai para a **fatura seguinte** — o fechamento ocorre no início do dia. Corte exclusivo: `dataCompra < diaFechamento`. Fecha D-04 |
| E-04 | Cartão com fechamento no dia 31 e mês com 30 dias | Regra de fronteira a definir na Functional Design |
| E-05 | Membro sai do grupo com gastos compartilhados em aberto | RF-10: preservar histórico |
| E-06 | Exclusão de categoria com gastos vinculados | RF-37: bloquear ou realocar |
| E-07 | Tentativa de adicionar usuário inexistente a um grupo | Rejeitar com erro de validação |
| E-08 | Usuário tenta acessar fatura de cartão de outro grupo | RF-04: negar acesso |
| E-09 | Gasto de escopo GRUPO lançado por usuário que não pertence a nenhum grupo | Rejeitar — escopo GRUPO exige grupo válido do qual o autor é membro (RF-07, RF-11) |
| E-10 | Membro adicionado a um grupo após gastos já lançados | ✅ **Resolvido** (User Stories): enxerga **todo o histórico** do grupo. Visibilidade e rateio ficam desacoplados — vê os gastos antigos, mas não tem cota neles. Fecha D-13 |
| E-11 | Conta recorrente com vencimento no dia 31 e mês com 30 dias | Regra de fronteira a definir na Functional Design (mesma natureza de E-04) |
| E-12 | Compra lançada retroativamente, em fatura de cartão já fechada | ✅ **Resolvido** (User Stories): a fatura é **reaberta e recalculada**, com a compra na competência correta pela data — **exceto** se já estiver PAGA, quando a operação é bloqueada (E-13) |
| E-13 | Alteração que afeta fatura já **PAGA** (exclusão de compra ou lançamento retroativo) | ✅ **Resolvido** (User Stories): **bloquear a operação**. Fatura paga é fato consumado e bate com o extrato do banco. Saída para o usuário: desmarcar o pagamento, corrigir, e marcar como paga de novo — operação explícita, não efeito colateral |
| E-14 | Saldo atual do objetivo informado **menor** que o total aportado (prejuízo ou resgate não registrado) | Rendimento implícito fica negativo (RF-72). Deve ser aceito e exibido, não rejeitado — ver premissa P-08 |
| E-15 | Objetivo com meta e prazo já vencido sem a meta atingida | Sinalizar o objetivo como atrasado; não bloquear novos aportes |
| E-16 | Objetivo de grupo com membro que sai do grupo | Mesmo tratamento de E-05: preservar o histórico de aportes |

---

## 6. Configuração de Extensões

| Extensão | Habilitada | Modo | Decidido em |
|---|---|---|---|
| `security/baseline` | **Não** | — | Requirements Analysis (Question 14) |
| `resiliency/baseline` | **Não** | — | Requirements Analysis (Question 15) |
| `testing/property-based` | **Sim** | **Parcial** (PBT-02, PBT-03, PBT-07, PBT-08, PBT-09 bloqueantes; demais advisory) | Requirements Analysis (Question 16) |

> **Ressalva importante sobre a extensão Security**: desligá-la remove o *checklist bloqueante de
> hardening* das stages do AI-DLC. **Não** remove autenticação, isolamento de dados ou permissões
> de grupo — confirmado pelo usuário na Question 17, esses permanecem como requisitos funcionais
> de primeira classe (RF-01 a RF-05, RF-16, RF-24).

---

## 7. Premissas

| ID | Premissa | Impacto se incorreta |
|---|---|---|
| P-01 | Moeda única: **BRL**. Sem conversão nem multi-moeda. | Alto — mudaria o modelo de valores monetários |
| P-02 | Fuso horário de negócio: **America/Sao_Paulo**, com persistência em UTC. | Médio — afetaria cálculo de competência de fatura |
| P-03 | Volume: dezenas de usuários, milhares de lançamentos. Sem requisito de escala. | Baixo — afetaria decisões de índice e paginação |
| P-04 | ~~Deploy: nenhuma decisão tomada.~~ **Revisada**: deploy em **AWS EC2**, IaC em Terraform no mesmo repositório, PostgreSQL na própria instância. Ver Seção 8 (D-08 a D-10) e Seção 8.1 (riscos). | — (decidido) |
| P-05 | Receitas são individuais, **não** compartilháveis com o grupo. | Médio — o usuário pediu compartilhamento explicitamente para *gastos e contas* |
| P-06 | ~~O sistema calcula as cotas de cada gasto.~~ **Revisada na revisão 8**: não há cotas nem rateio. O sistema não calcula divisão de valores, não consolida "quem deve a quem" e não registra acertos entre membros. | — (decidido em D-27) |
| P-07 | ~~Um gasto pago com cartão do grupo pode ter rateio próprio.~~ **Revisada na revisão 8**: sem rateio. Um lançamento em cartão do grupo tem o dono que o cadastrou; a propriedade do cartão define apenas quem enxerga a fatura. | — (decidido em D-27) |
| P-08 | **Não** há registro de resgate/retirada de objetivo de investimento — o usuário não marcou esse atributo. Retiradas são refletidas indiretamente ao atualizar o saldo atual à mão (RF-71). | Médio — se incorreta, o rendimento implícito (RF-72) fica distorcido em objetivos com retirada |
| P-09 | O sistema **registra** que uma conta foi paga; não executa pagamento nem integra com banco. | Baixo |
| P-10 | Contas recorrentes têm frequência **mensal**. Outras periodicidades (anual, semanal) não foram solicitadas. | Médio — IPVA e IPTU são anuais e poderiam se beneficiar |
| P-11 | A fatura de cartão gerada como conta a pagar (RF-59) é **derivada**, não editável diretamente: seu valor vem da consolidação dos lançamentos (RF-26). | Médio — afeta o modelo de domínio e o comportamento de E-13 |

---

## 8. Decisões Técnicas e Pontos em Aberto

| ID | Item | Status |
|---|---|---|
| D-01 | **Flyway** como ferramenta de migration, mantendo `ddl-auto: validate` | ✅ Decidido (Question 13). Resolve o débito bloqueante apontado na engenharia reversa |
| D-02 | Mecanismo de autenticação (JWT stateless / sessão / OAuth2-OIDC) | ⏳ Adiado para **NFR Requirements** (seleção de stack) |
| D-03 | Estrutura de pacotes e separação de camadas | ⏳ Adiado para **Application Design** |
| D-04 | Regra de fronteira do fechamento de fatura | ✅ **Parcialmente resolvido** (User Stories): compra no exato dia do fechamento vai para a **fatura seguinte** — corte exclusivo `dataCompra < diaFechamento` (E-03). Continua adiado apenas o caso de fechamento em dia 29–31 com mês curto (E-04) | ⏳ Functional Design (só E-04) |
| D-05 | Framework PBT: **Kotest Property Testing** (recomendação PBT-09 para Kotlin) | ✅ Pré-decidido; confirmar em NFR Requirements |
| D-06 | **Contrato de API em OpenAPI 3.1 (YAML)**, entregue **após a Application Design** (RF-78 a RF-80) | ✅ Decidido pelo usuário. Justificativa: o front-end vive em outro repositório e seria construído sobre um contrato provisório se gerado a partir dos requisitos. Esperar o modelo de domínio estabilizar evita retrabalho no front. Formato YAML permite Swagger UI e geração de cliente TypeScript. A ferramenta de geração no backend (springdoc-openapi ou spec escrita à mão) segue adiada para **NFR Requirements** |
| D-07 | ~~Modelagem de "participante" de um gasto compartilhado.~~ **Resolvido duplamente**: pela remoção de RF-12 (rev. 3) e, definitivamente, pela remoção do rateio (rev. 8, D-27). Não existe entidade de participante nem de cota — cada lançamento tem um único **dono**. | ✅ Resolvido |
| D-13 | Visibilidade do histórico para membro que entra em um grupo já existente | ✅ **Resolvido** (User Stories): enxerga todo o histórico. Simplificado ainda mais na rev. 8 — sem rateio, não há cota a atribuir retroativamente (E-10) |
| D-14 | **Fatura de cartão unificada com conta a pagar**: a fatura fechada gera automaticamente uma conta a pagar (RF-59), em vez de viver num módulo separado | ✅ Decidido. Uma única visão de vencimentos reunindo fatura, PIX, boleto e fatura de serviço |
| D-15 | **Fechamento** (não vencimento) determina a fatura de destino de cada compra (RF-61) | ✅ Decidido. Confirma o comportamento real de cartão de crédito e mantém os valores compatíveis com o extrato do banco |
| D-16 | **Recorrência como pergunta no cadastro** da conta (RF-62), com contas recorrentes e avulsas no mesmo modelo | ✅ Decidido |
| D-17 | **Investimento: aportes + saldo manual** (RF-69, RF-71), sem controle de ativos, indexadores ou cotação | ✅ Decidido. Rendimento é derivado (`saldo − aportado`), não calculado a partir de taxas |
| D-18 | **Aporte conta como gasto** no balanço do mês (RF-76) | ✅ Decidido pelo usuário. Consequência: o balanço mede fluxo de caixa, não variação patrimonial — investir reduz o saldo do mês |
| D-19 | Mecanismo de geração das ocorrências de contas recorrentes (job agendado, geração sob demanda na consulta, ou híbrido) | ⏳ Adiado para **Functional Design** |
| D-20 | Momento e mecanismo do fechamento automático da fatura (RF-59) — job agendado ou cálculo derivado na leitura | ⏳ Adiado para **Functional Design** |
| D-27 | **Compartilhamento é apenas visibilidade — não há rateio de valores** | ✅ Decidido (revisão 8). Cada lançamento tem um dono e o valor é integralmente dele. O grupo torna o lançamento visível aos membros, sem dividi-lo. Consequências: RF-13, RF-14 e RF-15 removidos; a questão J-01 (rateio por parcela) deixa de existir; uma das três invariantes de property-based testing desaparece; o modelo de dados perde a entidade Cota |
| D-28 | **Totais pessoal e de grupo são grandezas distintas** (RF-97) | ✅ Decidido. "Quanto eu gastei" soma apenas os lançamentos de que sou dono; "quanto a casa gastou" soma todos os lançamentos de escopo GRUPO. Nunca se somam entre si |
| D-21 | **GitHub Actions** como plataforma de CI/CD, fechando a lacuna de provisionamento do AI-DLC | ✅ Decidido. A fase de Operations do método é placeholder; sem CI, o `terraform apply` ficaria manual e não rastreável |
| D-22 | **OIDC** para autenticação do Actions na AWS, em vez de access keys em Secrets | ✅ Decidido. Elimina credencial de longa duração do repositório; trust policy restrita a repo e branch (RF-93) |
| D-23 | **Amazon ECR** como registry da imagem, em vez de GHCR | ✅ Decidido. A EC2 puxa por IAM role, sem token de registry armazenado na instância — coerente com a escolha de OIDC e SSM |
| D-24 | **SSM Run Command** como mecanismo de deploy, em vez de SSH | ✅ Decidido. Mantém a porta 22 fechada e dispensa chave privada nos Secrets. Resolve parcialmente D-12 |
| D-25 | **`terraform apply` automático no merge**, sem gate de aprovação manual | ✅ Decidido pelo usuário, ciente da alternativa (GitHub Environment com reviewer). Ver risco R-05 |
| D-26 | **Bootstrap manual e único** (`infra/terraform/bootstrap/`) para resolver o ovo-e-galinha do state remoto e da role OIDC | ✅ Decidido. Entregue com runbook passo a passo (RF-92) |
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
| R-05 | **`terraform apply` automático no merge, sem aprovação manual**: um PR que remova ou altere recursos chega em produção sem ponto de parada. O caso mais grave é a substituição ou destruição do volume EBS do PostgreSQL (RF-50) — combinado com R-01 (sem backup gerenciado), significaria **perda dos dados financeiros**. | **Alta** | Decisão consciente do usuário (D-25), ciente da alternativa com GitHub Environment. Mitigações a detalhar na Infrastructure Design: `prevent_destroy` no volume EBS e demais recursos com estado, `terraform plan` obrigatório e visível no PR (RF-84), e a rotina de backup de RF-54 tratada como pré-requisito de qualquer merge que toque `infra/**`. |

---

## 9. Critérios de Aceitação do Ciclo

O ciclo será considerado bem-sucedido quando:

1. Um usuário conseguir se autenticar e enxergar apenas os próprios dados e os que lhe são
   compartilhados
2. Uma compra parcelada lançada por valor de parcela gerar N parcelas com soma exatamente igual ao
   total
3. Cada parcela cair na fatura correta segundo o ciclo de fechamento do cartão
4. Um lançamento de escopo GRUPO aparecer para todos os membros do grupo, com o dono identificado,
   e o total pessoal de cada membro somar apenas os lançamentos de que ele é dono
5. A fatura mensal consolidar corretamente os lançamentos e puder ser marcada como paga
6. O schema for criado por migrations Flyway versionadas, com `ddl-auto: validate` passando
7. Os testes rodarem contra PostgreSQL via Testcontainers, incluindo PBT para a divisão de parcelas
8. A infraestrutura (EC2 + PostgreSQL em container + volume EBS + security group) for provisionável
   a partir do Terraform versionado em `infra/terraform/`, sem passos manuais no console AWS
9. A visão de vencimentos reunir, ordenados por data, a fatura de cartão gerada automaticamente e
   as contas de PIX, boleto e serviço — com marcação de pagamento funcionando para todas
10. Uma compra lançada antes do fechamento aumentar a fatura em aberto, e uma lançada depois do
    fechamento cair na fatura seguinte, ainda que a atual não tenha vencido
11. Uma conta recorrente gerar as ocorrências de cada mês com valor ajustável no pagamento
12. Um objetivo de investimento acumular aportes, aceitar atualização manual de saldo, exibir
    rendimento implícito e progresso contra a meta, e funcionar também no escopo de grupo
13. Um PR que toque `src/**` disparar build e testes, e um que toque `infra/**` disparar
    `terraform plan` com o diff visível — sem que um dispare o workflow do outro
14. O merge em `main` aplicar a infraestrutura, construir e publicar a imagem no ECR, e atualizar a
    instância EC2 via SSM, sem nenhuma credencial de longa duração envolvida
15. O runbook de bootstrap ser suficiente para, partindo de uma conta AWS vazia, deixar o pipeline
    operacional

---

## 10. Rastreabilidade

| Requisito | Origem |
|---|---|
| RF-01 a RF-05 | Question 1 (multi-usuário) + Question 17 (auth como requisito funcional) |
| RF-06 a RF-10 | Question 2 (texto livre do usuário sobre grupo) + Question 5 |
| RF-11, RF-16, RF-17 | Questions 5, 6, 7 — revisados na generalização Casa → Grupo (rev. 3) e novamente na remoção do rateio (rev. 8). RF-12 removido na rev. 3; RF-13, RF-14 e RF-15 removidos na rev. 8 |
| RF-97 | Esclarecimento do usuário na revisão 8: *"todos dois conseguem ver suas contas de uma casa caso sejam membros do mesmo grupo e as contas estejam cadastrada para aquele grupo nao individualmente"* |
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
| RF-55 a RF-67 | Rodada de esclarecimento sobre contas a pagar — pedido do usuário: *"cada conta pode ter um vencimento especifico para ela"*, detalhado como *"conta de cartao de credito, pix que tenho para fazer, boleto, fatura (energia eletrica, gás, viagem)"* e *"a fatura da conta deve passar a ser um vencimento geral"* |
| RF-68 a RF-77 | Mesmo pedido — *"eu quero poder adicionar valores relacionados a investimentos... Exemplo: investimento de viagem e investimento de geral"* |
| RF-94 a RF-96 | Resolução dos casos de borda E-12 e E-13 no planejamento das User Stories |
| RF-81 a RF-93 | Pedido do usuário: *"preciso que no plaejamento seja incluido também o github actions com tudo já pronto... Tem como provisionar a infrsaestrutura já com github actions?"* — surgido da pergunta sobre em que momento a infra seria provisionada |
| RF-78 a RF-80 | Pedido do usuário: *"vou precisar de um documento também com endpoints para montar o front"* — formalizado como especificação OpenAPI 3.1, entregue após a Application Design |
| RF-45 a RF-54 | Rodada de esclarecimento sobre infraestrutura (deploy em AWS EC2, Terraform no mesmo repo, PostgreSQL na instância) |
| RNF-13 a RNF-17 | Mesma rodada — decorrências não-funcionais das decisões D-08, D-09 e D-10 |
| R-01 a R-04 | Análise de risco das decisões de infraestrutura |
