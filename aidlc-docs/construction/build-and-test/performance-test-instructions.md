# Testes de Desempenho

**Não existem, e a ausência é decisão.** Este documento registra por quê, e o que precisaria ser
verdade para que passassem a existir.

---

## 1. Por que não há

**RNF-12** define o alvo: uso doméstico, **dezenas de usuários**, instância única, sem alta
disponibilidade. A NFR Design de U1 registrou que esse número deveria **podar o espaço de decisão**,
e não convidar a superdimensionar.

Um teste de carga aqui mediria uma pergunta que ninguém fez. Os números que ele produziria — vazão,
percentis, saturação — não têm um limiar contra o qual comparar, porque **não há requisito de
desempenho no projeto**. Um teste sem critério de aprovação é um relatório, não um teste.

---

## 2. O que foi feito no lugar

Cada consulta com custo real recebeu uma **decisão de design registrada**, e não uma medição:

| Consulta | Decisão | Racional |
|---|---|---|
| Totais de gastos (U2) | `SUM` no banco (D-64) | O banco pode somar |
| Listagem de gastos (U2) | **Projeção direta**, paginada com teto de 100 (D-65) | Com `open-in-view: false`, o N+1 não tem por onde acontecer |
| Total da fatura (U3) | `SUM` na leitura (D-75) | Evita oito caminhos de escrita mantendo um agregado |
| Vencimentos (U3) | Projeção das recorrentes, materialização ao ser tocada (D-72) | Não cria linhas até o infinito |
| Acompanhamento (U4) | **Uma consulta agrupada** por base (D-84) | Evita um N+1 escrito de propósito |

E os índices que sustentam cada uma estão nas migrations, com o comentário de qual predicado servem.

> **A regra que atravessa todas**: *o banco pode somar; dividir, nunca.* Existem duas divisões
> monetárias no sistema, e as duas vivem em Kotlin, com teste de propriedade.

---

## 3. O que precisaria mudar para justificar testes de carga

| Gatilho | Por quê |
|---|---|
| Mais de uma instância da aplicação | Traria o `RegistroDeTentativas` para a discussão, e com ele a necessidade de medir contenção |
| Requisito de tempo de resposta escrito | Daria o critério de aprovação que hoje falta |
| Volume acima de milhares de lançamentos por usuário | Tornaria as agregações de D-64, D-75 e D-84 mensuráveis em vez de irrelevantes |
| Uso não-doméstico | Invalidaria RNF-12, que é a premissa de tudo acima |

**Nenhum dos quatro é verdade hoje.**

---

## 4. O único limite de recurso que existe

`Paginacao.MAXIMO = 100`. Ele não é ajuste de desempenho: é **defesa**. Sem ele,
`?tamanho=1000000` seria um caminho de exaustão de memória disponível a qualquer usuário
autenticado. É a única proteção de recurso do sistema, e é barata.

---

## 5. O que **é** medido, ainda que não por teste de carga

| Item | Onde |
|---|---|
| Tempo de resposta constante na falha de autenticação | `UsuarioIntegracaoTest` — RN-U04. É **segurança**, não desempenho: latência variável vira oráculo de enumeração de contas |
| Ausência de N+1 na listagem | Garantida por construção (D-65), não por medição |
| Duração da suíte | O CI reporta: **~16 s** para 199 testes |
