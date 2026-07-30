# Unit of Work Plan

**Stage**: INCEPTION - Units Generation - Part 1 (Planejamento)
**Timestamp**: 2026-07-30T16:11:59Z
**Status**: ✅ Concluído — Partes 1 e 2 executadas

> **Definição**: uma unidade de trabalho é um agrupamento lógico de histórias para fins de
> desenvolvimento. Este projeto é um **monolito single-module** — as unidades são **módulos
> lógicos**, não serviços implantáveis separadamente.

---

## 1. Contexto

**Fontes**: `requirements.md` rev. 8 (93 RF) · `stories.md` (57 histórias + 3 jornadas) ·
`components.md` (12 componentes) · `component-dependency.md` (grafo) · `execution-plan.md`.

### O que já está decidido

| Item | Origem |
|---|---|
| Monolito single-module | Estado do repositório; nenhuma decisão o alterou |
| Estrutura de pacotes por feature | D-03 |
| 12 componentes, 15 entidades, 12 agregados | `components.md` |
| Decomposição prevista em 5 unidades | `execution-plan.md` §4 — **expectativa, não decisão** |
| Núcleo mínimo = 28 histórias | Marcação feita nas User Stories |

---

## 2. A divergência que esta stage precisa resolver

O plano de execução propôs cinco unidades nesta ordem:

```
U1 Fundacao      -> U2 Lancamentos -> U3 Credito -> U4 Planejamento
U5 Infraestrutura   (paralelizavel)
```

**O grafo de dependências da Application Design contradiz essa ordem.** O componente `gasto`
(previsto para U2) depende de `cartao` e `fatura` (previstos para U3):

```
gasto --> cartao     um gasto no cartao precisa do cartao cadastrado
gasto --> fatura     e da competencia calculavel (RF-25)
```

Um gasto **à vista** não tem essa dependência; um gasto **em cartão**, sim.

### Saídas possíveis

**(A) Dividir o componente `gasto` em duas etapas**
Gasto à vista entra em U2; a integração com cartão entra em U3, junto com `cartao` e `fatura`.
Preserva a ordem conceitual do plano.

```
U1 Fundacao     usuario, grupo, common
U2 Lancamentos  categoria, gasto (a vista apenas)
U3 Credito      cartao, fatura, conta, compra, gasto (integracao com cartao)
U4 Planejamento receita, orcamento, investimento
```

- ✅ Cada unidade entrega algo utilizável — U2 já permite registrar gastos à vista
- ⚠️ O componente `gasto` é tocado em duas unidades, exigindo coordenação

**(B) Antecipar `cartao` e `fatura` para antes dos lançamentos**
Inverte U2 e U3. Cada componente é implementado numa única unidade.

```
U1 Fundacao     usuario, grupo, common
U2 Credito      cartao, fatura, conta
U3 Lancamentos  categoria, gasto, compra
U4 Planejamento receita, orcamento, investimento
```

- ✅ Nenhum componente é tocado em duas unidades
- ⚠️ U2 entrega cartões e faturas **sem nenhum lançamento para consolidar** — só é demonstrável
  ao fim de U3

**(C) Fundir lançamentos e crédito numa unidade só**
Elimina a divergência ao eliminar a fronteira.

```
U1 Fundacao        usuario, grupo, common
U2 Lancamentos e   categoria, gasto, cartao, compra, fatura, conta
   Credito
U3 Planejamento    receita, orcamento, investimento
```

- ✅ Fronteiras naturais, sem componente dividido
- ⚠️ U2 fica grande — 6 componentes e a maior parte da complexidade do sistema

---

## 3. Questões de decomposição

Respostas coletadas via tool e transcritas aqui.

## Question 1 — Resolução da divergência
Como resolver o conflito entre a ordem do plano e o grafo de dependências?

A) Dividir o componente `gasto` — à vista em U2, integração com cartão em U3

B) Antecipar `cartao` e `fatura` — inverter U2 e U3

C) Fundir lançamentos e crédito numa unidade só

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Dividir o componente `gasto`**. Gasto à vista em U2; a integração com cartão em U3.
Preserva a ordem conceitual do plano e faz cada unidade terminar com algo demonstrável.

## Question 2 — Critério de agrupamento
Qual critério define a fronteira entre unidades?

A) **Dependência técnica** — uma unidade agrupa componentes que dependem entre si e podem ser
   implementados juntos

B) **Capacidade de negócio** — uma unidade entrega uma capacidade completa e demonstrável ao
   usuário, mesmo que isso implique mais coordenação

C) **Tamanho equilibrado** — unidades de esforço semelhante, facilitando o acompanhamento

X) Other (please describe after [Answer]: tag below)

[Answer]: B — **Capacidade de negócio** (default adotado pelo AI-DLC e comunicado ao usuário).
Decorre logicamente da resposta à Question 1: dividir o componente `gasto` só faz sentido se o
critério for entregar capacidade demonstrável, aceitando mais coordenação em troca.

## Question 3 — Infraestrutura como unidade
A infraestrutura (Terraform, Dockerfile, GitHub Actions — RF-45 a RF-54, RF-81 a RF-93) deve ser
uma unidade própria?

A) **Unidade própria e paralelizável** — não depende de nenhuma unidade de domínio; pode ser feita
   a qualquer momento

B) **Última unidade** — depois que o domínio estiver pronto, quando já se sabe o que implantar

C) **Primeira unidade** — pipeline pronto antes do código, para que cada unidade seguinte já nasça
   com CI verde

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Unidade própria e paralelizável** (default adotado pelo AI-DLC e comunicado ao
usuário). Mantém o que o `execution-plan.md` já previa. **Recomendação registrada**: executá-la
cedo, para que as unidades seguintes já nasçam com CI rodando os testes.

## Question 4 — Escopo de entrega deste ciclo
Todas as unidades entram neste ciclo AI-DLC, ou apenas parte?

A) **Todas** — o ciclo entrega o sistema completo

B) **Somente o núcleo** — identidade, grupos, lançamentos, crédito e contas a pagar. Receitas,
   orçamento e investimentos ficam para um ciclo posterior

C) **Núcleo + infraestrutura** — o essencial já implantável, e o restante depois

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Todas as unidades neste ciclo**. O sistema completo.

## Question 5 — Granularidade
Quantas unidades no total?

A) **4 a 5 unidades** — como previsto no plano de execução

B) **2 a 3 unidades maiores** — menos gates de aprovação, ciclo mais rápido

C) **6 ou mais unidades menores** — mais pontos de verificação, ciclo mais longo

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **5 unidades** (default adotado pelo AI-DLC e comunicado ao usuário), conforme o
`execution-plan.md`. Decorre das respostas anteriores: dividir `gasto` mantém U2 e U3 separadas, e
incluir todo o escopo mantém U4 e U5.

---

## 3.1 Decomposição resultante

| Unidade | Componentes | Critério de conclusão |
|---|---|---|
| **U1 — Fundação** | `common`, `usuario`, `grupo` | Usuário se cadastra, autentica, cria grupo e adiciona membros; isolamento de dados funcionando |
| **U2 — Lançamentos** | `categoria`, `gasto` (à vista) | Usuário registra gastos à vista, pessoais e de grupo, e consulta com os dois totais |
| **U3 — Crédito** | `cartao`, `fatura`, `conta`, `compra`, `gasto` (integração com cartão) | Compra parcelada gera parcelas nas faturas corretas; visão de vencimentos consolidada |
| **U4 — Planejamento** | `receita`, `orcamento`, `investimento` | Balanço do período, acompanhamento de orçamento e objetivos com meta |
| **U5 — Infraestrutura** | Terraform, Dockerfile, GitHub Actions | `terraform plan` executa; pipeline constrói imagem e implanta via SSM |

**Defaults adotados sem pergunta** (Questions 2, 3 e 5): decorrem logicamente das respostas às
Questions 1 e 4 e foram comunicados ao usuário no momento da adoção.

---

## 4. Checklist de execução (Parte 2)

Executado após aprovação deste plano.

### 4.1 Preparação
- [x] Consolidar as respostas da Seção 3
- [x] Confirmar a resolução da divergência (Question 1)

### 4.2 Definição das unidades
- [x] Definir cada unidade: nome, propósito, componentes, entidades
- [x] Definir os critérios de conclusão de cada unidade
- [x] Documentar a **estratégia de organização de código** (obrigatório — greenfield)
- [x] Gerar `aidlc-docs/inception/application-design/unit-of-work.md`

### 4.3 Dependências
- [x] Construir a matriz de dependências entre unidades
- [x] Identificar o caminho crítico e as oportunidades de paralelização
- [x] Definir os pontos de coordenação e a estratégia de teste de integração
- [x] Gerar `aidlc-docs/inception/application-design/unit-of-work-dependency.md`

### 4.4 Mapa de histórias
- [x] Atribuir **cada uma das 57 histórias e 3 jornadas** a uma unidade
- [x] Verificar que nenhuma história ficou sem unidade
- [x] Verificar que nenhuma história está em duas unidades
- [x] Gerar `aidlc-docs/inception/application-design/unit-of-work-story-map.md`

### 4.5 Validação
- [x] Verificar que as fronteiras respeitam o grafo de dependências de componentes
- [x] Verificar que cada unidade tem critério de conclusão verificável
- [x] Verificar cobertura: 93 RF ativos e 60 histórias atribuídos
- [x] Confirmar que cada unidade é implementável na ordem proposta

### 4.6 Fechamento
- [x] Atualizar `aidlc-docs/aidlc-state.md` com a decomposição definitiva
- [x] Registrar em `aidlc-docs/audit.md`
- [x] Adicionar entrada no `aidlc-docs/research-log.md`
- [x] Apresentar mensagem de conclusão e aguardar aprovação

---

## 5. Artefatos obrigatórios

Exigidos pelo Step 2 da regra `inception/units-generation.md`:

- [x] `unit-of-work.md` — definições e responsabilidades das unidades
- [x] `unit-of-work-dependency.md` — matriz de dependências
- [x] `unit-of-work-story-map.md` — mapeamento histórias → unidades
- [x] Estratégia de organização de código documentada (greenfield)
- [x] Fronteiras e dependências validadas
- [x] Todas as histórias atribuídas

---

## 6. Fora do escopo desta stage

- Regras de negócio detalhadas → **Functional Design**, por unidade
- Escolha de stack e autenticação → **NFR Requirements**
- Estimativas de esforço em tempo → não faz parte do método
- Ordem de execução dentro de cada unidade → **Code Generation**
