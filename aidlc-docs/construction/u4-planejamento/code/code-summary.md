# Resumo de Código — U4 Planejamento

**CI verde na primeira execução** — run `30817833392`, commit `c67bac5`. **A única unidade do ciclo
em que isso aconteceu.**

---

## 1. Escopo entregue

| | |
|---|---|
| Entidades | `Receita`, `Orcamento`, `ObjetivoInvestimento`, `Aporte` |
| Histórias | H-36 a H-41, H-52 a H-60 (15) + **J-02** |
| Regras | 26 |
| Decisões | D-77 a D-84 |
| Arquivos | 13 novos, 5 modificados |
| Migration | `V4__planejamento.sql` — 4 tabelas |

**Nenhuma alteração não-aditiva.** U3 reverteu `dividirEm`; U4 só acrescenta.

---

## 2. J-02, em código

A pergunta aberta desde as User Stories, resolvida e testada:

```
Compra de R$ 1.200,00 em 12x, em 30/07, cartao que fecha dia 28

Teto de julho    com base DATA_DA_COMPRA -> realizado 1.200,00  (estourado)
Teto de setembro com base COMPETENCIA    -> realizado   100,00  (dentro)
```

O SQL tem **duas formas**, e não só dois filtros:

| Base | Soma |
|---|---|
| `DATA_DA_COMPRA` | Gastos pela `data` + compras pelo **valor total** na `data_compra` |
| `COMPETENCIA` | Gastos e **parcelas** pela `competencia` |

**Só é possível porque U3 deixou as duas datas em cada parcela.** Foi a preparação que J-02 exigia,
feita uma unidade antes de ser usada.

---

## 3. D-81 — o padrão de leitura entre unidades

`ConsultaDeRealizado` vive no domínio de **`gasto`** (U2). `OrcamentoService` apenas consome: não
conhece tabela, não escreve SQL, não reimplementa predicado.

`BaseDoRealizado` ficou em `common` — se ficasse em `orcamento`, a porta de U2 importaria um tipo de
U4 e a seta apontaria da unidade mais antiga para a mais nova. **Primeira vez no ciclo que um
conceito nasce numa unidade e retrocede para `common`.**

---

## 4. As três decisões de estado

| Valor | Natureza | Tratamento |
|---|---|---|
| `totalAportado` | Soma | **Calculado** (D-82) |
| `saldoAtual` | Fato declarado pelo usuário | **Persistido** |
| Realizado do orçamento | Soma | **Calculado** |

> O critério do ciclo, consolidado: **se o número é uma soma, calcule; se é um fato, guarde.**

**D-80 e D-83 fecham a simetria**: aportar soma ao saldo, excluir subtrai. Sem D-83, excluir um
aporte de R$ 500 faria o rendimento **subir** R$ 500 sem erro e sem log.

---

## 5. A segunda divisão monetária do sistema

`CalculadoraDeAporte` arredonda **para cima**, e a direção é **oposta** à do parcelamento:

| | Invariante |
|---|---|
| Parcelamento (U3) | `soma(parcelas) == total` — **exata** |
| Aporte mensal (U4) | `mensal × meses >= falta` — **suficiente** |

Arredondar para baixo faria o usuário chegar ao prazo faltando centavos.

---

## 6. `Receita` exercita a metade do predicado que nunca rodou sozinha

É a **única entidade com dono e sem escopo** do sistema (P-05). O predicado de RN-V01 reduz-se a
`dono == usuarioAtual`.

Em U1, U2 e U3, toda entidade com dono também tinha escopo, e o `OU` sempre rodava completo. Um
defeito que só aparecesse com o lado direito ausente teria passado despercebido por três unidades.

---

## 7. O que o `ArquiteturaTest` encontrou

Ele reprovou **`Aporte`** na verificação local — e desta vez a regra estava **parcialmente certa**.

Ela supunha, sem dizer, que **ter `dono` implica ser raiz de agregado**. A suposição valeu por acaso
em três unidades: `MembroGrupo` e `Parcela` estão dentro de agregados e **não têm `dono`**. `Aporte`
tem, porque RF-75 exige — mas ali o `dono` é **atribuição, não visibilidade**.

Tratado com uma lista explícita e curta, `DENTRO_DE_AGREGADO`, com a justificativa escrita.

> Mesmo critério de `CartoesParaFechamento` em U3: **uma exceção nomeada continua sendo exceção;
> uma exceção embutida na regra a enfraquece para todos os usos.**

---

## 8. Testes

| Arquivo | Cobre |
|---|---|
| `InvestimentoPropriedadesTest` | 12 — rendimento, simetria aportar/excluir, **`mensal × meses >= falta`** |
| `PlanejamentoIntegracaoTest` | 15 — receita isolada, balanço com aporte, **J-02 nas duas bases**, consistência entre unidades, investimento de grupo |
| `ArquiteturaTest` | 4 — guarda estendida às 9 entidades com dono |

O teste *"as duas bases coincidem quando não há cartão"* é uma **prova de consistência entre
unidades**: se falhar, o realizado de U4 divergiu dos totais de U2.

---

## 9. Verificação final (Passo 29)

| Item | Resultado |
|---|---|
| `Double`/`Float` em caminho monetário | Nenhum |
| Divisão monetária | **Duas** no sistema: `dividirEm` (exata) e `CalculadoraDeAporte` (para cima). Nenhuma em SQL |
| Consulta de domínio sem filtro | Nenhuma nova |
| Total geral somando bases diferentes | Nenhum — verificado por teste |
| As 26 regras com teste | Sim |

---

## 10. O ciclo, do ponto de vista de U4

Com esta entrega:

- **Nenhuma decisão do ciclo permanece adiada** — J-02 era a última
- **Uma pendência atravessa para fora**: RF-29 e H-27 desatualizados por D-67 (U3), correção de texto
- **Um item** quebra com escala horizontal (`RegistroDeTentativas`, U1)
- **Zero** integrações externas, caches ou filas
