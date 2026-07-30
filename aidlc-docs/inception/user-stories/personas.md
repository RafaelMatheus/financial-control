# Personas

**Stage**: INCEPTION - User Stories - Part 2
**Timestamp**: 2026-07-30T16:11:59Z
**Decisão de modelagem**: uma única persona (Question 2 do `story-generation-plan.md`)

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
que gasta, acompanha o que tem a pagar, e quando divide despesas com outras pessoas quer saber
exatamente quanto cabe a cada um.

**Contexto de vida**: usa o sistema para finanças pessoais e, quando divide custos — moradia,
viagem, um projeto em comum — precisa que isso conviva com as finanças individuais sem se
misturar.

### Motivações

- Saber quanto realmente gastou no mês, incluindo o que ainda vai vencer
- Não ser surpreendido por uma fatura de cartão maior que o esperado
- Saber quanto de cada parcela ainda está comprometido nos meses seguintes
- Dividir despesas comuns sem planilha paralela e sem discussão sobre quem pagou o quê
- Guardar dinheiro com propósito e ver o progresso

### Frustrações que o sistema endereça

- Planilha manual que desatualiza e não calcula competência de fatura
- Não saber se uma compra caiu na fatura deste mês ou do próximo
- Perder o controle de quantas parcelas ainda faltam, espalhadas entre cartões
- Divisão de despesas de casa feita "de cabeça", sem registro
- Descobrir uma conta vencida depois do vencimento

### O que pode fazer

| | |
|---|---|
| **Identidade** | Criar conta, autenticar, gerenciar o próprio perfil |
| **Grupos** | Criar grupos, entrar, adicionar e remover membros, sair — **sem hierarquia** entre membros |
| **Gastos** | Lançar gastos pessoais ou de grupo, com categoria e forma de pagamento |
| **Rateio** | Definir como um gasto de grupo se divide: igual (padrão), por percentual ou por valor |
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
- Ter cota em gastos de grupo lançados antes de sua entrada, embora os enxergue (RF-09, E-10)

---

## Contextos da persona

A mesma pessoa ocupa contextos diferentes conforme a situação. **Não são papéis com permissões
distintas** — são posições que qualquer usuário assume, e que determinam o que ele vê.

### C1 — Usuário individual
Não pertence a nenhum grupo, ou está operando sobre dados pessoais. Todo lançamento tem escopo
PESSOAL e é visível só para ele. É o contexto padrão, e o sistema funciona inteiro nele — a
participação em grupo é opcional (RF-07).

### C2 — Membro de grupo
Pertence a um ou mais grupos. Enxerga os gastos de escopo GRUPO de cada um deles — **inclusive os
anteriores à sua entrada** (E-10) — e pode editá-los ou excluí-los como qualquer outro membro
(RF-16). Tem cota apenas nos gastos posteriores à sua entrada.

### C3 — Autor do lançamento
Quem registrou um gasto específico. A autoria é sempre preservada (RF-17), mas **não confere
privilégio**: em escopo GRUPO, qualquer membro pode alterar o que ele lançou. A autoria serve para
rastreabilidade e para responder "quem lançou isso?", não para controle de acesso.

### C4 — Proprietário do cartão
Cadastrou um cartão em seu nome. Quando o cartão pertence a um **grupo** (RF-24), a fatura passa a
ser visível a todos os membros — a propriedade define quem enxerga a fatura, não quem manda nela.

---

## Mapeamento persona → histórias

Como há uma única persona, **todas as histórias são executadas por ela**. O que varia é o contexto:

| Contexto | Épicos onde é relevante |
|---|---|
| C1 — Usuário individual | Todos. É o contexto mínimo de operação |
| C2 — Membro de grupo | E2 (Grupos), E3 (Compartilhamento e Rateio), e o escopo GRUPO em E4, E5, E10, E11 |
| C3 — Autor do lançamento | E3 (RF-17, autoria), E4 (Gastos) |
| C4 — Proprietário do cartão | E5 (Cartões), E6 (Parcelamento), E10 (fatura como conta a pagar) |

---

## Nota para a Application Design

A ausência de hierarquia entre membros é uma **decisão de produto**, não uma omissão. Se em algum
momento surgir a necessidade de um administrador de grupo — por exemplo, para impedir que um membro
remova outro arbitrariamente —, isso será uma mudança de requisito (RF-08 e RF-16), não um detalhe
de implementação. A modelagem não deve antecipar essa hierarquia "por precaução".
