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
| **Initial Complexity Estimate** | **Complex** — 10+ agregados de domínio, regras de rateio configurável, cálculo de competência de fatura a partir do ciclo do cartão, motor de recorrência de contas, aritmética monetária com resíduo, e um modelo de autorização com dois eixos (usuário e grupo) |
| **Depth Justification** | **Comprehensive**. O escopo cresceu significativamente durante o esclarecimento: o pedido original ("cadastrar gastos e parcelas") tornou-se um sistema multi-usuário com compartilhamento de despesas em grupo. Dinheiro, rateio entre pessoas e isolamento de dados são áreas onde ambiguidade custa caro. |

### Evolução do escopo durante a análise

O pedido inicial sugeria um app pessoal simples. O esclarecimento revelou três ampliações
materiais:

1. **Multi-usuário com isolamento de dados** (não era mencionado no pedido original)
2. **Compartilhamento de gastos num grupo, com rateio configurável** (requisito novo, trazido pelo
   usuário na resposta à Question 2) — este é o requisito de maior complexidade do sistema
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

---

## 2. Escopo

### 2.1 Dentro do escopo

- API REST (JSON) cobrindo todo o domínio de controle financeiro
- Autenticação e autorização de usuários
- Grupos com N membros (ex.: casa, república, viagem, casal)
- Gastos pessoais e compartilhados, com rateio configurável
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
| RF-09 | M | O sistema deve tornar visível a todos os membros de um grupo qualquer gasto marcado com escopo daquele grupo. |
| RF-10 | S | O sistema deve permitir que um membro saia de um grupo, preservando o histórico dos gastos já lançados. |

### 3.3 Compartilhamento e Rateio

> **Modelo único de compartilhamento**: todo compartilhamento acontece **através de um grupo**.
> Não existe compartilhamento pontual com usuários avulsos — para dividir um gasto com pessoas
> específicas, cria-se um grupo com elas. Isso resulta em um único caminho de visibilidade e um
> único caminho de rateio, em vez de dois modelos paralelos.

| ID | Prioridade | Requisito |
|---|---|---|
| RF-11 | M | O sistema deve permitir marcar um gasto com escopo **PESSOAL** (visível apenas ao autor) ou **GRUPO** (visível a todos os membros do grupo indicado). |
| ~~RF-12~~ | — | ~~Compartilhamento avulso com usuários que não pertencem ao grupo.~~ **REMOVIDO** na revisão pós-gate. Justificativa: com a generalização de "Casa" para "Grupo" (conceito genérico), o caso de uso que este requisito cobria — dividir um gasto com pessoas fora do grupo doméstico — passa a ser atendido criando um grupo. Manter os dois modelos significaria duas regras de visibilidade e dois caminhos de autorização sem ganho funcional. **O número RF-12 não foi reaproveitado**, para preservar a rastreabilidade da numeração. |
| RF-13 | M | O sistema deve dividir o valor de um gasto de escopo GRUPO entre os membros do grupo, com **divisão igual como padrão**. |
| RF-14 | M | O sistema deve permitir sobrescrever a divisão padrão de um gasto, por **percentual** ou por **valor absoluto**, individualmente por membro. |
| RF-15 | M | O sistema deve garantir que a soma das cotas de um gasto compartilhado seja **exatamente igual** ao valor total do gasto — invariante de integridade monetária. |
| RF-16 | M | O sistema deve permitir que **qualquer membro do grupo** edite ou exclua um gasto de escopo GRUPO daquele grupo (Question 7). |
| RF-17 | M | O sistema deve registrar quem lançou cada gasto (autoria), mesmo que outros membros possam editá-lo. |

### 3.4 Gastos

| ID | Prioridade | Requisito |
|---|---|---|
| RF-18 | M | O sistema deve permitir cadastrar um gasto com, no mínimo: descrição, valor, data e categoria. |
| RF-19 | M | O sistema deve permitir associar um gasto a uma forma de pagamento — à vista ou em cartão de crédito. |
| RF-20 | M | O sistema deve permitir editar e excluir gastos, respeitando as regras de permissão (RF-16). |
| RF-21 | M | O sistema deve permitir consultar gastos por período, com filtros por categoria, grupo e escopo. |
| RF-22 | S | O sistema deve totalizar os gastos consultados, no total e por categoria. |

### 3.5 Cartões de Crédito

| ID | Prioridade | Requisito |
|---|---|---|
| RF-23 | M | O sistema deve permitir cadastrar cartões de crédito com apelido, **dia de fechamento** e **dia de vencimento** (Question 8). |
| RF-24 | M | O sistema deve permitir que um cartão pertença a um **usuário** ou a um **grupo**; a fatura de um cartão do grupo é visível a todos os seus membros (Question 12). |
| RF-25 | M | O sistema deve determinar automaticamente em qual **fatura de competência** cada compra ou parcela cai, a partir da data da compra e do **dia de fechamento** do cartão — nunca do dia de vencimento (ver RF-61). |
| RF-26 | M | O sistema deve consolidar a fatura mensal de um cartão, listando todos os lançamentos e parcelas que nela incidem, com o valor total. A fatura fechada materializa-se como uma **conta a pagar** (RF-59). |
| RF-27 | M | O sistema deve permitir marcar uma fatura como **paga**, registrando a data do pagamento — operação equivalente a quitar a conta a pagar correspondente (RF-57). |
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
| RF-65 | M | O sistema deve permitir marcar uma conta a pagar com escopo **PESSOAL** ou **GRUPO**, seguindo as mesmas regras de visibilidade e rateio dos gastos (RF-11, RF-13 a RF-16). |
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
| RF-75 | M | O sistema deve permitir que um objetivo de investimento pertença a um **grupo**, com todos os membros aportando e enxergando o progresso — seguindo as regras de visibilidade de RF-09 e RF-11. |
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
| RNF-08 | Contrato de API | A API é consumida por um front-end web **em outro repositório**. O contrato deve ser publicado como especificação **OpenAPI 3.1 em YAML**, versionada, servindo de fonte única para o desenvolvimento do front (permite Swagger UI e geração de cliente TypeScript). Entregável definido em RF-78. |
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

**C-01 — Lançar um gasto compartilhado do grupo com rateio desigual**
Rafael faz uma compra de mercado de R$ 400. Ele lança o gasto com escopo GRUPO (Apartamento 42),
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

**C-04 — Dividir um gasto com pessoas fora do grupo doméstico**
Rafael vai dividir R$ 900 de uma viagem com João e Maria, que não pertencem ao grupo
"Apartamento 42". Ele cria o grupo **"Viagem Chapada"** com os três e lança o gasto com escopo
GRUPO nesse grupo. O sistema divide o valor entre os três membros e torna o lançamento visível a
eles, sem envolver Ana. Ilustra o modelo único de compartilhamento: qualquer arranjo de pessoas é
expresso como um grupo.

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
| E-02 | Rateio configurado cuja soma difere do total do gasto | RF-15: rejeitar a operação |
| E-03 | Compra no exato dia de fechamento do cartão | Regra de fronteira a definir na Functional Design (ver decisão D-04) |
| E-04 | Cartão com fechamento no dia 31 e mês com 30 dias | Regra de fronteira a definir na Functional Design |
| E-05 | Membro sai do grupo com gastos compartilhados em aberto | RF-10: preservar histórico |
| E-06 | Exclusão de categoria com gastos vinculados | RF-37: bloquear ou realocar |
| E-07 | Tentativa de adicionar usuário inexistente a um grupo | Rejeitar com erro de validação |
| E-08 | Usuário tenta acessar fatura de cartão de outro grupo | RF-04: negar acesso |
| E-09 | Gasto de escopo GRUPO lançado por usuário que não pertence a nenhum grupo | Rejeitar — escopo GRUPO exige grupo válido do qual o autor é membro (RF-07, RF-11) |
| E-10 | Membro adicionado a um grupo após gastos já lançados | Definir na Functional Design se ele passa a enxergar o histórico ou apenas os gastos posteriores à entrada |
| E-11 | Conta recorrente com vencimento no dia 31 e mês com 30 dias | Regra de fronteira a definir na Functional Design (mesma natureza de E-04) |
| E-12 | Compra lançada retroativamente, em fatura de cartão já fechada | Definir na Functional Design: rejeitar, ou alocar na próxima fatura aberta |
| E-13 | Compra excluída depois que a fatura já virou conta a pagar | O valor da conta precisa ser recalculado, ou a operação bloqueada se a conta já estiver PAGA |
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
| P-05 | Receitas são individuais, **não** compartilháveis entre membros do grupo. | Médio — o usuário pediu compartilhamento explicitamente para *gastos* |
| P-06 | O sistema calcula as cotas de cada gasto, mas **não** consolida "quem deve a quem" nem registra acertos entre membros. | Médio — é a extensão natural do rateio; fora do escopo declarado |
| P-07 | Um gasto pago com cartão de crédito do grupo também pode ter rateio próprio, independente da propriedade do cartão. | Médio — afeta o modelo de domínio |
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
| D-04 | Regra de fronteira do fechamento de fatura (compra no exato dia do fechamento; fechamento dia 29–31 em meses curtos) | ⏳ Adiado para **Functional Design** |
| D-05 | Framework PBT: **Kotest Property Testing** (recomendação PBT-09 para Kotlin) | ✅ Pré-decidido; confirmar em NFR Requirements |
| D-06 | **Contrato de API em OpenAPI 3.1 (YAML)**, entregue **após a Application Design** (RF-78 a RF-80) | ✅ Decidido pelo usuário. Justificativa: o front-end vive em outro repositório e seria construído sobre um contrato provisório se gerado a partir dos requisitos. Esperar o modelo de domínio estabilizar evita retrabalho no front. Formato YAML permite Swagger UI e geração de cliente TypeScript. A ferramenta de geração no backend (springdoc-openapi ou spec escrita à mão) segue adiada para **NFR Requirements** |
| D-07 | ~~Modelagem de "participante" de um gasto compartilhado (membro do grupo vs. usuário avulso).~~ **Resolvido** pela remoção de RF-12: existe apenas um tipo de participante — **membro do grupo**. A cota de rateio referencia um membro, não um usuário arbitrário. | ✅ Resolvido |
| D-13 | Visibilidade do histórico para membro que entra em um grupo já existente (ver E-10) | ⏳ Adiado para **Functional Design** |
| D-14 | **Fatura de cartão unificada com conta a pagar**: a fatura fechada gera automaticamente uma conta a pagar (RF-59), em vez de viver num módulo separado | ✅ Decidido. Uma única visão de vencimentos reunindo fatura, PIX, boleto e fatura de serviço |
| D-15 | **Fechamento** (não vencimento) determina a fatura de destino de cada compra (RF-61) | ✅ Decidido. Confirma o comportamento real de cartão de crédito e mantém os valores compatíveis com o extrato do banco |
| D-16 | **Recorrência como pergunta no cadastro** da conta (RF-62), com contas recorrentes e avulsas no mesmo modelo | ✅ Decidido |
| D-17 | **Investimento: aportes + saldo manual** (RF-69, RF-71), sem controle de ativos, indexadores ou cotação | ✅ Decidido. Rendimento é derivado (`saldo − aportado`), não calculado a partir de taxas |
| D-18 | **Aporte conta como gasto** no balanço do mês (RF-76) | ✅ Decidido pelo usuário. Consequência: o balanço mede fluxo de caixa, não variação patrimonial — investir reduz o saldo do mês |
| D-19 | Mecanismo de geração das ocorrências de contas recorrentes (job agendado, geração sob demanda na consulta, ou híbrido) | ⏳ Adiado para **Functional Design** |
| D-20 | Momento e mecanismo do fechamento automático da fatura (RF-59) — job agendado ou cálculo derivado na leitura | ⏳ Adiado para **Functional Design** |
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
4. Um gasto de grupo aparecer para todos os membros com as cotas corretas, e a soma das cotas
   igualar o valor do gasto
5. A fatura mensal consolidar corretamente os lançamentos e puder ser marcada como paga
6. O schema for criado por migrations Flyway versionadas, com `ddl-auto: validate` passando
7. Os testes rodarem contra PostgreSQL via Testcontainers, incluindo PBT para divisão de parcelas e
   rateio
8. A infraestrutura (EC2 + PostgreSQL em container + volume EBS + security group) for provisionável
   a partir do Terraform versionado em `infra/terraform/`, sem passos manuais no console AWS
9. A visão de vencimentos reunir, ordenados por data, a fatura de cartão gerada automaticamente e
   as contas de PIX, boleto e serviço — com marcação de pagamento funcionando para todas
10. Uma compra lançada antes do fechamento aumentar a fatura em aberto, e uma lançada depois do
    fechamento cair na fatura seguinte, ainda que a atual não tenha vencido
11. Uma conta recorrente gerar as ocorrências de cada mês com valor ajustável no pagamento
12. Um objetivo de investimento acumular aportes, aceitar atualização manual de saldo, exibir
    rendimento implícito e progresso contra a meta, e funcionar também no escopo de grupo

---

## 10. Rastreabilidade

| Requisito | Origem |
|---|---|
| RF-01 a RF-05 | Question 1 (multi-usuário) + Question 17 (auth como requisito funcional) |
| RF-06 a RF-10 | Question 2 (texto livre do usuário sobre grupo) + Question 5 |
| RF-11, RF-13 a RF-17 | Questions 5, 6, 7 — **revisados** na generalização Casa → Grupo. RF-12 (compartilhamento avulso) removido: a Question 5 fora respondida como "Ambos" quando o grupo era exclusivamente doméstico; com grupos genéricos, o modelo colapsou em um só |
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
| RF-78 a RF-80 | Pedido do usuário: *"vou precisar de um documento também com endpoints para montar o front"* — formalizado como especificação OpenAPI 3.1, entregue após a Application Design |
| RF-45 a RF-54 | Rodada de esclarecimento sobre infraestrutura (deploy em AWS EC2, Terraform no mesmo repo, PostgreSQL na instância) |
| RNF-13 a RNF-17 | Mesma rodada — decorrências não-funcionais das decisões D-08, D-09 e D-10 |
| R-01 a R-04 | Análise de risco das decisões de infraestrutura |
