# Story Generation Plan

**Stage**: INCEPTION - User Stories - Part 1 (Planejamento)
**Timestamp**: 2026-07-30T16:11:59Z
**Papel assumido**: Product Owner
**Status**: ✅ Plano aprovado · Parte 2 executada — todos os passos concluídos

> **Mecanismo de resposta**: por instrução do usuário, as questões são apresentadas por tool
> interativo e transcritas neste documento. Ver nota em `requirement-verification-questions.md`.

---

## 1. Escopo

**Fonte**: `aidlc-docs/inception/requirements/requirements.md` (revisão 6, aprovada).

**Coberto por histórias** — 67 requisitos de domínio:

| Área | Requisitos |
|---|---|
| Usuários e autenticação | RF-01 a RF-05 |
| Grupo | RF-06 a RF-10 |
| Compartilhamento e rateio | RF-11, RF-13 a RF-17 |
| Gastos | RF-18 a RF-22 |
| Cartões de crédito | RF-23 a RF-28 |
| Compras parceladas | RF-29 a RF-35 |
| Categorias | RF-36 a RF-38 |
| Receitas | RF-39 a RF-41 |
| Orçamento por categoria | RF-42 a RF-44 |
| Contas a pagar | RF-55 a RF-67 |
| Investimentos | RF-68 a RF-77 |

**Fora do escopo das histórias** — permanecem como requisitos técnicos: RF-45 a RF-54
(infraestrutura), RF-78 a RF-80 (contrato de API), RF-81 a RF-93 (CI/CD). Justificativa em
`user-stories-assessment.md`.

---

## 2. Abordagens de quebra — trade-offs

O AI-DLC exige apresentar as opções de organização das histórias. Análise de cada uma **para este
projeto especificamente**:

### 2.1 Feature-Based (por funcionalidade)
Histórias agrupadas por capacidade do sistema: Gastos, Cartões, Grupos, Contas a Pagar…

- **A favor**: mapeia 1:1 com as seções dos requisitos, facilitando rastreabilidade. Alinha bem com
  a decomposição em unidades de trabalho que virá depois.
- **Contra**: fragmenta jornadas que cruzam funcionalidades. Lançar uma compra parcelada num cartão
  de grupo toca Cartões, Parcelamento, Grupo e Rateio — a história ficaria repartida em quatro.

### 2.2 User Journey-Based (por jornada)
Histórias seguem o fluxo real: "registrar um gasto do mês", "fechar as contas do mês"…

- **A favor**: expõe exatamente os vazios que o assessment identificou — quem faz o quê em cada
  ponto do fluxo. Bom para quem vai construir o front, que pensa em telas e sequências.
- **Contra**: uma jornada costuma virar história grande demais, ferindo o "Small" do INVEST.
  Rastrear qual requisito está coberto fica mais difícil.

### 2.3 Persona-Based (por tipo de usuário)
Histórias agrupadas por quem as executa: administrador de grupo, membro, usuário individual.

- **A favor**: torna as diferenças de permissão impossíveis de ignorar — é justamente onde os
  requisitos são omissos (RF-16 combinado com RF-24 e RF-27).
- **Contra**: duplica histórias que são idênticas entre personas. Se no fim houver só uma persona
  real com variações de contexto, a divisão vira artifício.

### 2.4 Domain-Based (por contexto de negócio)
Agrupamento por subdomínio: Identidade e Acesso, Lançamentos, Crédito, Planejamento.

- **A favor**: aproxima as histórias das futuras fronteiras de agregado, ajudando a Application
  Design e a Units Generation.
- **Contra**: é um recorte técnico apresentado como recorte de negócio; o usuário final não pensa
  em "contexto de Crédito".

### 2.5 Epic-Based (épicos com sub-histórias)
Hierarquia de dois níveis: épico por área, histórias pequenas dentro dele.

- **A favor**: com 67 requisitos, dá estrutura navegável sem perder granularidade. Épicos servem de
  candidatos naturais a unidade de trabalho.
- **Contra**: mais estrutura para manter; risco de o épico virar só um rótulo sem conteúdo próprio.

### 2.6 Híbrido — Épicos por área + jornadas transversais *(recomendação)*
Épicos organizados por área funcional, com histórias dentro deles, **mais** um conjunto pequeno de
histórias de jornada para os fluxos que cruzam áreas (lançar compra parcelada em cartão de grupo,
fechar o mês).

- **A favor**: preserva a rastreabilidade requisito↔história e ainda captura as costuras entre
  áreas, que é onde os vazios estão. Os épicos alimentam a decomposição em unidades.
- **Contra**: exige critério explícito para decidir quando algo é história de área ou de jornada —
  proposta: **vira jornada apenas quando o fluxo atravessa três ou mais áreas**.

---

## 3. Questões de planejamento

Respostas coletadas via tool e transcritas aqui.

## Question 1 — Abordagem de quebra
Como organizar as histórias?

A) Híbrido — épicos por área + jornadas transversais (fluxo que cruza 3+ áreas vira jornada)

B) Epic-Based — épicos por área, apenas

C) Feature-Based — lista plana por funcionalidade

D) User Journey-Based — organizadas por fluxo do usuário

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Híbrido. Critério de corte: um fluxo vira história de jornada apenas quando atravessa **três ou mais áreas**.

## Question 2 — Personas
Os requisitos sugerem papéis distintos, mas não confirmam se o sistema os trata de forma diferente.
Como modelar?

A) Uma persona só — "usuário" — com o contexto (dono do gasto, membro do grupo) variando por situação

B) Administrador de grupo + Membro — quem cria o grupo tem poderes que os demais não têm

C) Três personas — Administrador de grupo, Membro e Usuário individual (sem grupo)

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Uma persona só ("usuário")**. Não há hierarquia: quem cria o grupo não ganha poder extra. Coerente com RF-16, que já permite a qualquer membro editar gastos do grupo. O que varia é o contexto (autor do gasto, membro do grupo, proprietário do cartão), não o papel.

## Question 3 — Granularidade
Qual o tamanho alvo de cada história?

A) Uma operação por história ("cadastrar gasto", "editar gasto", "excluir gasto" = 3 histórias)

B) Um recurso por história ("gerenciar gastos" cobrindo CRUD = 1 história com vários critérios)

C) Misto — CRUD simples agrupado numa história; regras de negócio complexas em histórias próprias

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Misto. CRUD simples agrupado numa história; cada regra de negócio relevante ganha história própria.

## Question 4 — Formato dos critérios de aceitação
Como escrever os critérios?

A) Gherkin (Dado/Quando/Então) — verboso, mas mapeia direto para testes automatizados

B) Lista de asserções — conciso, formato de checklist

C) Gherkin nas histórias com regra de negócio; lista nas de CRUD simples

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Misto. Gherkin (Dado/Quando/Então) nas histórias com regra de negócio; lista de asserções nas de CRUD simples. Alinhado com a granularidade escolhida na Question 3.

## Question 5 — Casos de borda em aberto
Quatro casos de borda continuam sem tratamento definido: E-03 (compra no exato dia do fechamento),
E-10 (membro entra em grupo com histórico), E-12 (lançamento retroativo em fatura fechada), E-13
(exclusão de compra depois de a fatura virar conta a pagar). O que fazer?

A) Resolver agora, com perguntas durante a escrita das histórias

B) Manter adiados para a Functional Design, apenas registrando nas histórias como pontos abertos

C) Resolver os de fatura (E-03, E-12, E-13) agora; deixar E-10 para depois

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Resolver agora. Os quatro casos foram decididos nesta rodada; ver Seção 3.1.

## Question 6 — Priorização das histórias
Cada história deve indicar prioridade de entrega?

A) Sim — MoSCoW, herdando a prioridade dos requisitos (M/S/C)

B) Sim, e também marcar quais compõem um "núcleo mínimo utilizável"

C) Não — prioridade é decisão da Workflow Planning e da Units Generation

X) Other (please describe after [Answer]: tag below)

[Answer]: B — MoSCoW herdado dos requisitos **mais** marcação de núcleo mínimo utilizável.

---

## 3.1 Resolução dos casos de borda

Decididos nesta rodada. Entram como critérios de aceitação das histórias correspondentes e são
retropropagados para `requirements.md`.

### E-03 — Compra no exato dia do fechamento
**Decisão**: vai para a **fatura seguinte**. O fechamento ocorre no início do dia, então o próprio
dia do fechamento já pertence ao próximo ciclo.

```
Cartao fecha dia 28

  27/07  ->  fatura de agosto
  28/07  ->  fatura de setembro   <- dia do fechamento
  29/07  ->  fatura de setembro
```

**Impacto**: fecha a decisão **D-04**, que estava adiada para a Functional Design. Torna RF-25 e
RF-61 completamente especificados — o corte é `dataCompra < diaFechamento`, exclusivo.

### E-12 — Lançamento retroativo em fatura já fechada
**Decisão**: a fatura é **reaberta e recalculada**, com a compra alocada na competência correta pela
data. **Exceto** se a fatura já estiver PAGA — ver E-13.

### E-13 — Alteração que afeta fatura já PAGA
**Decisão**: **bloquear a operação**. Vale tanto para lançamento retroativo (E-12) quanto para
exclusão de compra de uma fatura paga.

**Justificativa**: uma fatura paga é fato consumado — o dinheiro saiu e o valor bate com o extrato
do banco. Alterá-la produziria divergência com a realidade.

**Saída para o usuário que errou**: desmarcar a fatura como paga, corrigir, e marcar como paga
novamente. A operação fica explícita e consciente, em vez de acontecer como efeito colateral.

**Impacto**: RF-27 ganha a operação inversa (desmarcar pagamento). RF-57 idem, para contas a pagar.

### E-10 — Membro entra em grupo com histórico
**Decisão**: enxerga **todo o histórico** do grupo, inclusive o período anterior à sua entrada.

**Consequência**: visibilidade e rateio ficam desacoplados — o membro **vê** gastos antigos mas
**não tem cota** neles. As telas de total do grupo e de "meus gastos" precisam refletir essa
distinção.

**Impacto**: fecha a decisão **D-13**, que estava adiada para a Functional Design.

---

## 4. Checklist de execução (Parte 2)

Executado somente após aprovação deste plano.

### 4.1 Preparação
- [x] Carregar `requirements.md` (revisão 6) e este plano aprovado
- [x] Consolidar as respostas da Seção 3 nas decisões de formato
- [x] Confirmar a abordagem de quebra escolhida na Question 1

### 4.1.1 Decisões consolidadas
| Item | Decisão |
|---|---|
| Organização | Épicos por área + jornadas para fluxos que cruzam 3+ áreas |
| Personas | Uma só — "usuário", sem hierarquia |
| Granularidade | Mista — CRUD agrupado; regra de negócio em história própria |
| Critérios | Gherkin nas regras; lista de asserções no CRUD |
| Prioridade | MoSCoW herdado + marcação de núcleo mínimo |
| Casos de borda | E-03, E-10, E-12 e E-13 resolvidos (Seção 3.1) |

### 4.2 Personas
- [x] Identificar as personas conforme a Question 2
- [x] Para cada persona: nome, descrição, motivações, o que ela **pode** e **não pode** fazer
- [x] Mapear cada persona aos requisitos que a envolvem
- [x] Gerar `aidlc-docs/inception/user-stories/personas.md`

### 4.3 Histórias — por área
- [x] Usuários e autenticação (RF-01 a RF-05)
- [x] Grupo (RF-06 a RF-10)
- [x] Compartilhamento e rateio (RF-11, RF-13 a RF-17)
- [x] Gastos (RF-18 a RF-22)
- [x] Cartões de crédito (RF-23 a RF-28)
- [x] Compras parceladas (RF-29 a RF-35)
- [x] Categorias (RF-36 a RF-38)
- [x] Receitas (RF-39 a RF-41)
- [x] Orçamento por categoria (RF-42 a RF-44)
- [x] Contas a pagar (RF-55 a RF-67)
- [x] Investimentos (RF-68 a RF-77)

### 4.4 Histórias transversais
- [x] Identificar os fluxos que cruzam três ou mais áreas
- [x] Escrever as histórias de jornada correspondentes (se a Question 1 for A)

### 4.5 Verificação de qualidade
- [x] **INVEST** — cada história é Independent, Negotiable, Valuable, Estimable, Small, Testable
- [x] Cada história tem critérios de aceitação no formato definido na Question 4
- [x] Cada história referencia os requisitos que cobre
- [x] Cada história indica a persona que a executa
- [x] **Cobertura**: todos os 67 requisitos de domínio aparecem em pelo menos uma história
- [x] Nenhuma história inventa comportamento fora dos requisitos aprovados
- [x] Casos de borda E-03, E-10, E-12 e E-13 refletidos nos critérios de aceitação (Seção 3.1)

### 4.6 Artefatos e fechamento
- [x] Gerar `aidlc-docs/inception/user-stories/stories.md`
- [x] Gerar matriz de rastreabilidade requisito ↔ história
- [x] Atualizar `aidlc-docs/aidlc-state.md`
- [x] Registrar em `aidlc-docs/audit.md`
- [x] Adicionar entrada no `aidlc-docs/research-log.md`
- [x] Apresentar mensagem de conclusão e aguardar aprovação

---

## 5. Artefatos obrigatórios

Exigidos pelo Step 4 da regra `inception/user-stories.md`:

- [x] `stories.md` — histórias seguindo INVEST
- [x] `personas.md` — arquétipos de usuário com características
- [x] Critérios de aceitação em cada história
- [x] Personas mapeadas às histórias

---

## 6. Fora do escopo desta stage

Conforme Step 11 da regra:

- Estimativas de esforço ou pontos
- Planejamento de sprint ou cronograma
- Decisões de implementação técnica
- Decomposição em unidades de trabalho (é da Units Generation)
