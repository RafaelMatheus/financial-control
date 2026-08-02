# Regras de Negócio — U3 Crédito

Cada regra tem identificador, enunciado verificável e requisito de origem. As que produzem erro
declaram o código que o `ErroHandler` devolve.

**Prefixos**: `RN-K*` cartão · `RN-F*` fatura · `RN-P*` compra e parcela · `RN-A*` conta a pagar ·
`RN-R*` recorrência.

**Herdadas e valendo sem reescrita**: RN-U05 (autenticação), RN-V01 a V04 (visibilidade),
RN-L02/L03 (categoria visível, escopos não precisam casar), RN-T01 (toda consulta filtrada).

---

## 1. `cartao`

### RN-K01 — Apelido obrigatório; dias entre 1 e 31
`diaFechamento` e `diaVencimento` aceitam **qualquer** valor de 1 a 31, inclusive os que não
existem em todo mês.

**Origem**: RF-23, H-18 · **Erro**: `NOME_OBRIGATORIO`, `DIA_INVALIDO` (400)

### RN-K02 — Cartão é pessoal ou de grupo, com as regras de sempre
Bicondicional escopo↔grupo, mais associação ativa. A fatura de um cartão de grupo é visível a todos
os membros.

**Origem**: RF-24, H-19, E-08 · **Erro**: `GRUPO_INVALIDO` (400), `GRUPO_NAO_ENCONTRADO` (404)

### RN-K03 — Dia que não existe no mês cai para o último dia (D-69)
`diaEfetivo(dia, ano, mês) = min(dia, últimoDiaDoMês)`. Fechamento 31 vira 28 em fevereiro comum,
29 em bissexto, 30 em abril.

**Origem**: E-04, E-11, **D-04 fechada** · **Verificação**: 🔬 property-based

> A mesma função serve a três lugares: fechamento de cartão, vencimento de cartão e vencimento de
> conta recorrente. É uma regra só, e escrevê-la três vezes seria criar três oportunidades de
> divergirem.

### RN-K04 — Encerrar cartão não apaga histórico
Cartão encerrado não recebe compras novas; faturas e parcelas existentes permanecem.

**Origem**: RF-23 · **Erro**: `CARTAO_ENCERRADO` (409)

---

## 2. `fatura`

### RN-F01 — A competência sai do dia de fechamento, com corte exclusivo
```
fechamentoDoMês = diaEfetivo(cartao.diaFechamento, mês(dataCompra))
se  dia(dataCompra) <  fechamentoDoMês  →  competência = mês(dataCompra) + 1
se  dia(dataCompra) >= fechamentoDoMês  →  competência = mês(dataCompra) + 2
```

**Origem**: RF-25, RF-61, H-20, E-03 · **Verificação**: 🔬 property-based

Conferido contra os três cenários de H-20, cartão que fecha dia 28:

| Compra | Comparação | Competência |
|---|---|---|
| 27/07 | 27 < 28 | **agosto** |
| 28/07 | 28 ≥ 28 | **setembro** |
| 30/07 | 30 ≥ 28 | **setembro** |

> **O corte é exclusivo**: o dia do fechamento já pertence ao ciclo seguinte, porque o fechamento
> ocorre no início do dia. É a leitura que E-03 fixou e que fecha D-04 na sua parte principal.

> **Nunca o dia de vencimento.** RF-61 é explícito, e o engano é natural — o vencimento é a data que
> o usuário vê no aplicativo do banco. Uma regra que usasse o vencimento daria o resultado certo em
> muitos cartões e errado em todos os que vencem no mês seguinte ao fechamento.

### RN-F02 — Uma fatura por cartão e competência
`(cartao, competencia)` é único. A fatura é criada sob demanda, na primeira vez que um lançamento
cai nela.

**Origem**: RF-26, D-31 · **Erro**: garantido por restrição de unicidade

### RN-F03 — `valorTotal` é derivado, nunca digitado
É sempre a soma dos `Gasto` e `Parcela` daquele cartão com aquela competência. Nenhuma operação o
atribui diretamente.

**Origem**: RF-26, RF-60, H-22

### RN-F04 — Fatura aberta acumula
Enquanto `dataFechamento == null`, cada lançamento novo recalcula `valorTotal`.

**Origem**: RF-60, H-22

### RN-F05 — O fechamento é diário e automático (D-71)
Um job roda todo dia e fecha as faturas cujo `diaEfetivo(cartao.diaFechamento)` é hoje: grava
`dataFechamento` e **gera a conta a pagar** (RN-A05).

**Origem**: RF-26, RF-59, **D-20 fechada**

> **Modo de falha novo, e silencioso**: se o job não rodar, a fatura não fecha e a conta a pagar não
> nasce. Ninguém recebe erro — o vencimento simplesmente não aparece. O tratamento está em
> `business-logic-model.md` §4.2, e é a razão de o fechamento ser **idempotente e recuperável**:
> o job fecha tudo que já deveria estar fechado, não apenas o de hoje.

### RN-F06 — Status derivado (D-70)
```
ABERTA   se dataFechamento == null
FECHADA  se dataFechamento != null e contaAPagar.status == EM_ABERTO
PAGA     se dataFechamento != null e contaAPagar.status == PAGA
```
`PAGA` **não é campo da fatura**.

**Origem**: RF-27, RF-57, **D-33 fechada**

### RN-F07 — Fatura PAGA bloqueia qualquer alteração de valor
Vale para: lançar gasto ou compra cuja competência caia nela, editar e excluir lançamento existente.
A mensagem orienta a desmarcar o pagamento antes.

**Origem**: RF-95, H-24, E-13 · **Erro**: `FATURA_PAGA` (409)

> **É a regra transversal da unidade.** Não vive em `fatura`: é invocada por `gasto`, por `compra` e
> por `conta` antes de qualquer alteração. Três chamadores, uma regra — e o risco é justamente um
> deles esquecer. Ver `business-logic-model.md` §5.

### RN-F08 — Lançamento retroativo em fatura FECHADA não paga reabre e recalcula
A compra fica na competência correta **pela data** (RN-F01); a fatura volta a `dataFechamento = null`
e é recalculada. A conta a pagar gerada é **descartada** e renasce no próximo fechamento.

**Origem**: RF-96, H-25, E-12

### RN-F09 — Desmarcar o pagamento libera as alterações
Desmarcar é operação sobre a **conta**, e o efeito na fatura é consequência de RN-F06.

**Origem**: RF-94, H-23

---

## 3. `compra` e `parcela`

### RN-P01 — A entrada é o valor total (D-67)
O usuário informa `valorTotal` e `numeroParcelas`. **Não** informa valor de parcela.

**Origem**: RF-29 (**texto desatualizado — ver nota**), H-27

> ⚠️ **RF-29 e H-27 dizem o contrário** e ficaram desatualizados por decisão do usuário. A correção
> nos requisitos é pendência registrada, não reinterpretação.

### RN-P02 — Valor total positivo e ao menos uma parcela
**Origem**: RF-29, H-27 · **Erro**: `VALOR_INVALIDO`, `NUMERO_PARCELAS_INVALIDO` (400)

### RN-P03 — A última parcela absorve todo o resíduo (D-68)
```
base = piso(valorTotal em centavos / n)
parcelas 1..n-1 = base
parcela n       = valorTotal - (n-1) * base
```

**Origem**: RF-31, E-01, H-28 · **Verificação**: 🔬 property-based

> **Reverte o comportamento entregue em U1.** `Dinheiro.dividirEm` distribui hoje um centavo por
> parte, nas últimas — adotado porque o property-based testing encontrou o defeito da regra
> original (3.36, O-28). A reversão foi apresentada com os números e **confirmada pelo usuário**.
>
> **A propriedade *"partes diferem no máximo 0,01" deixa de valer** e é substituída por
> *"as primeiras n-1 são iguais entre si"*. A propriedade de soma exata continua valendo, e é a
> que importa.

### RN-P04 — Soma das parcelas igual ao total, sempre
Verificada em criação **e** em edição.

**Origem**: RF-32, H-29 · **Verificação**: 🔬 property-based — *a invariante monetária mais
importante do sistema*

### RN-P05 — A competência da parcela n é a da primeira mais n-1 meses
A primeira sai de RN-F01 aplicada a `dataCompra`.

**Origem**: RF-30, H-27

### RN-P06 — Edição é sempre da compra inteira
Alterar valor total ou número de parcelas **descarta todas** as parcelas e regenera. Parcela
individual não é editável.

**Origem**: RF-33, H-30 · **Erro**: `EDICAO_DE_PARCELA` (400)

### RN-P07 — Excluir a compra exclui as parcelas
E recalcula as faturas afetadas que não estejam pagas.

**Origem**: RF-34, H-31

### RN-P08 — Toda operação sobre compra respeita RN-F07
Se **qualquer** parcela cair em fatura PAGA, a operação é bloqueada.

**Origem**: RF-95, H-31, E-13 · **Erro**: `FATURA_PAGA` (409)

> **"Qualquer parcela" é o ponto delicado.** Uma compra de 12 parcelas toca 12 faturas. Verificar só
> a primeira deixaria passar a edição que altera uma parcela já paga lá na frente.

### RN-P09 — A posição é derivada
`"3/12"` é `numero` e `compra.numeroParcelas`. Não é campo.

**Origem**: RF-35, H-32

---

## 4. `conta` (a pagar)

### RN-A01 — Descrição, valor, vencimento próprio, tipo e categoria são obrigatórios
Cada conta tem **a sua** data de vencimento.

**Origem**: RF-55, H-42 · **Erro**: `DADOS_INVALIDOS` (400)

### RN-A02 — Quatro tipos
FATURA_CARTAO · PIX · BOLETO · FATURA_SERVICO.

**Origem**: RF-56

### RN-A03 — Status é EM ABERTO ou PAGA, com data no pagamento
`status == PAGA ⟺ dataPagamento != null`.

**Origem**: RF-57, H-44

### RN-A04 — Visão consolidada de vencimentos
Reúne **todos** os tipos no período, ordenada por vencimento, com total.

**Origem**: RF-58, H-43

> As ocorrências recorrentes ainda **não materializadas** entram nesta visão por projeção (RN-R02).
> Quem consulta não distingue.

### RN-A05 — O fechamento de fatura gera a conta automaticamente
Tipo FATURA_CARTAO, valor igual ao total consolidado, vencimento no `diaEfetivo(cartao.diaVencimento)`
do mês da competência.

**Origem**: RF-59, H-45

Conferido contra H-45 — cartão fecha 28, vence 5, fatura de agosto: fecha 28/07, vence **05/08**. ✓

### RN-A06 — Conta derivada de fatura não tem valor editável
O valor deriva dos lançamentos.

**Origem**: RF-59, H-45, P-11 · **Erro**: `CONTA_DERIVADA` (409)

### RN-A07 — Escopo e visibilidade como nos gastos
Qualquer membro edita ou exclui conta de grupo; o valor pertence integralmente ao dono.

**Origem**: RF-65, H-49

### RN-A08 — A vencer e vencidas
Horizonte configurável em dias; vencida é `dataVencimento < hoje` **e** `status == EM_ABERTO`.

**Origem**: RF-66, H-50

### RN-A09 — Origem única
`origemFatura` e `origemRecorrente` nunca são ambas preenchidas.

**Origem**: modelagem · **Verificação**: `CHECK` no banco

---

## 5. `recorrencia`

### RN-R01 — Ao cadastrar, pergunta-se se repete
Recorrentes e avulsas no mesmo modelo. Frequência **mensal** no MVP.

**Origem**: RF-62, H-46, P-10

### RN-R02 — Ocorrência materializa ao ser tocada (D-72)
A consulta **projeta** as ocorrências do período a partir da regra. A linha vira registro quando
ganha estado próprio: pagamento ou valor ajustado.

**Origem**: RF-63, H-47, **D-19 fechada**

> **Projetada e materializada precisam ser indistinguíveis** para quem consulta. A diferença é de
> armazenamento, não de negócio.

### RN-R03 — Ajustar o valor da ocorrência não muda o valor base
E as ocorrências futuras continuam usando o base.

**Origem**: RF-64, H-48

> É esta regra que **obriga** a ocorrência a ter identidade própria. Sem ela, D-72 poderia ser puro
> cálculo.

### RN-R04 — Uma ocorrência por competência
`(origemRecorrente, competenciaRecorrencia)` é único.

**Origem**: RF-63 · **Verificação**: índice único parcial, com teste de concorrência

### RN-R05 — Encerrar interrompe a geração, preserva o histórico
Nada é gerado com competência posterior a `encerradaEm`; as ocorrências existentes permanecem com o
seu status.

**Origem**: RF-67, H-51

### RN-R06 — O dia de vencimento segue RN-K03
Recorrente com vencimento dia 31 vence dia 28 em fevereiro.

**Origem**: E-11, D-69

---

## 6. Alvos de property-based testing

Modo **Parcial** (PBT-02, PBT-03, PBT-07, PBT-08, PBT-09 bloqueantes).

| # | Propriedade | Regra |
|---|---|---|
| 1 | **`soma(parcelas) == valorTotal`**, para qualquer valor e qualquer N | RN-P04 |
| 2 | As primeiras n-1 parcelas são **iguais entre si**; só a última difere | RN-P03 |
| 3 | Nenhuma parcela é negativa, para qualquer valor positivo | RN-P02, RN-P03 |
| 4 | A invariante 1 vale **após qualquer sequência** de criação e edição | RN-P04, H-29 |
| 5 | `diaEfetivo(d, ano, mês)` produz data válida para todo `d` em 1–31 e todo mês, inclusive fevereiro bissexto | RN-K03 |
| 6 | Para qualquer data e qualquer `diaFechamento`, a competência é determinística e monotônica: data maior nunca cai em competência menor | RN-F01 |
| 7 | A competência da parcela `n` é sempre a da primeira mais `n-1` meses, atravessando viradas de ano | RN-P05 |

> **A 4 é a mais valiosa e a mais cara.** Testa uma invariante **sobre sequências de operações**, não
> sobre um valor. É a forma que H-29 pede explicitamente — *"inclusive depois de eu editá-la"* — e a
> única que pega o erro de a edição regenerar parcelas sem revalidar a soma.

> **A 6 é a que protege contra o engano de RF-61.** Uma implementação que usasse o dia de vencimento
> passaria em muitos exemplos e falharia na monotonicidade em cartões cujo vencimento é no mês
> seguinte.

---

## 7. Rastreabilidade

| História | Regras |
|---|---|
| H-18 gerenciar cartões | RN-K01, RN-K04 |
| H-19 cartão pessoal ou de grupo | RN-K02 |
| H-20 fatura de competência | RN-F01, RN-K03 |
| H-21 fatura consolidada | RN-F02, RN-F03, RN-F06, RN-P09 |
| H-22 fatura aberta acumula | RN-F04 |
| H-23 marcar e desmarcar | RN-F06, RN-F09, RN-A03 |
| H-24 proteger fatura paga | **RN-F07**, RN-P08 |
| H-25 retroativo em fatura fechada | RN-F08 |
| H-26 faturas futuras | RN-F01, RN-P05 |
| H-27 lançar parcelada | RN-P01, RN-P02, RN-P05 |
| H-28 resíduo | **RN-P03** |
| H-29 integridade | **RN-P04** |
| H-30 corrigir compra | RN-P06 |
| H-31 excluir compra | RN-P07, RN-P08 |
| H-32 posição | RN-P09 |
| H-42 gerenciar contas | RN-A01, RN-A02 |
| H-43 vencimentos do período | RN-A04 |
| H-44 marcar paga | RN-A03 |
| H-45 fatura vira conta | RN-A05, RN-A06 |
| H-46 cadastrar recorrente | RN-R01 |
| H-47 gerar ocorrências | RN-R02, RN-R04 |
| H-48 ajustar no pagamento | RN-R03 |
| H-49 conta do grupo | RN-A07 |
| H-50 a vencer e vencidas | RN-A08 |
| H-51 encerrar recorrente | RN-R05 |

**37 regras**, 25 histórias cobertas. As jornadas J-01 e J-03 estão em `business-logic-model.md` §7.
