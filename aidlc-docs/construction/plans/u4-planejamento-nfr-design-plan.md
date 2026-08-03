# Plano de NFR Design — U4 Planejamento

> A **última NFR Design do ciclo**. Nasce sem seção de respostas (O-32) — quinta stage seguida.

---

## 1. Pré-requisito

Como em U2 e U3: U4 **não tem NFR Requirements própria**. Ela roda uma vez, em U1. Insumo: os 14
NFRs de U1, as NFR Designs de U2 e U3, e a Functional Design de U4.

---

## 2. O que esta stage tem para resolver

U4 é a primeira unidade cuja **leitura principal depende de dados de outras unidades**. O
"realizado" do orçamento é calculado sobre `gasto` (U2) e `parcela` (U3) — e U4 não é dona de
nenhuma das duas.

| # | Problema | Origem |
|---|---|---|
| 1 | Onde vive a leitura **entre unidades**? U4 lendo tabelas de U2 e U3 | `business-logic-model.md` §2.1 |
| 2 | `totalAportado` é derivado ou persistido? | O mesmo eixo de D-64 e D-75 |
| 3 | Excluir ou editar um aporte mexe no `saldoAtual`? | D-80 resolveu a criação, não a exclusão |
| 4 | O acompanhamento calcula o realizado **por categoria** — N consultas ou uma? | RN-O05, H-40 |

**É a última oportunidade do ciclo de fixar o padrão de leitura entre unidades.** O que ficar aqui é
o que um U5 de domínio herdaria.

---

## 3. Passos

### Análise

- [x] **Passo 1** — Ler os 14 NFRs de U1 e as NFR Designs de U2 e U3
- [x] **Passo 2** — Ler a Functional Design de U4 e extrair o que ela endereçou a esta stage
- [x] **Passo 3** — Avaliar as **5 categorias obrigatórias**

### Esclarecimento

- [x] **Passo 4** — Formular as questões
- [x] **Passo 5** — Coletar as respostas e criar a §5
- [x] **Passo 6** — Reanalisar

### Design

- [x] **Passo 7** — `nfr-design-patterns.md`
- [x] **Passo 8** — `logical-components.md`, com o **inventário final** do ciclo
- [x] **Passo 9** — Diagramas validados
- [x] **Passo 10** — Registrar as decisões (continua de D-80) e **fechar o inventário de tudo o que
      quebra com escala horizontal**, agora que é a última unidade

---

## 4. Questões

### Q1 — Onde vive a leitura entre unidades?

O realizado do orçamento soma `gasto` e `parcela`. U4 não é dona de nenhuma das duas tabelas.

Há precedente parcial: em U3, o adaptador de `fatura` lê `gasto`, `parcela` e `conta_a_pagar` por
consulta nativa, com a justificativa de que a **tabela** é contrato estável e a entidade não. Mas
aquilo foi dentro da mesma unidade, e aqui atravessa uma fronteira maior.

### Q2 — `totalAportado`: derivado ou persistido?

D-75 removeu `Fatura.valorTotal` porque a invariante dependia de oito caminhos de escrita lembrarem
de recalcular. `totalAportado` tem a mesma forma — é a soma dos aportes — mas bem menos caminhos.

### Q3 — Excluir um aporte mexe no `saldoAtual`?

D-80 decidiu que **aportar soma ao saldo**. A recíproca não foi decidida: aportei R$ 500 por engano,
o saldo subiu R$ 500. Ao excluir o aporte, o saldo desce?

### Q4 — O acompanhamento calcula o realizado por categoria: N consultas ou uma?

Um mês com 10 categorias orçadas. Com uma consulta por orçamento, são 10 idas ao banco — e o
`ArquiteturaTest` não pega N+1, porque ele é estrutural.

---

## 5. Respostas

| | Resposta | Decisão |
|---|---|---|
| **Q1** | **Porta exposta por quem é dono do dado.** `ConsultaDeRealizado` vive no domínio de `gasto`; U4 apenas consome | **D-81** |
| **Q2** | `totalAportado` **calculado na leitura** | **D-82** |
| **Q3** | Excluir aporte **subtrai do saldo** — simétrico ao aportar | **D-83** |
| **Q4** | **Uma consulta agrupada** por categoria, no máximo uma por base | **D-84** |

### 5.1 Reanálise (Passo 6)

**Q1 tem uma implicação que a pergunta não mostrou, e ela precisa de decisão.** A porta vive no
domínio de `gasto` (U2), mas o parâmetro `BaseDoRealizado` é conceito de **U4** — foi criado por
D-77. Uma porta de U2 que importa um tipo de U4 inverte a seta de dependência: a unidade mais antiga
passaria a depender da mais nova.

*Resolução*: **`BaseDoRealizado` vai para `common/dominio`**, ao lado de `Escopo` e `Competencia`.
Ele é vocabulário compartilhado — U2 e U3 são quem **sabem as duas datas**, U4 é quem **escolhe
qual usar**. Nenhuma das duas é dona sozinha.

> É o mesmo lugar e o mesmo motivo de `Escopo`, que nasceu em U1 sem nenhum consumidor porque
> `Visibilidade` precisava conhecê-lo. A diferença é que aqui o tipo nasce **em U4** e retrocede para
> `common` — a primeira vez no ciclo que um conceito muda de casa para baixo.

**Q1 é a decisão mais duradoura desta stage.** Ela fixa o padrão de leitura entre unidades para
qualquer unidade futura: **quem sabe somar um dado é quem é dono dele**. O ganho concreto não é de
organização — é que o **filtro de visibilidade continua sendo aplicado por quem o escreveu**. A
alternativa recusada obrigaria U4 a reimplementar o predicado de RN-V01, que é precisamente onde
erros de isolamento nascem.

Vale o contraste com U3: lá, o adaptador de `fatura` lê `gasto` e `parcela` por consulta nativa. A
diferença é que aquilo acontece **dentro da mesma unidade**, entre features que foram desenhadas
juntas. Atravessar a fronteira de unidade é outro grau de acoplamento, e mereceu outro tratamento.

**Q2 aplica D-75 pela terceira vez no ciclo** — depois de `Fatura.valorTotal` e antes dele, do total
de gastos de U2. O critério consolidou-se: **se o número é uma soma, calcule; se é um fato, guarde.**
`totalAportado` é soma; `saldoAtual` é fato declarado pelo usuário.

**Q3 fecha a simetria que D-80 deixou pela metade.** Sem ela, excluir um aporte de R$ 500 faria o
rendimento **subir** R$ 500 do nada. As duas operações passam a ser inversas de verdade.

**Q4 evita um N+1 escrito de propósito.** Registrado que o `ArquiteturaTest` **não pegaria** este
defeito: ele é estrutural, e N+1 é comportamental. Nem toda garantia cabe numa regra de arquitetura.

**Nenhuma contradição entre as respostas.**

---

## 6. Categorias obrigatórias

| Categoria | Aplicável | Justificativa |
|---|---|---|
| **Resilience** | Não | Nenhuma integração externa. U4 não acrescenta job, fila nem chamada fora do processo |
| **Scalability** | Parcial | RNF-12 continua. U4 **não acrescenta componente com estado** — a lista fechada do ciclo vai para `logical-components.md` |
| **Performance** | **Sim** | Q1 e Q4. O acompanhamento é a consulta mais cara da unidade |
| **Security** | Parcial | Nenhum mecanismo novo. As 4 entidades entram no padrão existente e o `ArquiteturaTest` as cobre — **e `Receita` é o primeiro caso de entidade com dono e sem escopo**, que exercita a metade do predicado que nunca foi exercitada sozinha |
| **Logical Components** | **Sim** | O inventário final do ciclo |

---

## 7. Riscos

| Risco | Tratamento |
|---|---|
| A leitura entre unidades virar acoplamento silencioso | Objeto de Q1. Qualquer que seja a resposta, ela vira **precedente** para todo o resto |
| `totalAportado` divergir dos aportes | Objeto de Q2. A lição de D-75 está a uma stage de distância |
| N+1 no acompanhamento | Objeto de Q4 |
| A última unidade herdar pressa | Não há unidade seguinte para corrigir o que ficar mal resolvido |
