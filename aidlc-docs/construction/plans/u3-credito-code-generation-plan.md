# Plano de Code Generation — U3 Crédito

> **Fonte única de verdade da Code Generation de U3.** Cada passo tem checkbox, marcado no mesmo
> momento em que o trabalho é concluído. Desvios vão para §7, inclusive os que derem certo.

---

## 1. Contexto

| | |
|---|---|
| **Componentes** | `cartao`, `fatura`, `conta`, `compra`, `gasto` (integração) |
| **Entidades** | `Cartao`, `Fatura`, `Compra`, `Parcela`, `ContaAPagar`, `ContaRecorrente` |
| **Histórias** | H-18 a H-32, H-42 a H-51 (25) + J-01 e J-03 |
| **Requisitos** | RF-23 a RF-35, RF-55 a RF-67, RF-94 a RF-96 (31) |
| **Regras** | 37 (RN-K, RN-F, RN-P, RN-A, RN-R) |
| **Decisões** | D-67 a D-76 |
| **Depende de** | U1 e U2 — entregues, aprovadas, CI verde |

**A maior entrega do projeto.** Por D-76 sai numa vez só; a mitigação é a organização em **oito
blocos com verificação intermediária** — compilar e rodar a suíte local ao fim de cada um.

### 1.1 Esta é a primeira entrega que altera código de U1 de forma não-aditiva

`Dinheiro.dividirEm` muda de comportamento (D-68), e a propriedade *"partes diferem no máximo 0,01"*
**passa a ser falsa**. Está no Bloco A, isolado e primeiro, para que a alteração seja verificada
antes de qualquer coisa depender dela.

**Critério de aceitação da alteração**: a suíte de `Dinheiro` fica verde **depois** de a propriedade
obsoleta ser substituída — nunca desligada.

---

## 2. Onde o código vai

```
src/main/kotlin/com/rafaelmatheus/financialcontrol/
├── common/
│   ├── dominio/Dinheiro.kt            MODIFICADO — D-68
│   ├── dominio/CalculadoraDeCompetencia.kt   NOVO — funcao pura
│   ├── web/Erros.kt                   MODIFICADO — codigos novos
│   └── agendamento/TravaDeExecucao.kt NOVO — advisory lock (D-74)
├── cartao/       dominio · aplicacao · adaptador{web,persistencia}
├── fatura/       dominio · aplicacao{FaturaService, ProtecaoFatura} · adaptador · FechamentoAgendado
├── compra/       dominio{Compra,Parcela,DivisorDeParcelas} · aplicacao · adaptador
├── conta/        dominio{ContaAPagar,ContaRecorrente} · aplicacao · adaptador
└── gasto/        MODIFICADO — integracao com cartao

src/main/resources/db/migration/V3__credito.sql
src/test/kotlin/...
```

---

## 3. Blocos e passos

### Bloco A — `common`, e a reversão de D-68

- [x] **Passo 1** — `Erros.kt`: códigos novos — `DIA_INVALIDO`, `CARTAO_ENCERRADO`,
      `CARTAO_NAO_ENCONTRADO`, `FATURA_NAO_ENCONTRADA`, `FATURA_PAGA`, `NUMERO_PARCELAS_INVALIDO`,
      `EDICAO_DE_PARCELA`, `CONTA_NAO_ENCONTRADA`, `CONTA_DERIVADA`, `RECORRENTE_NAO_ENCONTRADA`
- [x] **Passo 2** — ⚠️ **`Dinheiro.dividirEm`**: última parte absorve o resíduo — *D-68, RN-P03*
- [x] **Passo 3** — ⚠️ `DinheiroPropriedadesTest`: **substituir** a propriedade *"partes diferem no
      máximo 0,01"* por *"as primeiras n-1 são iguais entre si"*. Manter soma exata e não-negatividade
- [x] **Passo 4** — `CalculadoraDeCompetencia`: `diaEfetivo` (RN-K03, D-69) e `competenciaDe`
      (RN-F01). **Função pura, sem banco**
- [x] **Passo 5** — 🔬 Testes de propriedade da calculadora: dia válido para todo dia 1–31 e todo
      mês incluindo fevereiro bissexto; competência determinística e **monotônica**
- [x] **Passo 6** — ✅ **Verificação do bloco**: compilar e rodar a suíte local

### Bloco B — `cartao` (H-18, H-19)

- [x] **Passo 7** — `dominio/`: `Cartao` + porta `CartaoRepositorio` — *RN-K01, RN-K02, RN-K04*
- [x] **Passo 8** — `aplicacao/CartaoService`: cadastrar, editar, encerrar, listar
- [x] **Passo 9** — `adaptador/`: persistência com o critério de visibilidade, e web
- [x] **Passo 10** — Testes de `cartao`, incluindo dias 29–31 aceitos no cadastro
- [x] **Passo 11** — ✅ **Verificação do bloco**

### Bloco C — `fatura` e a guarda transversal (H-20 a H-26)

- [x] **Passo 12** — `dominio/`: `Fatura` **sem `valorTotal`** (D-75) + porta
- [x] **Passo 13** — 🔒 **`ProtecaoFatura`** — *RN-F07, D-73. Um dos dois passos mais importantes*
- [x] **Passo 14** — `aplicacao/FaturaService`: consultar (com `SUM`), futuras, recalcular, reabrir.
      **Sem `fechar` público** — o fechamento é do job
- [x] **Passo 15** — `adaptador/persistencia/`: `SUM` da competência (D-75) e **a guarda de D-73 na
      gravação**
- [x] **Passo 16** — `adaptador/web/`: consultar fatura e faturas futuras
- [x] **Passo 17** — feito no Bloco D, em `FaturaIntegracaoTest` (ver §7)
      status derivado, bloqueio de fatura paga, reabertura por lançamento retroativo
- [x] **Passo 18** — compilacao verde; testes de comportamento no Bloco D — ✅ **Verificação do bloco**

### Bloco D — `compra`, `parcela` e a integração de `gasto` (H-27 a H-32)

- [x] **Passo 19** — `dominio/`: `Compra`, `Parcela`, `DivisorDeParcelas`, porta — *RN-P01 a P09*
- [x] **Passo 20** — `aplicacao/CompraService`: lançar, editar (**união das competências antigas e
      novas**), excluir, consultar
- [x] **Passo 21** — `adaptador/` de `compra`
- [x] **Passo 22** — **Integração de `gasto` com cartão**: `GastoService` passa a aceitar `cartaoId`,
      calcula a competência e invoca `ProtecaoFatura`. **Segunda metade do componente iniciado em U2**
- [x] **Passo 23** — 🔬 **PBT do parcelamento**: soma exata; primeiras n-1 iguais; nenhuma negativa;
      **invariante após qualquer sequência de criação e edição** (H-29 — o alvo mais valioso)
- [x] **Passo 24** — Testes de integração de `compra`, incluindo edição que reduz parcelas tocando
      fatura paga
- [x] **Passo 25** — ✅ **Verificação do bloco**

### Bloco E — `conta` e recorrência (H-42 a H-51)

- [ ] **Passo 26** — `dominio/`: `ContaAPagar`, `ContaRecorrente`, portas — *RN-A01 a A09, RN-R01 a R06*
- [ ] **Passo 27** — `aplicacao/ContaService`: cadastrar, editar, excluir, marcar paga (com valor
      ajustado), desmarcar, vencimentos do período, a vencer, vencidas
- [ ] **Passo 28** — `aplicacao/RecorrenteService` e a **projeção de ocorrências** (D-72), com a
      materialização ao ser tocada
- [ ] **Passo 29** — `adaptador/` de `conta` e de `recorrente`
- [ ] **Passo 30** — Testes: visão consolidada reunindo os quatro tipos ordenados; ajuste de valor
      não alterando o valor base; encerrar preservando histórico
- [ ] **Passo 31** — ✅ **Verificação do bloco**

### Bloco F — o job (D-71, D-74)

- [ ] **Passo 32** — `TravaDeExecucao`: `pg_try_advisory_lock` — *D-74*
- [ ] **Passo 33** — `FechamentoAgendado`: `@Scheduled`, fecha **todas** as faturas com janela
      terminada, gera a conta a pagar com o valor do momento — *RN-F05, RN-A05*
- [ ] **Passo 34** — `@EnableScheduling` e configuração do horário em `application.yml`
- [ ] **Passo 35** — Testes do job: **idempotência** (rodar duas vezes não gera duas contas),
      **recuperação** (três dias parado recupera os três), e o lock impedindo execução concorrente

### Bloco G — persistência e isolamento

- [x] **Passo 36** — ANTECIPADO para o Bloco B (ver §7) — `V3__credito.sql`: 6 tabelas, único `(cartao_id, competencia)`, único
      `(compra_id, numero)`, **índice único parcial** `(origem_recorrente_id, competencia)`,
      `CHECK`s, FK de `parcela` **`CASCADE`** e de `categoria` **`RESTRICT`**
- [ ] **Passo 37** — 🔒 **Teste de isolamento das 6 entidades novas** — replica os casos de H-03
- [ ] **Passo 38** — Teste de concorrência: materialização simultânea da mesma ocorrência recorrente
- [ ] **Passo 39** — ✅ **Verificação do bloco**

### Bloco H — fechamento

- [ ] **Passo 40** — **`ArquiteturaTest`**: confirmar que as 6 entidades novas são cobertas
      automaticamente e **atualizar a guarda contra vacuidade** para incluí-las — *primeiro retorno
      concreto de D-66*
- [ ] **Passo 41** — Empurrar e **esperar o CI verde**. A stage não se declara concluída antes
- [ ] **Passo 42** — Verificação final: nenhum `Double`/`Float` monetário; **nenhuma divisão
      monetária em SQL**; nenhuma consulta de domínio sem filtro; nenhuma gravação de entidade com
      competência fora da guarda de D-73; as 37 regras com teste
- [ ] **Passo 43** — Resumo em `aidlc-docs/construction/u3-credito/code/code-summary.md`

---

## 4. Rastreabilidade

| História | Passos | | História | Passos |
|---|---|---|---|---|
| H-18 cartões | 7, 8, 9, 10 | | H-42 contas | 26, 27, 30 |
| H-19 cartão de grupo | 7, 9, 37 | | H-43 vencimentos | 27, 28, 30 |
| H-20 competência | 4, 5, 17 | | H-44 marcar paga | 27, 30 |
| H-21 consolidada | 14, 16, 17 | | H-45 fatura vira conta | 33, 35 |
| H-22 acumula | 14, 17 | | H-46 cadastrar recorrente | 26, 28 |
| H-23 marcar/desmarcar | 27, 17 | | H-47 gerar ocorrências | 28, 30, 38 |
| H-24 proteger paga | **13, 15**, 17, 24 | | H-48 ajustar valor | 28, 30 |
| H-25 retroativo | 14, 17 | | H-49 conta de grupo | 26, 37 |
| H-26 futuras | 14, 16 | | H-50 a vencer/vencidas | 27, 30 |
| H-27 a H-32 parcelamento | 19–24, **23** | | H-51 encerrar | 28, 30 |

---

## 5. Escopo

**43 passos, 8 blocos.** Estimativa: **~28 arquivos novos** e **~6 modificados**
(`Dinheiro.kt`, `DinheiroPropriedadesTest.kt`, `Erros.kt`, `GastoService.kt`, `GastoPersistencia.kt`,
`application.yml`, `FinancialControlApplication.kt`).

**O que este plano não faz**: nada de receita, orçamento ou investimento (U4); nenhum front-end;
nenhuma correção de RF-29/H-27 nos requisitos — é pendência registrada, não código.

---

## 6. Riscos

| Risco | Tratamento |
|---|---|
| **A alteração de `dividirEm` quebrar U1** | É o Bloco A, isolado e primeiro. A propriedade obsoleta é **substituída**, nunca desligada. Se a propriedade de soma exata quebrar, o problema é a implementação nova — não a propriedade |
| **A guarda de D-73 ser esquecida em algum caminho de gravação** | Passo 42 verifica explicitamente. Candidato a regra do `ArquiteturaTest` se a verificação manual se mostrar frágil |
| Testar o job sem esperar um dia | O `Clock` já é injetado desde U1 — precisamente para isto (`ConfiguracaoComum`). O teste avança o relógio, não dorme |
| O advisory lock não liberar após exceção | `try/finally` no `TravaDeExecucao`, e o lock de sessão do PostgreSQL cai junto com a conexão |
| Um plano de 43 passos ser executado sem atenção no fim | **Blocos com verificação intermediária** (D-76). Seis pontos de parada antes do CI |
| A união de competências na edição de compra ser esquecida | Passo 20 a nomeia explicitamente, e o Passo 24 tem teste dedicado |
| `V3` reprovar no `ddl-auto: validate` | Ajustar a migration, nunca desligar o `validate` |

---

## 7. Desvios de execução

*Preenchido durante a execução.*
