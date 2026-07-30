# Componentes

**Stage**: INCEPTION - Application Design
**Timestamp**: 2026-07-30T16:11:59Z
**Base**: `requirements.md` revisão 8 (93 RF ativos) · `stories.md` (57 histórias + 3 jornadas)

> **Escopo**: identificação de componentes, responsabilidades e interfaces. **Regras de negócio
> detalhadas ficam para a Functional Design.**

---

## 1. Estrutura de pacotes (D-03)

Organização **por feature** — cada capacidade num pacote próprio, com suas camadas dentro. O corte
espelha as unidades de trabalho previstas no plano de execução.

```
com.rafaelmatheus.financialcontrol
|
+-- common/                    infraestrutura compartilhada
|     Escopo.kt                enum PESSOAL | GRUPO
|     Dinheiro.kt              value object sobre BigDecimal
|     Competencia.kt           value object ano-mes
|     ContextoUsuario.kt       usuario autenticado da requisicao
|     ErroHandler.kt           @RestControllerAdvice (RNF-09)
|     Visibilidade.kt          regra de escopo aplicavel a consultas
|
+-- usuario/                   U1  identidade
+-- grupo/                     U1  grupos e membros
+-- categoria/                 U2  classificacao
+-- gasto/                     U2  lancamentos avulsos
+-- receita/                   U4  entradas
+-- cartao/                    U3  cartoes e ciclo
+-- compra/                    U3  compras parceladas
+-- fatura/                    U3  consolidacao mensal
+-- conta/                     U3  contas a pagar e recorrencia
+-- orcamento/                 U4  teto por categoria
+-- investimento/              U4  objetivos e aportes
```

Cada pacote de feature contém, quando aplicável: entidade(s), repositório, serviço, controller e
DTOs. A coluna `U*` indica a unidade de trabalho prevista.

---

## 2. Componentes de domínio

### 2.1 `usuario` — Identidade

| | |
|---|---|
| **Propósito** | Representar quem usa o sistema e ancorar todo o isolamento de dados |
| **Entidade raiz** | `Usuario` |
| **Requisitos** | RF-01 a RF-05 |
| **Unidade** | U1 |

**Responsabilidades**
- Cadastrar usuário com e-mail único e credencial
- Autenticar e fornecer o contexto do usuário da requisição
- Expor e atualizar dados de perfil

**Atributos principais**: `id: UUID`, `email`, `senhaHash`, `nome`, `criadoEm`

> O **mecanismo** de autenticação (JWT, sessão, OAuth2) é a decisão D-02, ainda aberta, adiada para
> a NFR Requirements. Este componente define o *contrato*, não a tecnologia.

---

### 2.2 `grupo` — Grupos e composição

| | |
|---|---|
| **Propósito** | Agrupar usuários que compartilham visibilidade de lançamentos |
| **Entidades** | `Grupo` (raiz), `MembroGrupo` |
| **Requisitos** | RF-06 a RF-10 |
| **Unidade** | U1 |

**Responsabilidades**
- Criar e renomear grupos
- Adicionar e remover membros, **sem hierarquia** entre eles
- Responder "de quais grupos este usuário é membro?" — base do filtro de visibilidade
- Preservar o histórico quando um membro sai

**Atributos**: `Grupo(id: UUID, nome, criadoEm)` · `MembroGrupo(id: UUID, grupo, usuario, entrouEm, saiuEm?)`

> `MembroGrupo` guarda `entrouEm` e `saiuEm` para preservar o histórico (RF-10). **Não** é usado
> para restringir visibilidade retroativa — E-10 determina que o membro enxerga todo o histórico.

---

### 2.3 `categoria` — Classificação

| | |
|---|---|
| **Propósito** | Classificar lançamentos para consulta e orçamento |
| **Entidade raiz** | `Categoria` |
| **Requisitos** | RF-36 a RF-38 |
| **Unidade** | U2 |

**Responsabilidades**
- CRUD de categorias, com nome único por usuário
- Bloquear exclusão de categoria com lançamentos vinculados, ou realocar (RF-37)
- Prover conjunto inicial no primeiro acesso (RF-38)

**Atributos**: `id: UUID`, `nome`, `usuario`

---

### 2.4 `gasto` — Lançamentos avulsos

| | |
|---|---|
| **Propósito** | Registrar uma saída de dinheiro, à vista ou em cartão, não parcelada |
| **Entidade raiz** | `Gasto` |
| **Requisitos** | RF-11, RF-16 a RF-22, RF-97 |
| **Unidade** | U2 |

**Responsabilidades**
- CRUD de gastos, com descrição, valor, data e categoria
- Definir escopo PESSOAL ou GRUPO e registrar o **dono**
- Associar forma de pagamento: à vista ou cartão (define se entra numa fatura)
- Consultar por período com filtros
- Totalizar em **duas grandezas distintas**: total pessoal e total do grupo (RF-97)

**Atributos**: `id: UUID`, `descricao`, `valor: Dinheiro`, `data: LocalDate`, `categoria`,
`dono: Usuario`, `escopo: Escopo`, `grupo?`, `cartao?`, `competencia?: Competencia`

> **Regra de propriedade (D-27)**: o valor pertence **integralmente ao dono**. Escopo GRUPO
> significa apenas que os membros enxergam o lançamento — não que dividem o valor.

---

### 2.5 `cartao` — Cartões de crédito

| | |
|---|---|
| **Propósito** | Representar um cartão e seu ciclo de fechamento e vencimento |
| **Entidade raiz** | `Cartao` |
| **Requisitos** | RF-23 a RF-25, RF-61 |
| **Unidade** | U3 |

**Responsabilidades**
- CRUD de cartões, com apelido, dia de fechamento e dia de vencimento
- Pertencer a um **usuário** ou a um **grupo** (RF-24)
- **Calcular a competência de fatura** de uma data de compra (RF-25, RF-61) — corte **exclusivo**:
  compra no dia do fechamento vai para a fatura seguinte (E-03)

**Atributos**: `id: UUID`, `apelido`, `diaFechamento: Int`, `diaVencimento: Int`,
`dono: Usuario`, `escopo: Escopo`, `grupo?`

> O cálculo de competência é a lógica mais delicada do componente. Fica como método do domínio,
> não do serviço, para ser testável isoladamente. O caso de fechamento em dia 29–31 (E-04) segue
> aberto — decisão D-04, Functional Design.

---

### 2.6 `compra` — Compras parceladas

| | |
|---|---|
| **Propósito** | Registrar uma compra dividida em N parcelas, e mantê-las consistentes |
| **Entidades** | `Compra` (raiz), `Parcela` |
| **Requisitos** | RF-29 a RF-35 |
| **Unidade** | U3 |

**Responsabilidades**
- Criar compra a partir de **valor da parcela + número de parcelas**, calculando o total (RF-29)
- **Gerar as N parcelas**, cada uma com sua competência (RF-30)
- Distribuir o **resíduo de centavos** — últimas parcela absorve (RF-31)
- Manter a invariante **`soma(parcelas) == valorTotal`** (RF-32) 🔬 **PBT**
- Editar apenas por inteiro, regenerando as parcelas (RF-33)
- Excluir a compra removendo todas as parcelas (RF-34)

**Atributos**: `Compra(id: UUID, descricao, valorParcela, numeroParcelas, valorTotal, dataCompra,
cartao, categoria, dono, escopo, grupo?)` · `Parcela(id: UUID, compra, posicao, valor, competencia)`

> 🔬 **Invariante para property-based testing** (H-28, H-29): para qualquer valor e qualquer número
> de parcelas, a soma fecha exatamente. **É a única área do sistema com aritmética monetária de
> divisão** — após a remoção do rateio na revisão 8.
>
> `Parcela` é entidade **filha**, não raiz de agregado: não existe sem a compra e não é editável
> isoladamente (RF-33).

---

### 2.7 `fatura` — Consolidação mensal

| | |
|---|---|
| **Propósito** | Consolidar os lançamentos de um cartão numa competência e controlar seu ciclo |
| **Entidade raiz** | `Fatura` |
| **Requisitos** | RF-26, RF-27, RF-28, RF-59, RF-60, RF-94 a RF-96 |
| **Unidade** | U3 |

**Responsabilidades**
- Consolidar gastos de cartão e parcelas de uma competência (RF-26)
- **Recalcular enquanto ABERTA**, a cada novo lançamento (RF-60)
- **Fechar** na data de fechamento e gerar a conta a pagar correspondente (RF-59)
- Projetar faturas futuras com as parcelas comprometidas (RF-28)
- **Reabrir e recalcular** quando um lançamento retroativo a afeta, se não estiver paga (RF-96)
- **Bloquear** qualquer alteração quando já paga (RF-95)

**Atributos**: `id: UUID`, `cartao`, `competencia: Competencia`, `valorTotal: Dinheiro`,
`vencimento: LocalDate`, `status: StatusFatura`

**Ciclo de vida**

```
    ABERTA  <----------+
      |                | reabertura por lancamento
      | dia fechamento | retroativo, se nao paga (RF-96)
      v                |
    FECHADA -----------+
      |
      | conta a pagar quitada
      v
     PAGA  -> alteracoes bloqueadas (RF-95)
      |
      | desmarcar pagamento (RF-94)
      v
    FECHADA
```

> **Decisão de modelagem (D-31)**: a fatura é **persistida**, não calculada. Ela carrega estado que
> não deriva dos lançamentos — momento do fechamento e situação de pagamento — e precisa suportar o
> bloqueio de RF-95.
>
> **Ponto para a Functional Design**: o estado `PAGA` é a única fonte de verdade sobre pagamento, ou
> deriva da `ContaAPagar` vinculada? Persistir nos dois lugares cria duas verdades. A recomendação
> é **derivar** — a `ContaAPagar` carrega o pagamento e `Fatura.status` o projeta. Fica registrado
> como item de decisão.

---

### 2.8 `conta` — Contas a pagar e recorrência

| | |
|---|---|
| **Propósito** | Reunir numa visão única tudo que tem vencimento e status de pagamento |
| **Entidades** | `ContaAPagar` (raiz), `ContaRecorrente` (raiz) |
| **Requisitos** | RF-55 a RF-67, RF-94, RF-95 |
| **Unidade** | U3 |

**Responsabilidades**
- CRUD de contas com **vencimento próprio**, tipo e categoria (RF-55, RF-56)
- Manter status **EM_ABERTO** ou **PAGA**, com data de pagamento (RF-57)
- Permitir **desmarcar** o pagamento (RF-94)
- Apresentar a **visão consolidada de vencimentos** do período (RF-58)
- Receber a conta gerada pelo fechamento de fatura (RF-59)
- Gerenciar contas recorrentes e gerar suas ocorrências (RF-62, RF-63, RF-67)
- Permitir **ajustar o valor** da ocorrência no pagamento (RF-64)
- Marcar escopo PESSOAL ou GRUPO (RF-65)
- Consultar a vencer e vencidas (RF-66)

**Atributos**: `ContaAPagar(id: UUID, descricao, valor, vencimento, tipo: TipoConta, status,
dataPagamento?, categoria, dono, escopo, grupo?, fatura?, contaRecorrente?)`
`ContaRecorrente(id: UUID, descricao, valorBase, diaVencimento, frequencia, ativa, dono, escopo, grupo?, categoria)`

**`TipoConta`**: `FATURA_CARTAO` · `PIX` · `BOLETO` · `FATURA_SERVICO`

> Contas do tipo `FATURA_CARTAO` são **derivadas** — o valor vem da consolidação e não é editável
> diretamente (premissa P-11).
>
> **Ponto para a Functional Design**: o mecanismo de geração das ocorrências recorrentes — job
> agendado, cálculo derivado na consulta, ou híbrido — é a decisão **D-19**.

---

### 2.9 `receita` — Entradas

| | |
|---|---|
| **Propósito** | Registrar dinheiro que entra e permitir o balanço do período |
| **Entidade raiz** | `Receita` |
| **Requisitos** | RF-39 a RF-41 |
| **Unidade** | U4 |

**Responsabilidades**
- CRUD de receitas com descrição, valor e data
- Consultar por período
- Compor o balanço: receitas − gastos, **incluindo aportes como gasto** (RF-41, RF-76)

**Atributos**: `id: UUID`, `descricao`, `valor: Dinheiro`, `data: LocalDate`, `dono: Usuario`

> Receitas são **individuais** — não têm escopo de grupo (premissa P-05).

---

### 2.10 `orcamento` — Teto por categoria

| | |
|---|---|
| **Propósito** | Estabelecer limite mensal por categoria e acompanhar o realizado |
| **Entidade raiz** | `Orcamento` |
| **Requisitos** | RF-42 a RF-44 |
| **Unidade** | U4 |

**Responsabilidades**
- Definir, alterar e remover teto mensal por categoria
- Comparar orçado × realizado (RF-43)
- Sinalizar categorias estouradas, com o valor excedente (RF-44)

**Atributos**: `id: UUID`, `categoria`, `competencia: Competencia`, `valorTeto: Dinheiro`, `dono`

> **Ponto para a Functional Design**: o "realizado" conta o gasto de cartão pela **data da compra**
> ou pela **competência da fatura**? Questão **J-02**, levantada pela jornada de fechar o mês e
> ainda aberta.

---

### 2.11 `investimento` — Objetivos e aportes

| | |
|---|---|
| **Propósito** | Bolsos nomeados onde o usuário guarda dinheiro com propósito |
| **Entidades** | `ObjetivoInvestimento` (raiz), `Aporte` |
| **Requisitos** | RF-68 a RF-77 |
| **Unidade** | U4 |

**Responsabilidades**
- CRUD de objetivos, com meta e prazo **opcionais** (RF-68, RF-73, RF-74)
- Registrar aportes e acumular o total aportado (RF-69, RF-70)
- **Atualizar o saldo manualmente** (RF-71)
- Calcular o **rendimento implícito** = `saldo − aportado`, aceitando negativo (RF-72, E-14)
- Calcular progresso contra a meta e o **aporte mensal necessário** para o prazo (RF-73, RF-74)
- Pertencer a um grupo, com todos aportando (RF-75)
- Apresentar a posição consolidada (RF-77)

**Atributos**: `ObjetivoInvestimento(id: UUID, nome, meta?, prazoAlvo?, saldoAtual, dono, escopo,
grupo?)` · `Aporte(id: UUID, objetivo, valor, data, dono)`

> **Integração com o balanço (RF-76, D-18)**: o aporte é contabilizado como **gasto** no balanço do
> mês. O balanço mede fluxo de caixa, não variação patrimonial.

---

## 3. Componentes compartilhados — `common`

### 3.1 `Escopo`
Enum `PESSOAL | GRUPO`. Usado por `Gasto`, `Compra`, `Cartao`, `ContaAPagar`, `ContaRecorrente` e
`ObjetivoInvestimento`.

### 3.2 `Dinheiro`
Value object sobre `BigDecimal` com escala 2 e política de arredondamento explícita (RNF-01).
Encapsula soma, subtração e a divisão em parcelas com resíduo. **Nunca usar `Double` ou `Float`.**

### 3.3 `Competencia`
Value object ano-mês (`YearMonth`). Identifica a fatura e o período de orçamento.

### 3.4 `ContextoUsuario`
Expõe o usuário autenticado da requisição e os grupos de que é membro. **Fonte única do filtro de
visibilidade** — nenhuma consulta o resolve por conta própria.

### 3.5 `Visibilidade`
Componente que traduz o contexto em predicado de consulta. Aplica RF-03 e RNF-05: um registro é
visível se `dono == usuarioAtual` **ou** (`escopo == GRUPO` e `grupo ∈ gruposDoUsuario`).

> **Decisão estrutural**: o isolamento é responsabilidade **deste componente**, não de cada
> consulta. Nenhum repositório expõe método que retorne dados sem o predicado aplicado — assim,
> esquecer o filtro deixa de ser possível por construção, e não apenas por disciplina.

### 3.6 `ErroHandler`
`@RestControllerAdvice` com formato consistente de erro (RNF-09): código, mensagem acionável e
detalhes de validação.

---

## 4. Resumo

| Componente | Entidades | Unidade | Requisitos |
|---|---|---|---|
| `usuario` | Usuario | U1 | RF-01 a RF-05 |
| `grupo` | Grupo, MembroGrupo | U1 | RF-06 a RF-10 |
| `categoria` | Categoria | U2 | RF-36 a RF-38 |
| `gasto` | Gasto | U2 | RF-11, RF-16 a RF-22, RF-97 |
| `cartao` | Cartao | U3 | RF-23 a RF-25, RF-61 |
| `compra` | Compra, Parcela | U3 | RF-29 a RF-35 |
| `fatura` | Fatura | U3 | RF-26 a RF-28, RF-59, RF-60, RF-94 a RF-96 |
| `conta` | ContaAPagar, ContaRecorrente | U3 | RF-55 a RF-67 |
| `receita` | Receita | U4 | RF-39 a RF-41 |
| `orcamento` | Orcamento | U4 | RF-42 a RF-44 |
| `investimento` | ObjetivoInvestimento, Aporte | U4 | RF-68 a RF-77 |
| `common` | — | U1 | RF-03, RF-04, RNF-01, RNF-05, RNF-09 |

**11 componentes de feature + 1 compartilhado · 15 entidades · 12 agregados**

### Pontos deixados para a Functional Design

| # | Questão | Componente | ID |
|---|---|---|---|
| 1 | Fechamento em dia 29–31 com mês curto | `cartao` | D-04 |
| 2 | Base de cálculo do "realizado" do orçamento | `orcamento` | J-02 |
| 3 | Mecanismo de geração das ocorrências recorrentes | `conta` | D-19 |
| 4 | Mecanismo de fechamento automático da fatura | `fatura` | D-20 |
| 5 | `Fatura.status = PAGA` é persistido ou derivado da `ContaAPagar`? | `fatura` | 🆕 Novo |
