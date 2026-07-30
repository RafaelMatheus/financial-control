# Application Design Plan

**Stage**: INCEPTION - Application Design
**Timestamp**: 2026-07-30T16:11:59Z
**Status**: ✅ Concluído — todos os artefatos gerados

> **Escopo desta stage**: identificação de componentes, responsabilidades, assinaturas de método e
> camada de serviço. **Regras de negócio detalhadas ficam para a Functional Design** (por unidade,
> fase de Construction).

---

## 1. Contexto analisado

**Fontes**: `requirements.md` (revisão 7 — 95 RF ativos), `stories.md` (11 épicos, 63 histórias),
`personas.md` (1 persona, 4 contextos), `execution-plan.md`.

### Capacidades de negócio identificadas

| # | Capacidade | Origem |
|---|---|---|
| 1 | Identidade e controle de acesso | E1 (RF-01 a RF-05) |
| 2 | Grupos e composição de membros | E2 (RF-06 a RF-10) |
| 3 | Compartilhamento por visibilidade | E3 (RF-11, RF-16, RF-17) |
| 4 | Registro de gastos | E4 (RF-18 a RF-22) |
| 5 | Classificação por categoria | E7 (RF-36 a RF-38) |
| 6 | Cartões de crédito e ciclo de fatura | E5 (RF-23 a RF-28, RF-94 a RF-96) |
| 7 | Compras parceladas | E6 (RF-29 a RF-35) |
| 8 | Contas a pagar e vencimentos | E10 (RF-55 a RF-67) |
| 9 | Receitas | E8 (RF-39 a RF-41) |
| 10 | Orçamento por categoria | E9 (RF-42 a RF-44) |
| 11 | Objetivos de investimento | E11 (RF-68 a RF-77) |

### Complexidade do design

**Alta**, porém **reduzida na revisão 8**. Eram 11 capacidades com três questões estruturais
herdadas das jornadas; a remoção do rateio (D-27) extinguiu uma delas (J-01) e resolveu outra
(J-03), além de eliminar a entidade `Cota` do modelo.

---

## 2. Questões estruturais herdadas

Estas não são preferências de estilo — são decisões que mudam o esquema de dados. São o motivo
principal desta stage existir.

### 2.1 ~~J-01 — Onde a cota de rateio se ancora~~ — **QUESTÃO EXTINTA**

Descoberta ao escrever a jornada de compra parcelada em cartão de grupo, e **extinta na revisão 8**
com a remoção do rateio (D-27). Sem cotas, não há o que ancorar. A entidade `Cota` sai do modelo.

### 2.2 J-03 — "Total do grupo" e "total pessoal" divergem — **RESOLVIDA**

Resolvida na revisão 8 por **RF-97** e **D-28**: são duas grandezas distintas, apresentadas
separadamente e **nunca somadas**.

- **Total pessoal** — soma apenas os lançamentos de que o usuário consultante é dono
- **Total do grupo** — soma todos os lançamentos de escopo GRUPO daquele grupo, de qualquer dono

Resta apenas a decisão de **como a API expõe** as duas (Question 4).

### 2.3 D-03 — Estrutura de pacotes e camadas

Nenhuma estrutura existe hoje. Precisa ser decidida antes de qualquer código.

### 2.4 Coexistência de gastos pessoais e de grupo

**Reforçado pelo usuário durante esta stage**: *"cada usuário pode ter despesas de grupo ou
despesas pessoais"*.

Já especificado por **RF-11** (escopo PESSOAL ou GRUPO) e **RF-07** (participação em grupo é
opcional), e coberto pela história **H-09**. Registrado aqui porque tem **consequência direta sobre
o modelo de leitura**:

```
Consulta de gastos de agosto - Rafael (membro do grupo "Apto 42")

  02/08  Cafe       PESSOAL  R$  12,00   dono: Rafael
  05/08  Mercado    GRUPO    R$ 400,00   dono: Ana
  07/08  Livro      PESSOAL  R$  89,00   dono: Rafael
  10/08  Energia    GRUPO    R$ 180,00   dono: Rafael

  Total pessoal (Rafael):  R$ 281,00   <- so os dele
  Total do grupo:          R$ 580,00   <- todos de escopo GRUPO
```

A mesma listagem mistura lançamentos pessoais e de grupo, e de donos diferentes. Duas decorrências:

1. Todo lançamento carrega seu **dono**; o valor é sempre um só, integral
2. **A totalização produz duas grandezas distintas** (RF-97, D-28) — o total pessoal soma só os
   lançamentos do consultante; o total do grupo soma todos os de escopo GRUPO. **Nunca se somam**

O ponto 2 é a regra mais fácil de implementar errado do sistema. Item de verificação obrigatória.

Isso conecta diretamente com a **Question 4** abaixo.

---

## 3. Questões de design

Respostas coletadas via tool e transcritas aqui.

## ~~Question 1~~ — EXTINTA na revisão 8
Perguntava onde a cota de rateio se ancorava (parcela ou compra). Sem rateio, a questão não existe.
O número não foi reaproveitado.

## Question 2 — Estrutura de pacotes (D-03)
Como organizar o código?

A) Por **feature** (`gasto/`, `cartao/`, `grupo/`), com camadas dentro de cada uma

B) Por **camada técnica** (`controller/`, `service/`, `repository/`, `domain/`)

C) **Arquitetura hexagonal** — `domain/`, `application/`, `adapter/in/web`, `adapter/out/persistence`

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Por feature**. Cada capacidade num pacote próprio com suas camadas dentro. O corte espelha as 5 unidades de trabalho do plano de execução.

## Question 3 — Modelo de escrita e leitura
As consultas do sistema (visão de vencimentos, orçado × realizado, posição de investimentos)
agregam dados de várias entidades. Como tratar?

A) **Mesmo modelo** para escrita e leitura — serviços retornam entidades mapeadas para DTO

B) **Modelos separados** — consultas complexas usam projeções ou views próprias, sem carregar agregados

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Mesmo modelo**. Serviços carregam entidades e mapeiam para DTO. Adequado ao volume de RNF-12; otimização pontual se alguma consulta ficar lenta.

## Question 4 — Como a API expõe total pessoal e total do grupo (J-03)
Ambos os totais precisam existir (RF-97). Como estruturar?

A) Cada lançamento traz seu **dono**, e a resposta inclui os **dois totais** calculados

B) **Endpoints separados** — um para a visão pessoal, outro para a visão do grupo

C) **Parâmetro de perspectiva** na consulta — `?perspectiva=pessoal|grupo`

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Cada lançamento traz seu **dono**, e a resposta inclui **ambos os totais** (`totalPessoal` e `totalGrupo`). Uma chamada monta a tela do mês.

## Question 5 — Fatura: entidade persistida ou projeção?
A fatura consolidada (RF-26) e a conta a pagar derivada dela (RF-59) precisam existir como registro
no banco, ou podem ser calculadas a partir dos lançamentos?

A) **Persistida** — a fatura é uma entidade com status próprio; o fechamento a materializa

B) **Calculada** — a fatura é uma projeção sobre os lançamentos; só o pagamento é persistido

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Persistida**. A fatura tem estado que não deriva dos lançamentos (fechamento, pagamento) e precisa suportar o bloqueio de RF-95.

## Question 6 — Identificadores
Que tipo de identificador as entidades usam?

A) **UUID** — gerado pela aplicação, seguro para expor na API, sem revelar volume

B) **Sequencial (BIGSERIAL)** — menor, índices mais eficientes, mas expõe volume e é enumerável

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **UUID**. Não revela volume nem permite enumeração — relevante num sistema multi-usuário com a extensão Security desligada.

---

## 4. Checklist de execução

Executado após aprovação deste plano.

### 4.1 Preparação
- [x] Consolidar as respostas da Seção 3

#### Decisões consolidadas
| Item | Decisão | ID |
|---|---|---|
| Estrutura de pacotes | **Por feature**, camadas dentro de cada pacote | D-03 ✅ |
| Modelo de leitura | **Mesmo modelo** — entidades mapeadas para DTO | D-29 |
| Exposição dos totais | Dono em cada item + `totalPessoal` e `totalGrupo` na resposta | D-30 |
| Fatura | **Entidade persistida** com ciclo de vida próprio | D-31 |
| Identificadores | **UUID** gerado pela aplicação | D-32 |

- [x] Confirmar a resolução de D-03 e da forma de exposição dos dois totais

### 4.2 Componentes
- [x] Identificar os agregados do domínio e suas fronteiras
- [x] Definir responsabilidade de cada componente
- [x] Definir interfaces expostas por cada componente
- [x] Gerar `application-design/components.md`

### 4.3 Métodos
- [x] Definir assinaturas de método por componente (sem regra de negócio detalhada)
- [x] Definir tipos de entrada e saída
- [x] Gerar `application-design/component-methods.md`

### 4.4 Serviços
- [x] Definir os serviços de aplicação e suas responsabilidades
- [x] Definir os pontos de orquestração e as fronteiras transacionais
- [x] Gerar `application-design/services.md`

### 4.5 Dependências
- [x] Construir a matriz de dependências entre componentes
- [x] Definir os padrões de comunicação
- [x] Diagramar os fluxos de dados principais
- [x] Gerar `application-design/component-dependency.md`

### 4.6 Contrato de API
- [x] Definir os recursos REST e suas operações
- [x] Definir os schemas de request e response
- [x] Definir o formato consistente de erro (RNF-09)
- [x] Gerar `application-design/openapi.yaml` — **OpenAPI 3.1** (RF-78 a RF-80)

### 4.7 Consolidação e verificação
- [x] Gerar `application-design/application-design.md` consolidando os demais
- [x] Verificar que todo agregado tem componente e serviço correspondentes
- [x] Verificar que as invariantes de H-28 e H-29 têm dono definido
- [x] Verificar que RNF-05 (isolamento) é estrutural, não opcional por consulta
- [x] Verificar cobertura das 63 histórias
- [x] Atualizar `aidlc-state.md`, `audit.md` e `research-log.md`

---

## 5. Artefatos obrigatórios

Exigidos pelo Step 3 da regra `inception/application-design.md`:

- [x] `components.md`
- [x] `component-methods.md`
- [x] `services.md`
- [x] `component-dependency.md`
- [x] `application-design.md` (consolidação)

**Adicional deste projeto**: `openapi.yaml` — exigido por RF-78 e prometido ao usuário como
entregável para o desenvolvimento do front-end.

---

## 6. Fora do escopo desta stage

- Regras de negócio detalhadas → **Functional Design**
- Mecanismo de autenticação → **NFR Requirements** (D-02)
- Schema SQL e migrations → **Code Generation**
- Dimensionamento de infraestrutura → **Infrastructure Design**
