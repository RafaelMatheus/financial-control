# Plano de NFR Design — U3 Crédito

> Fonte única de verdade da NFR Design de U3.
>
> **Nasce sem seção de respostas** — criada depois de as respostas existirem. Quarta stage seguida
> com a correção estrutural a O-32.

---

## 1. Pré-requisito

Como em U2: U3 **não tem NFR Requirements própria**. Ela roda uma vez, em U1, porque fecha decisões
de stack da aplicação. Insumo: os 14 NFRs de U1 mais a Functional Design de U3, recém-aprovada.

---

## 2. O que esta stage tem para resolver

A Functional Design de U3 deixou **quatro problemas estruturais** explicitamente endereçados a esta
stage. Nenhum deles é sobre desempenho — todos são sobre **garantias que dependem de alguém
lembrar**, que é a classe de problema que este projeto vem convertendo em mecanismo.

| # | Problema | Origem |
|---|---|---|
| 1 | RN-F07 (bloqueio de fatura paga) tem **três chamadores**, e o risco é um deles esquecer | `business-logic-model.md` §5 |
| 2 | O job de fechamento (D-71) é o **primeiro componente agendado**, e duas instâncias fechariam a mesma fatura duas vezes | §4.2 |
| 3 | `Fatura.valorTotal` é **derivado mas persistido** — um agregado que pode divergir da soma | RN-F03 |
| 4 | A visão de vencimentos **projeta** ocorrências a cada consulta | RN-R02, D-72 |

---

## 3. Passos

### Análise

- [x] **Passo 1** — Ler os 14 NFRs de U1, a NFR Design de U1 e a de U2
- [x] **Passo 2** — Ler a Functional Design de U3 e extrair o que ela endereçou a esta stage (§2)
- [x] **Passo 3** — Avaliar as **5 categorias obrigatórias**, com justificativa escrita inclusive
      para as não-aplicáveis

### Esclarecimento

- [x] **Passo 4** — Formular as questões (§4)
- [x] **Passo 5** — Coletar as respostas e criar a §5
- [x] **Passo 6** — Reanalisar

### Design

- [x] **Passo 7** — `nfr-design-patterns.md`
- [x] **Passo 8** — `logical-components.md`, com a tabela de ausências **atualizada** — o agendador
      sai dela e entra na de presenças, com o inventário do que se perde
- [x] **Passo 9** — Diagramas Mermaid validados
- [x] **Passo 10** — Registrar as decisões (numeração continua de D-72) e **avaliar se a Code
      Generation de U3 cabe numa entrega só**

---

## 4. Questões

### Q1 — Como impedir que um dos três chamadores esqueça RN-F07?

`gasto`, `compra` e `conta` precisam verificar fatura paga antes de alterar. Escrito como chamada
explícita, funciona enquanto alguém lembrar — e U3 tem seis entidades novas, então "alguém lembrar"
já falhou como estratégia uma vez neste projeto (foi o que D-52 resolveu para visibilidade).

### Q2 — Como o job de fechamento roda, e o que impede execução dupla?

D-71 escolheu job diário. Falta decidir **onde ele vive** e o que acontece se houver duas
instâncias — hoje há uma, mas o `RegistroDeTentativas` de U1 já é a primeira coisa que quebra com a
segunda, e este seria o segundo item da mesma lista.

### Q3 — `Fatura.valorTotal`: persistido ou calculado na leitura?

RN-F03 diz que é sempre a soma dos lançamentos da competência. Persistir é guardar um agregado que
precisa ser recalculado a cada lançamento, edição e exclusão — e que pode divergir se um caminho
esquecer de recalcular. Calcular na leitura nunca diverge, ao custo de somar a cada consulta.

É o mesmo eixo de D-64 em U2, com um agravante: lá as duas aritméticas eram equivalentes por
construção; aqui, uma delas é **estado guardado**.

### Q4 — A Code Generation de U3 sai numa entrega só?

U1 teve 28 passos e ~45 arquivos. U2 teve 24 passos e 17 arquivos. U3 tem **6 entidades, 37 regras e
5 serviços** — pelo padrão, algo como 40 passos e 30 arquivos, mais a alteração de `dividirEm` em
U1 e a reescrita da propriedade correspondente.

---

## 5. Respostas

*Criada depois de as respostas existirem.*

| | Resposta | Decisão |
|---|---|---|
| **Q1** | **Guarda no adaptador de persistência.** Nenhuma entidade com `cartao`+`competencia` é gravada sem a checagem | **D-73** |
| **Q2** | **`@Scheduled` + advisory lock do PostgreSQL** | **D-74** |
| **Q3** | **`valorTotal` calculado na leitura**, por `SUM` | **D-75** |
| **Q4** | **Uma entrega só** — contra a recomendação | **D-76** |

### 5.1 Reanálise (Passo 6)

**Q3 altera um artefato já aprovado, e a correção precisa ser feita, não interpretada.**
`domain-entities.md` §2 lista `valorTotal` como atributo persistido de `Fatura`, com a invariante
*"sempre igual à soma dos lançamentos"*. Com D-75 ele **deixa de ser atributo**: passa a ser
computado.

A correção foi aplicada ao artefato da Functional Design, com nota apontando para cá. É a segunda
vez neste ciclo que uma stage posterior corrige uma anterior — a primeira foi D-57 divergindo do
`openapi.yaml`. Registrar a correção no lugar de reinterpretar o texto é o que mantém os dois
documentos utilizáveis.

**O que continua persistido, e é importante não confundir**: o **valor da conta a pagar** gerada no
fechamento. Aquele é fato histórico — o que foi cobrado —, e não pode mudar se um lançamento antigo
for corrigido depois. A fatura reflete a soma atual; a conta reflete o que foi fechado.

> Esta distinção é o que torna D-75 barato. Se a conta a pagar também derivasse, corrigir um gasto
> de março mudaria o valor de uma conta paga em abril.

**Q2 tira o fechamento da lista do que quebra com escala horizontal.** Depois de D-74, o
`RegistroDeTentativas` de U1 volta a ser o **único** item da lista. É a primeira vez no projeto que
essa lista **encolhe**.

**Q1 e Q3 se combinam melhor do que pareciam.** Com o total calculado na leitura, o adaptador não
precisa recalcular nada ao gravar — só verificar. A guarda de D-73 fica sendo a única coisa que o
adaptador faz além de gravar.

**Q4 foi respondida contra a recomendação, e a ressalva fica registrada.** Uma entrega só significa
um plano de ~40 passos e ~30 arquivos, com um gate no fim. A consequência concreta: se o CI reprovar,
a causa pode estar em qualquer ponto de seis entidades — em U2, com 24 passos, as duas falhas foram
localizadas em minutos porque a superfície era menor.

**Mitigação adotada dentro da decisão**: o plano será organizado em **blocos com verificação
intermediária** — compilar e rodar a suíte local ao fim de cada bloco, mesmo sem gate de usuário.
Não substitui o CI, mas evita chegar ao fim com seis entidades e nenhuma evidência.

---

## 6. Categorias obrigatórias

| Categoria | Aplicável | Justificativa |
|---|---|---|
| **Resilience** | **Sim — pela primeira vez** | O job de D-71 introduz um modo de falha que nenhuma unidade anterior tinha: falha silenciosa por não-execução. É a primeira vez que "não aconteceu nada" é um defeito |
| **Scalability** | **Parcial** | RNF-12 continua valendo. U3 **ia** acrescentar o segundo componente que quebra com escala horizontal; D-74 impediu, e a lista voltou a ter um item só (`RegistroDeTentativas`). *Avaliação atualizada após a resposta a Q2* |
| **Performance** | **Sim** | Q3 e Q4. Recálculo de fatura e projeção de ocorrências são os dois caminhos com custo real |
| **Security** | **Parcial** | Nenhum mecanismo novo. As 6 entidades entram no padrão de visibilidade já existente, e o `ArquiteturaTest` de U2 as cobre automaticamente — primeiro retorno concreto de D-66 |
| **Logical Components** | **Sim** | O agendador muda de lado na tabela |

---

## 7. Riscos identificáveis antes das respostas

| Risco | Tratamento |
|---|---|
| A garantia de RN-F07 ficar como convenção | Objeto de Q1. O critério é o mesmo de D-52: esquecer deve ser erro de compilação ou reprovação de build, não bug |
| O job rodar duas vezes e gerar duas contas a pagar | A idempotência já está no design funcional (a fatura fechada tem `dataFechamento`). Q2 decide se há proteção adicional |
| `valorTotal` divergir da soma | Objeto de Q3 |
| Um plano de código grande demais para ser executado com atenção | Objeto de Q4 |
| A alteração de `dividirEm` (D-68) quebrar testes de U1 | **Esperado e desejado**: a propriedade *"partes diferem no máximo 0,01"* passa a ser falsa. O que não pode é a de soma exata quebrar |
