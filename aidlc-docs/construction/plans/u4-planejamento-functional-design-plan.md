# Plano de Functional Design — U4 Planejamento

> Fonte única de verdade da Functional Design de U4, a **última unidade do ciclo**.
>
> **Nasce sem seção de respostas** — criada depois de as respostas existirem (O-32).

---

## 1. Contexto

| | |
|---|---|
| **Componentes** | `receita`, `orcamento`, `investimento` |
| **Entidades** | `Receita`, `Orcamento`, `ObjetivoInvestimento`, `Aporte` |
| **Histórias** | H-36 a H-41, H-52 a H-60 (15) + **J-02** |
| **Requisitos** | RF-39 a RF-44, RF-68 a RF-77 (20) |
| **Depende de** | U1 e U2 sempre; **U3 apenas por causa de J-02** |
| **Bloqueia** | — última unidade |

**A menor das quatro unidades de domínio**, e a única que fecha uma jornada que atravessa todas as
outras.

### 1.1 O que U4 herda

| De U1 | De U2 | De U3 |
|---|---|---|
| `Dinheiro`, `Competencia`, `Escopo` | Porta por feature com filtro obrigatório (D-63) | `Parcela` com **data da compra e competência** disponíveis |
| `RepositorioComVisibilidade` | Projeção de leitura (D-65) | O padrão de guarda no adaptador (D-73) |
| `ContextoUsuario` | `ArquiteturaTest` (D-66) — já cobre as 4 entidades novas | O agendador, se algo periódico for preciso |

> A primeira coluna de U3 é a que importa: **U3 deixou as duas datas em cada parcela justamente
> para que U4 pudesse escolher.** É a preparação que J-02 exigia.

### 1.2 A última decisão em aberto do ciclo

| ID | Aberta desde | Assunto |
|---|---|---|
| **J-02** | User Stories | O "realizado" do orçamento conta o gasto de cartão pela **data da compra** ou pela **competência da fatura**? |

Depois dela, **nenhuma decisão do ciclo permanece adiada**.

---

## 2. Passos

### Análise

- [x] **Passo 1** — Ler a definição de U4, as 15 histórias, J-02 e os 20 requisitos
- [x] **Passo 2** — Ler o que U1, U2 e U3 deixaram como contrato
- [x] **Passo 3** — Levantar as ambiguidades (§3)

### Esclarecimento

- [x] **Passo 4** — Formular as questões
- [x] **Passo 5** — Coletar as respostas e criar a §5
- [x] **Passo 6** — Reanalisar

### Modelagem

- [x] **Passo 7** — `domain-entities.md`: as 4 entidades
- [x] **Passo 8** — `business-rules.md`: `RN-RC*` (receita), `RN-O*` (orçamento), `RN-I*` (investimento)
- [x] **Passo 9** — `business-logic-model.md`: balanço, orçado × realizado, aporte mensal necessário,
      rendimento implícito
- [x] **Passo 10** — **Resolver J-02** e modelar o realizado com a base escolhida
- [x] **Passo 11** — Diagramas Mermaid validados

### Verificação

- [x] **Passo 12** — Rastreabilidade das 15 histórias e de J-02
- [x] **Passo 13** — Alvos de property-based testing
- [x] **Passo 14** — Registrar as decisões (numeração continua de D-76) e **fechar J-02**
- [x] **Passo 15** — **Verificar que nenhuma decisão do ciclo permanece em aberto**

---

## 3. Questões

### Q1 — J-02: a base de cálculo do realizado

**A última questão em aberto do ciclo**, levantada pelas jornadas transversais e adiada desde então.

Uma compra de **R$ 1.200,00 em 12x, feita em 30/07** num cartão que fecha dia 28. As parcelas caem
nas competências de setembro/2026 a agosto/2027.

| Base | Onde os R$ 1.200,00 aparecem |
|---|---|
| Data da compra | **Tudo em julho** |
| Competência da parcela | **R$ 100,00 por mês**, de setembro a agosto do ano seguinte |

As duas respondem a perguntas diferentes e legítimas: *"quanto me comprometi neste mês"* e *"quanto
vou pagar neste mês"*.

### Q2 — O orçamento é pessoal, ou pode ser de grupo?

`Categoria` ganhou escopo em U2 (D-54). Receitas são **individuais** por P-05. O orçamento fica de
que lado?

### Q3 — O aporte conta no realizado do orçamento?

RF-76 e D-18 são explícitos: o aporte conta como **gasto no balanço**. O orçamento é por
**categoria**, e um aporte não tem categoria — ele tem objetivo.

### Q4 — Registrar um aporte mexe no saldo do objetivo?

RF-70 acumula o **total aportado**; RF-71 permite **atualizar o saldo manualmente**; RF-72 define
`rendimento = saldo − aportado`. Se o aporte não mexer no saldo, o rendimento nasce negativo e só
fica certo depois de o usuário atualizar à mão.

---

## 5. Respostas

| | Resposta | Decisão |
|---|---|---|
| **Q1** | **O usuário escolhe a base por orçamento** — cada teto declara se conta pela data da compra ou pela competência | **D-77** — fecha **J-02** |
| **Q2** | O orçamento **pode ser de grupo**, além de pessoal | **D-78** |
| **Q3** | O aporte **não** entra no realizado do orçamento; conta só no balanço | **D-79** |
| **Q4** | Registrar aporte **soma ao saldo** do objetivo | **D-80** |

### 5.1 Reanálise (Passo 6)

**Q1 tem uma consequência que precisa de tratamento explícito, e ela foi sinalizada na pergunta.**
Com bases diferentes por orçamento, **somar os realizados de todas as categorias produz um número
sem significado** — seria a soma de "quanto me comprometi" com "quanto vou pagar".

*Tratamento adotado, dentro da decisão*: a comparação **por categoria** é sempre exata e é a que
H-40 e H-41 pedem. O agregado do mês **não é um escalar**: a resposta traz os totais **separados por
base**, com o mesmo princípio de RF-97 e D-28 — duas grandezas distintas, lado a lado, que nunca se
somam. É a terceira vez no ciclo que este padrão resolve um problema, e vale registrar a
recorrência.

**Q2 cria um segundo eixo, e ele já tem precedente.** Um gasto de escopo GRUPO conta no teto
**pessoal** do dono e no teto **do grupo** — igual ao que já acontece com `totalPessoal` e
`totalGrupo` em U2. A resolução é a mesma: orçamento PESSOAL compara contra `totalPessoal`;
orçamento GRUPO compara contra `totalGrupo`. **São grandezas distintas que nunca se somam.**

Não é dupla contagem pelo mesmo motivo de RN-T04: os dois números respondem a perguntas diferentes.

**Q3 repete o raciocínio da conta derivada de fatura em U3.** O aporte não tem categoria, e forçar
uma seria inventar dado. A diferença é que aqui a saída é mais simples: o aporte não entra no
orçamento, e ponto — o balanço continua contando (D-18, RF-76).

**Q4 e Q3 se combinam bem.** O aporte soma ao saldo, então o rendimento nasce **zero** em vez de
negativo; e como ele não entra no orçamento, não há risco de o mesmo dinheiro ser contado num teto
de categoria e num objetivo.

**Nenhuma contradição entre as respostas.**

---

## 4. Riscos identificáveis antes das respostas

| Risco | Tratamento |
|---|---|
| J-02 ser resolvida por conveniência de implementação em vez de por significado | É a primeira questão, e a tabela acima mostra os dois números concretos antes de qualquer código |
| O realizado do orçamento divergir do total de U2 | O realizado usa os mesmos dados; o que muda é o **eixo temporal**. Verificar no Passo 10 |
| `Aporte` virar um `Gasto` disfarçado | Ele conta como gasto no balanço (RF-76) sem **ser** um gasto. A distinção precisa ficar no modelo |
| A última unidade herdar pressa | O ciclo termina aqui, e o que ficar mal resolvido não tem unidade seguinte para corrigir |
