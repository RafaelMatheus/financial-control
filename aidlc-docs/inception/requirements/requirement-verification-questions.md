# Requirements Verification Questions

**Stage**: INCEPTION - Requirements Analysis
**Timestamp**: 2026-07-30T16:11:59Z
**Status**: ✅ Respondido — todas as ambiguidades resolvidas

> **Nota sobre o mecanismo de resposta**: por instrução explícita do usuário
> ("use o ask tool sempre que precisar fazer uma pergunta ao usuário em 100% dos casos"), as
> respostas foram coletadas via tool interativo e transcritas neste arquivo, que permanece como
> artefato rastreável do ciclo AI-DLC. Isso sobrepõe o mecanismo de arquivo-com-`[Answer]:`
> descrito em `common/question-format-guide.md`.
>
> As perguntas dos Blocos 2 e 3 foram **derivadas dinamicamente** das respostas do Bloco 1: a
> escolha por multi-usuário e o requisito de compartilhamento de gastos numa casa introduziram um
> conceito que não existia no pedido original e exigiu uma rodada de perguntas própria.

---

## Bloco 1 — Escopo e Uso

## Question 1
Quem vai usar o sistema?

A) Apenas eu — uso pessoal, sem login (single-user, sem autenticação)

B) Apenas eu, mas com login (single-user autenticado)

C) Múltiplos usuários, cada um com seus próprios dados (isolamento por usuário)

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Múltiplos usuários

## Question 2
Além de gastos e parcelas de cartão de crédito, o que entra no MVP? (múltipla escolha)

A) Categorias de gastos

B) Cadastro de cartões de crédito (múltiplos cartões)

C) Receitas / entradas de dinheiro

D) Orçamento mensal por categoria

X) Other (please describe after [Answer]: tag below)

[Answer]: A + B + C + D + X — Todas as quatro opções, **mais** o requisito adicional descrito pelo
usuário: *"Preciso também que o sistema permita o compartilhamento de gastos entre uma casa com
mais de uma pessoa caso seja necessário 1 ou mais usuários ver um gasto geral"*

## Question 3
Qual a interface do sistema no MVP?

A) API REST + documentação OpenAPI/Swagger navegável

B) Somente API REST (JSON)

C) API REST + interface web

X) Other (please describe after [Answer]: tag below)

[Answer]: X — *"API Rest + interface web que será implementado em outro repositório"*.
Ou seja: **este repositório entrega apenas a API REST**; o front-end web é um consumidor externo,
fora do escopo deste ciclo.

## Question 4
Como você quer lançar uma compra parcelada no cartão?

A) Informo o valor total + número de parcelas; o sistema gera as N parcelas automaticamente

B) Informo o valor da parcela + número de parcelas; o sistema calcula o total

C) Lanço cada parcela manualmente, uma a uma

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Valor da parcela + número de parcelas; o sistema calcula o total

---

## Bloco 2 — Compartilhamento e Cartão

## Question 5
Como deve funcionar o compartilhamento de gastos entre pessoas da mesma casa?

A) Grupo "Casa" fixo — usuários entram em uma casa; cada gasto é marcado como pessoal ou da casa

B) Compartilhamento pontual — sem grupo fixo; a cada gasto escolho com quais usuários compartilhar

C) Ambos — existe a casa como grupo padrão, mas também posso compartilhar um gasto avulso com pessoas fora dela

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Ambos

## Question 6
Quando um gasto é compartilhado, o valor precisa ser dividido entre as pessoas?

A) Divisão configurável por gasto — default igual, ajustável por cotas ou percentuais

B) Sempre divisão igual entre os membros

C) Sem divisão — apenas visibilidade; o valor pertence a quem lançou

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Divisão configurável por gasto (default: divisão igual)

## Question 7
Quem pode editar ou excluir um gasto compartilhado?

A) Só quem lançou

B) Quem lançou + administrador da casa

C) Qualquer membro da casa

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Qualquer membro da casa

## Question 8
Como modelar o cartão de crédito?

A) Nome + dia de fechamento + dia de vencimento

B) Nome + fechamento + vencimento + limite

C) Só nome/apelido

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Nome + dia de fechamento + dia de vencimento (sem limite no MVP)

---

## Bloco 3 — Regras de Negócio

## Question 9
O sistema precisa consolidar faturas mensais do cartão?

A) Sim, com marcação de paga — total por mês, parcelas que caem nela, e status de pagamento

B) Sim, apenas consolidação — sem controle de pagamento

C) Não no MVP

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Sim, com marcação de paga

## Question 10
Quando o valor não divide igualmente pelas parcelas, o que fazer com os centavos residuais?

A) Última parcela absorve (convenção do mercado brasileiro)

B) Primeira parcela absorve

C) Sem preferência — escolha a convenção e documente a regra

X) Other (please describe after [Answer]: tag below)

[Answer]: C — Sem preferência.
**Decisão adotada pelo AI-DLC**: convenção brasileira — as primeiras N-1 parcelas ficam com o valor
informado e a **última parcela absorve** a diferença residual. Ver RF-14.

## Question 11
O que acontece ao editar ou excluir uma compra parcelada já lançada?

A) Compra inteira + parcela individual editável

B) Só a compra inteira — sempre afeta todas as parcelas em conjunto

C) Somente exclusão — compras são imutáveis

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Só a compra inteira

## Question 12
Um cartão de crédito pertence a uma pessoa ou pode ser da casa?

A) Pessoa, com compras compartilháveis

B) Pode ser da pessoa ou da casa — fatura de cartão da casa é visível a todos os membros

C) Sempre pessoal

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Pode ser da pessoa ou da casa

---

## Bloco 4 — Técnico e Extensões

## Question 13
Estratégia de criação e versionamento do schema do banco?

> **Contexto**: a engenharia reversa identificou que `spring.jpa.hibernate.ddl-auto` está em
> `validate` sem nenhuma ferramenta de migration no classpath. Decisão **bloqueante** para a
> geração de código.

A) Flyway — migrations SQL versionadas

B) Liquibase — changelogs em XML/YAML/SQL

C) Manter `ddl-auto` gerando o schema

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Flyway

## Question 14 — Extensão: Security Baseline
As regras da extensão de segurança devem ser aplicadas como restrições bloqueantes?

A) Sim — aplicar todas as regras de SECURITY como restrições bloqueantes

B) Não — pular todas as regras de SECURITY

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Não

## Question 15 — Extensão: Resiliency Baseline
A baseline de resiliência (AWS Well-Architected, Pilar de Confiabilidade) deve ser aplicada?

A) Sim — aplicar as práticas direcionais de resiliência desde o design

B) Não — pular a baseline de resiliência

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Não.
*Nota de processo*: o usuário primeiro respondeu com a pergunta *"o que é aws well-architected?"*.
Foi fornecida uma explicação do framework e do Pilar de Confiabilidade, e a pergunta foi
reapresentada. A resposta informada após o esclarecimento foi **Não**.

## Question 16 — Extensão: Property-Based Testing
As regras de teste baseado em propriedades (PBT) devem ser aplicadas?

A) Sim, completo — todas as regras de PBT como restrições bloqueantes

B) Parcial — apenas funções puras e round-trips de serialização

C) Não — pular todas as regras de PBT

X) Other (please describe after [Answer]: tag below)

[Answer]: B — Parcial

## Question 17 — Clarificação: Autenticação como requisito funcional
Com a extensão Security desligada, autenticação e isolamento de dados entre usuários/casas
continuam sendo requisito funcional do sistema?

> **Motivo da pergunta**: detectada uma tensão entre a Question 1 (múltiplos usuários com dados
> isolados) e a Question 14 (extensão Security desligada). Um sistema multi-usuário com dados
> financeiros compartilhados em casas exige, por definição funcional, autenticação e controle de
> acesso — independentemente do checklist de hardening da extensão.

A) Sim — extensão Security desligada, mas login, isolamento por usuário e permissões de casa entram como requisitos funcionais normais

B) Não — API aberta no MVP; usuário é apenas um campo nos dados

C) Reativar a extensão Security

X) Other (please describe after [Answer]: tag below)

[Answer]: A — Sim, autenticação é requisito funcional

---

## Análise de Contradições e Ambiguidades

Conforme `common/question-format-guide.md`, as respostas foram analisadas em busca de
contradições e ambiguidades.

| # | Item detectado | Resolução |
|---|---|---|
| 1 | **Contradição**: Q1 (multi-usuário com dados isolados) vs. Q14 (extensão Security desligada) | Resolvida pela Question 17. A extensão fica desligada (sem checklist bloqueante de hardening), mas autenticação, isolamento por usuário e permissões de casa são requisitos **funcionais** (RF-01 a RF-05, RF-27). |
| 2 | **Ambiguidade**: Q10 respondida como "sem preferência" | Resolvida por decisão documentada do AI-DLC: última parcela absorve o resíduo (RF-14). |
| 3 | **Ambiguidade**: Q3 respondida em texto livre ("interface web em outro repositório") | Interpretada como: escopo deste repositório = **API REST apenas**; front-end web é consumidor externo fora do ciclo. Registrado como RNF-08 e como exclusão explícita de escopo. |
| 4 | **Tensão de escopo**: Q7 (qualquer membro da casa edita gastos compartilhados) vs. Q12 (cartão pode ser da casa) | Não é contradição, mas exige regra explícita sobre quem administra o cartão da casa e quem marca a fatura como paga. Endereçado por RF-24 e RF-27, e sinalizado como ponto de atenção para a Application Design. |

**Status**: nenhuma contradição em aberto. Requisitos prontos para consolidação.
