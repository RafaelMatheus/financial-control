# Regras de Negócio — U4 Planejamento

**Prefixos**: `RN-RC*` receita · `RN-O*` orçamento · `RN-I*` investimento · `RN-B*` balanço.

**Herdadas e valendo sem reescrita**: RN-U05, RN-V01 a V04, RN-L02 (categoria visível),
RN-T01 (toda consulta filtrada), RN-T04 (grandezas distintas nunca se somam).

---

## 1. `receita`

### RN-RC01 — Descrição, valor e data são obrigatórios; valor é positivo
**Origem**: RF-39, H-36 · **Erro**: `VALOR_INVALIDO` (400), `NOME_OBRIGATORIO` (400)

### RN-RC02 — Receita é **individual**, sem escopo
Não existe receita de grupo. É a única entidade com dono do sistema sem `Escopo`.

**Origem**: P-05, H-36

> A consequência não é de modelagem, é de produto: **não há renda familiar**. O balanço é sempre
> pessoal. Um requisito de renda compartilhada seria requisito novo.

### RN-RC03 — Só o dono vê, edita e exclui a própria receita
O predicado de visibilidade reduz-se à primeira metade: `dono == usuarioAtual`.

**Origem**: RN-V01, P-05 · **Erro**: `NAO_ENCONTRADO` (404)

### RN-RC04 — Consulta por período, com total
**Origem**: RF-40, H-37

---

## 2. `orcamento`

### RN-O01 — Um teto por categoria, competência e escopo
`(dono, categoria, competencia, escopo, grupo)` é único.

**Origem**: RF-42, H-39 · **Erro**: `ORCAMENTO_DUPLICADO` (409)

> A chave inclui **escopo e grupo**, e sem isso D-78 seria inexpressável: não daria para ter um teto
> pessoal de "Mercado" em agosto e um teto da casa para a mesma categoria no mesmo mês.

### RN-O02 — Teto não negativo; **zero é válido**
Zero significa *"não quero gastar nada nesta categoria este mês"* — é um teto, não a ausência de um.
Remover o orçamento é operação distinta.

**Origem**: RF-42, H-39 · **Erro**: `VALOR_INVALIDO` (400)

### RN-O03 — A categoria precisa ser visível
**Origem**: RF-42, RN-L02 · **Erro**: `CATEGORIA_NAO_ENCONTRADA` (404)

### RN-O04 — Cada orçamento declara a sua base de cálculo (D-77, **J-02 fechada**)
```
DATA_DA_COMPRA  ->  realizado conta pela data do lancamento
COMPETENCIA     ->  realizado conta pela competencia da fatura
```
Para gasto à vista as duas coincidem. Elas só divergem quando há cartão.

**Origem**: RF-43, J-02, D-77

### RN-O05 — O realizado depende do escopo do orçamento (D-78)
| Escopo do orçamento | Realizado |
|---|---|
| PESSOAL | lançamentos de que o consultante é **dono**, de qualquer escopo |
| GRUPO | lançamentos de **escopo GRUPO** daquele grupo, de qualquer dono |

**Origem**: RF-43, RF-97, D-28, D-78

> Um gasto de escopo GRUPO cujo dono é o consultante conta nos dois. **Não é dupla contagem** —
> é a mesma quantia respondendo a duas perguntas, e os números nunca se somam (RN-T04).

### RN-O06 — Categorias com realizado acima do teto são sinalizadas, com o excedente
**Origem**: RF-44, H-41

### RN-O07 — Aporte **não** entra no realizado (D-79)
O orçamento é por categoria; o aporte não tem categoria.

**Origem**: D-79 · **Verificação**: teste com aporte no período, verificando que o realizado não muda

### RN-O08 — Os totais do mês **nunca somam bases diferentes**
A resposta apresenta o total realizado **separado por base**. Não existe um escalar que some
`DATA_DA_COMPRA` com `COMPETENCIA`.

**Origem**: D-77 · **Verificação**: teste que percorre o DTO procurando um campo que seja a soma

> **Terceira aplicação do mesmo padrão no ciclo.** RF-97 separou total pessoal de total de grupo;
> D-28 proibiu somá-los; agora D-77 cria um terceiro par de grandezas incomensuráveis. Em todos, a
> resposta foi a mesma: **apresentar lado a lado e nunca somar**.

---

## 3. `investimento`

### RN-I01 — Nome obrigatório; meta e prazo **opcionais**
Objetivo aberto como "Geral" pode não ter alvo.

**Origem**: RF-68, RF-73, RF-74, H-52 · **Erro**: `NOME_OBRIGATORIO` (400)

### RN-I02 — Aporte tem valor positivo e registra o seu dono
Resgate **não** é aporte negativo — ajusta-se o `saldoAtual`.

**Origem**: RF-69, RF-75 · **Erro**: `VALOR_INVALIDO` (400)

### RN-I03 — `totalAportado` é a soma dos aportes
Derivado, nunca digitado.

**Origem**: RF-70

### RN-I04 — Registrar aporte **soma ao saldo atual** (D-80)
Aportar R$ 500 sobe `totalAportado` **e** `saldoAtual` em R$ 500.

**Origem**: RF-70, RF-71, D-80

> Sem isto o rendimento nasceria em **−R$ 500** logo após o primeiro aporte, até o usuário corrigir
> à mão. O número nasce certo em vez de nascer errado e esperar correção.

### RN-I05 — O saldo é atualizável à mão, e é assim que o rendimento aparece
O sistema não tem cotação nem integração com corretora, e não pretende ter.

**Origem**: RF-71, H-54

### RN-I06 — `rendimento = saldoAtual − totalAportado`, e **pode ser negativo**
Prejuízo e resgate não registrado são casos reais. **Exibir, não rejeitar.**

**Origem**: RF-72, E-14, P-08, H-55

### RN-I07 — Progresso e quanto falta, quando há meta
`progresso = saldoAtual / meta`; `falta = max(0, meta − saldoAtual)`.
Sem meta, os dois são ausentes — **não zero**.

**Origem**: RF-73, H-56

### RN-I08 — Aporte mensal necessário, quando há meta **e** prazo
```
meses = meses cheios entre hoje e o prazoAlvo
aporteMensal = (meta − saldoAtual) / meses,  se meses > 0 e meta > saldoAtual
```
Com prazo vencido e meta não atingida, o objetivo é sinalizado como **atrasado** — e novos aportes
continuam permitidos (E-15).

**Origem**: RF-74, E-15, H-57 · **Verificação**: 🔬 property-based

> **A divisão aqui é monetária**, e portanto vive em `Dinheiro`. É o **segundo** consumidor de
> divisão do sistema, depois do parcelamento — e vale a mesma regra: *o banco pode somar; dividir,
> nunca.*

### RN-I09 — Objetivo de grupo: todos aportam, cada aporte tem dono
O saldo é a soma de todos os aportes, **sem rateio** (D-27).

**Origem**: RF-75, H-58

### RN-I10 — Membro que sai do grupo preserva o histórico de aportes
Mesmo tratamento de E-05 e D-62: o que ele aportou permanece.

**Origem**: E-16

### RN-I11 — Posição consolidada de todos os objetivos
Total aportado, saldo atual e rendimento agregado.

**Origem**: RF-77, H-60

---

## 4. `balanco`

### RN-B01 — Balanço é receitas menos gastos do período
**Origem**: RF-41, H-38

### RN-B02 — O aporte conta **como gasto** no balanço
**Origem**: RF-76, D-18, H-38, H-59

> **Semântica declarada**: investir **reduz** o resultado do mês, embora o patrimônio não diminua.
> O balanço mede **fluxo de caixa**, não variação patrimonial. É escolha consciente do usuário
> (D-18), e define o significado do indicador.

### RN-B03 — O balanço é sempre pessoal
Consequência direta de RN-RC02: sem receita de grupo, não há balanço de grupo.

**Origem**: P-05

---

## 5. Alvos de property-based testing

| # | Propriedade | Regra |
|---|---|---|
| 1 | Para qualquer conjunto de aportes, `totalAportado` é a soma exata | RN-I03 |
| 2 | Após qualquer sequência de aportes, `saldoAtual >= totalAportado` **se** nenhum ajuste manual reduziu o saldo | RN-I04 |
| 3 | `rendimento` é sempre `saldoAtual − totalAportado`, para qualquer par de valores, inclusive negativo | RN-I06 |
| 4 | `aporteMensal × meses >= meta − saldoAtual`, para qualquer meta, saldo e prazo — **nunca falta dinheiro por arredondamento** | RN-I08 |
| 5 | O realizado com base `DATA_DA_COMPRA` e com base `COMPETENCIA` **coincidem** quando não há cartão | RN-O04 |

> **A 4 é a mais interessante.** Ela é a versão de U4 da invariante de parcelamento: uma divisão
> monetária que precisa fechar. A diferença é a direção — no parcelamento a soma tem que ser
> **exata**; aqui ela tem que ser **suficiente**, porque arredondar para menos deixaria o usuário
> chegar ao prazo faltando centavos.

> **A 5 é uma prova de consistência entre unidades.** Se ela falhar, o realizado de U4 divergiu dos
> totais de U2 — e o defeito estaria numa das duas.

---

## 6. Rastreabilidade

| História | Regras |
|---|---|
| H-36 gerenciar receitas | RN-RC01, RN-RC02, RN-RC03 |
| H-37 consultar receitas | RN-RC04 |
| H-38 balanço | RN-B01, RN-B02, RN-B03 |
| H-39 definir orçamento | RN-O01, RN-O02, RN-O03 |
| H-40 orçado × realizado | **RN-O04, RN-O05**, RN-O08 |
| H-41 sinalizar estouro | RN-O06 |
| H-52 criar objetivo | RN-I01 |
| H-53 registrar aporte | RN-I02, RN-I03, RN-I04 |
| H-54 atualizar saldo | RN-I05 |
| H-55 rendimento negativo | RN-I06 |
| H-56 progresso e meta | RN-I07 |
| H-57 aporte mensal necessário | **RN-I08** |
| H-58 objetivo de grupo | RN-I09, RN-I10 |
| H-59 aporte no balanço | RN-B02, RN-O07 |
| H-60 posição consolidada | RN-I11 |
| **J-02 fechar o mês** | **RN-O04**, mais o que U2 e U3 já entregaram |

**26 regras**, 15 histórias e a jornada cobertas.
