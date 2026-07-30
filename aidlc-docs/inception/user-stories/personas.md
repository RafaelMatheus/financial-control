# Personas

**Stage**: INCEPTION - User Stories - Part 2
**Timestamp**: 2026-07-30T16:11:59Z
**Decisão de modelagem**: uma única persona (Question 2 do `story-generation-plan.md`)

> **Revisão 8**: com a remoção do rateio (D-27), o contexto C3 passou de *"autor do lançamento"*
> para **"dono do lançamento"** — quem registrou **e** a quem o valor pertence integralmente.

---

## Decisão: por que uma persona só

Os requisitos sugerem à primeira vista três papéis — quem cria o grupo, quem participa dele, e quem
é dono do cartão. A análise mostrou que **o sistema não os trata de forma diferente**:

- **RF-16** permite que *qualquer membro* edite ou exclua gastos de escopo GRUPO. Não há privilégio
  de quem criou.
- **RF-08** permite adicionar e remover membros sem restringir quem pode fazê-lo.
- **RF-24** permite que o cartão pertença a um usuário **ou a um grupo** — mas a propriedade define
  visibilidade, não uma classe de usuário.
- **RF-07** admite usuário sem nenhum grupo. É a mesma pessoa, num contexto em que o
  compartilhamento simplesmente não se aplica.

Modelar administrador e membro como personas distintas criaria uma hierarquia que **o sistema não
implementa**, e as histórias herdariam uma diferença falsa. O que existe são **contextos** que a
mesma persona ocupa conforme a situação.

---

## Persona — Usuário

**Quem é**: uma pessoa que quer entender e controlar para onde vai o próprio dinheiro. Registra o
que gasta, acompanha o que tem a pagar, e quando mora ou convive com outras pessoas quer enxergar
as contas da casa junto com as suas, sem misturar o que é de cada um.

**Contexto de vida**: usa o sistema para finanças pessoais e, quando compartilha um contexto de
custos — moradia, viagem, um projeto em comum —, precisa que as contas do grupo fiquem visíveis a
todos sem se confundir com as individuais.

### Motivações

- Saber quanto realmente gastou no mês, incluindo o que ainda vai vencer
- Separar com clareza o que é gasto seu do que é gasto da casa
- Não ser surpreendido por uma fatura de cartão maior que o esperado
- Saber quanto de cada parcela ainda está comprometido nos meses seguintes
- Enxergar as contas da casa junto com as do parceiro, sabendo quem pagou o quê
- Guardar dinheiro com propósito e ver o progresso

### Frustrações que o sistema endereça

- Planilha manual que desatualiza e não calcula competência de fatura
- Não saber se uma compra caiu na fatura deste mês ou do próximo
- Perder o controle de quantas parcelas ainda faltam, espalhadas entre cartões
- Não ter visão das contas da casa como um todo, só das próprias
- Descobrir uma conta vencida depois do vencimento

### O que pode fazer

| | |
|---|---|
| **Identidade** | Criar conta, autenticar, gerenciar o próprio perfil |
| **Grupos** | Criar grupos, entrar, adicionar e remover membros, sair — **sem hierarquia** entre membros |
| **Gastos** | Lançar gastos pessoais ou de grupo, com categoria e forma de pagamento |
| **Visibilidade** | Marcar lançamentos como pessoais ou do grupo; enxergar os lançamentos dos demais membros |
| **Cartões** | Cadastrar cartões próprios ou do grupo, consultar faturas, marcar e desmarcar pagamento |
| **Parcelamento** | Lançar compras parceladas, corrigir ou excluir por inteiro |
| **Contas a pagar** | Registrar boletos, PIX e faturas de serviço; ver tudo que vence no período |
| **Receitas e orçamento** | Registrar entradas, definir teto por categoria, acompanhar o realizado |
| **Investimentos** | Criar objetivos, aportar, atualizar saldo, acompanhar meta e prazo |

### O que **não** pode fazer

- Acessar dados de outro usuário fora das regras de visibilidade (RF-03, RF-04)
- Alterar uma fatura ou conta já marcada como **paga** — precisa antes desmarcar o pagamento
  (RF-94, RF-95)
- Editar uma parcela isoladamente — só a compra inteira (RF-33)
- Reivindicar como seus os lançamentos de outro dono — o valor de um lançamento pertence integralmente a quem o cadastrou (RF-17)

---

## Contextos da persona

A mesma pessoa ocupa contextos diferentes conforme a situação. **Não são papéis com permissões
distintas** — são posições que qualquer usuário assume, e que determinam o que ele vê.

### C1 — Usuário individual
Não pertence a nenhum grupo, ou está operando sobre dados pessoais. Todo lançamento tem escopo
PESSOAL e é visível só para ele. É o contexto padrão, e o sistema funciona inteiro nele — a
participação em grupo é opcional (RF-07).

### C2 — Membro de grupo
Pertence a um ou mais grupos. Enxerga os lançamentos de escopo GRUPO de cada um deles —
**inclusive os anteriores à sua entrada** (E-10) — e pode editá-los ou excluí-los como qualquer
outro membro (RF-16). **Não assume valor nenhum por isso**: cada lançamento continua pertencendo ao
seu dono.

### C3 — Dono do lançamento
Quem registrou um gasto ou conta, e **a quem o valor pertence integralmente**. O dono é sempre
preservado (RF-17), mas **não confere privilégio de edição**: em escopo GRUPO, qualquer membro pode
alterar o que ele lançou (RF-16).

O dono é o eixo que separa as duas grandezas do sistema: o **total pessoal** soma apenas os
lançamentos de que o usuário é dono; o **total do grupo** soma todos os lançamentos de escopo GRUPO,
de qualquer dono (RF-97). **Os dois nunca se somam.**

### C4 — Proprietário do cartão
Cadastrou um cartão em seu nome. Quando o cartão pertence a um **grupo** (RF-24), a fatura passa a
ser visível a todos os membros — a propriedade define quem enxerga a fatura, não quem manda nela.

---

## Mapeamento persona → histórias

Como há uma única persona, **todas as histórias são executadas por ela**. O que varia é o contexto:

| Contexto | Épicos onde é relevante |
|---|---|
| C1 — Usuário individual | Todos. É o contexto mínimo de operação |
| C2 — Membro de grupo | E2 (Grupos), E3 (Compartilhamento e Visibilidade), e o escopo GRUPO em E4, E5, E10, E11 |
| C3 — Dono do lançamento | E3 (RF-17), E4 (Gastos), E10 (Contas a pagar) |
| C4 — Proprietário do cartão | E5 (Cartões), E6 (Parcelamento), E10 (fatura como conta a pagar) |

---

## Nota para a Application Design

A ausência de hierarquia entre membros é uma **decisão de produto**, não uma omissão. Se em algum
momento surgir a necessidade de um administrador de grupo — por exemplo, para impedir que um membro
remova outro arbitrariamente —, isso será uma mudança de requisito (RF-08 e RF-16), não um detalhe
de implementação. A modelagem não deve antecipar essa hierarquia "por precaução".
