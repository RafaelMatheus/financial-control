# Plano de Code Generation — U4 Planejamento

> **A última Code Generation do ciclo.** Fonte única de verdade; cada passo tem checkbox, marcado no
> mesmo momento em que o trabalho é concluído. Desvios vão para §6.

---

## 1. Contexto

| | |
|---|---|
| **Componentes** | `receita`, `orcamento`, `investimento` |
| **Entidades** | `Receita`, `Orcamento`, `ObjetivoInvestimento`, `Aporte` |
| **Histórias** | H-36 a H-41, H-52 a H-60 (15) + **J-02** |
| **Regras** | 26 (RN-RC, RN-O, RN-I, RN-B) |
| **Decisões** | D-77 a D-84 |
| **Depende de** | U1, U2 e U3 — todas entregues, aprovadas, CI verde |

**A menor entrega de domínio do ciclo**, e a única que toca código de U2 de forma **aditiva**:
`gasto` ganha a implementação de `ConsultaDeRealizado` (D-81).

### 1.1 O que muda fora de U4

| Arquivo | Mudança | Por quê |
|---|---|---|
| `common/dominio/BaseDoRealizado.kt` | **Novo** | O tipo retrocede de U4 para `common` (D-81) — U2 e U3 sabem as duas datas, U4 escolhe |
| `gasto/dominio/Gasto.kt` | Porta `ConsultaDeRealizado` acrescentada | D-81: quem sabe somar é quem é dono |
| `gasto/adaptador/persistencia/GastoPersistencia.kt` | Implementa a porta | Uma consulta agrupada por categoria (D-84) |
| `common/web/Erros.kt` | Códigos novos | As 26 regras |

**Nenhuma alteração não-aditiva.** Diferente de U3, que reverteu `dividirEm`, U4 só acrescenta.

---

## 2. Onde o código vai

```
src/main/kotlin/com/rafaelmatheus/financialcontrol/
├── common/dominio/BaseDoRealizado.kt     NOVO — retrocedeu de U4 (D-81)
├── common/web/Erros.kt                   MODIFICADO
├── gasto/                                MODIFICADO — implementa ConsultaDeRealizado
├── receita/       dominio · aplicacao · adaptador{web,persistencia}
├── orcamento/     idem
└── investimento/  idem

src/main/resources/db/migration/V4__planejamento.sql
src/test/kotlin/...
```

---

## 3. Blocos e passos

### Bloco A — `common` e a porta de U2 (D-81)

- [x] **Passo 1** — `Erros.kt`: `ORCAMENTO_DUPLICADO`, `ORCAMENTO_NAO_ENCONTRADO`,
      `RECEITA_NAO_ENCONTRADA`, `OBJETIVO_NAO_ENCONTRADO`, `APORTE_NAO_ENCONTRADO`, `META_INVALIDA`
- [x] **Passo 2** — `common/dominio/BaseDoRealizado.kt`: enum `DATA_DA_COMPRA` / `COMPETENCIA`,
      com o registro de **por que ele mora em `common`** — *D-77, D-81*
- [x] **Passo 3** — 🔑 `gasto/dominio/`: porta **`ConsultaDeRealizado`** — *D-81, o padrão de leitura
      entre unidades*
- [x] **Passo 4** — `gasto/adaptador/persistencia/`: implementa a porta com **uma consulta agrupada
      por categoria** — *D-84. Duas formas de janela, uma por base*
- [x] **Passo 5** — ✅ **Verificação do bloco**

### Bloco B — `receita` (H-36 a H-38)

- [ ] **Passo 6** — `dominio/`: `Receita` **sem escopo** (P-05) + porta — *RN-RC01 a RC03*
- [ ] **Passo 7** — `aplicacao/ReceitaService`: CRUD, consulta por período e **balanço** — *RN-B01 a B03*
- [ ] **Passo 8** — `adaptador/`: persistência com só a primeira metade do predicado, e web
- [ ] **Passo 9** — Testes: receita é invisível a qualquer outro usuário, inclusive do mesmo grupo;
      balanço com aporte contando como gasto (H-38, H-59)
- [ ] **Passo 10** — ✅ **Verificação do bloco**

### Bloco C — `orcamento` e J-02 (H-39 a H-41)

- [ ] **Passo 11** — `dominio/`: `Orcamento` com `base` e `escopo` + porta — *RN-O01 a O03, D-77, D-78*
- [ ] **Passo 12** — `aplicacao/OrcamentoService`: definir, remover, **acompanhar** — *RN-O04 a O08*
- [ ] **Passo 13** — `adaptador/`: persistência e web. O DTO **sem total geral** — *RN-O08*
- [ ] **Passo 14** — Testes de `orcamento`: teto zero válido, duplicidade por escopo, estouro
      sinalizado com excedente, e **as duas bases dando resultados diferentes na mesma compra
      parcelada** — *J-02, o teste que a jornada pedia*
- [ ] **Passo 15** — Teste de que nenhum DTO soma bases diferentes — *RN-O08*
- [ ] **Passo 16** — ✅ **Verificação do bloco**

### Bloco D — `investimento` (H-52 a H-60)

- [ ] **Passo 17** — `dominio/`: `ObjetivoInvestimento`, `Aporte`, `CalculadoraDeAporte` + porta —
      *RN-I01 a I11*
- [ ] **Passo 18** — `aplicacao/InvestimentoService`: criar, aportar (**soma ao saldo**, D-80),
      excluir aporte (**subtrai**, D-83), atualizar saldo, posição consolidada
- [ ] **Passo 19** — `adaptador/`: `totalAportado` por `SUM` na leitura (D-82); web
- [ ] **Passo 20** — 🔬 **PBT do investimento**: `totalAportado` exato; `rendimento` sempre
      `saldo − aportado` inclusive negativo; **`aporteMensal × meses >= falta`** — *o alvo mais
      interessante, e de direção oposta à do parcelamento*
- [ ] **Passo 21** — Testes de integração: rendimento negativo exibido, objetivo de grupo com dois
      aportantes, aporte e exclusão como inversas
- [ ] **Passo 22** — ✅ **Verificação do bloco**

### Bloco E — persistência e isolamento

- [ ] **Passo 23** — `V4__planejamento.sql`: 4 tabelas, **único** `(dono, categoria, competencia,
      escopo, grupo)` em orçamento, `CHECK`s, FK de `aporte` **`CASCADE`** e de `categoria`
      **`RESTRICT`**
- [ ] **Passo 24** — 🔒 **Teste de isolamento das 4 entidades**, com atenção a `Receita` — a primeira
      entidade com dono e **sem escopo**, que exercita a metade do predicado que nunca rodou sozinha
- [ ] **Passo 25** — 🔬 Teste de **consistência entre unidades**: o realizado pelas duas bases
      coincide quando não há cartão — *alvo 5 do PBT*
- [ ] **Passo 26** — ✅ **Verificação do bloco**

### Bloco F — fechamento do ciclo

- [ ] **Passo 27** — `ArquiteturaTest`: estender a guarda contra vacuidade às 4 entidades novas.
      **`Receita` entra; `Aporte` não** — ele pertence ao agregado do objetivo
- [ ] **Passo 28** — Empurrar e **esperar o CI verde**
- [ ] **Passo 29** — Verificação final: nenhum `Double`/`Float` monetário; **a única divisão
      monetária nova é a de `CalculadoraDeAporte`, e ela arredonda para cima**; nenhuma consulta de
      domínio sem filtro; as 26 regras com teste
- [ ] **Passo 30** — Resumo em `aidlc-docs/construction/u4-planejamento/code/code-summary.md`

---

## 4. Rastreabilidade

| História | Passos | | História | Passos |
|---|---|---|---|---|
| H-36 receitas | 6, 7, 8, 9 | | H-53 aportar | 18, 20, 21 |
| H-37 consultar | 7, 8 | | H-54 atualizar saldo | 18, 21 |
| H-38 balanço | 7, 9 | | H-55 rendimento negativo | 20, 21 |
| H-39 definir teto | 11, 12, 14 | | H-56 progresso | 17, 20 |
| H-40 orçado × realizado | **12, 14** | | H-57 aporte mensal | **17, 20** |
| H-41 estouro | 12, 14 | | H-58 objetivo de grupo | 17, 21, 24 |
| H-52 criar objetivo | 17, 18 | | H-59 aporte no balanço | 7, 9 |
| | | | H-60 posição consolidada | 18, 19 |
| **J-02** | **3, 4, 12, 14, 25** | | | |

---

## 5. Escopo e riscos

**30 passos, 6 blocos.** Estimativa: **~14 arquivos novos** e **4 modificados**.

| Risco | Tratamento |
|---|---|
| A porta de D-81 crescer para servir só a U4 e virar acoplamento | Ela expõe **uma** operação, com parâmetros que U2 já entende. Se precisar de mais, é sinal de que a fronteira está errada |
| `Receita` sem escopo escapar do padrão de visibilidade | Passo 24. Ela continua estendendo a porta; o que muda é só a segunda metade do OU não existir |
| A divisão de `CalculadoraDeAporte` arredondar para baixo | Passo 20. A propriedade é `aporteMensal × meses >= falta` — **suficiente**, não exata |
| O DTO de acompanhamento ganhar um total geral por descuido | Passo 15, teste dedicado. Terceira vez no ciclo |
| A última unidade herdar pressa | Blocos com verificação intermediária, como em U3 |

---

## 6. Desvios de execução

*Preenchido durante a execução.*
