# Execução dos Testes de Unidade

Testes que **não precisam de Docker**: rodam contra objetos em memória, sem banco e sem contexto
Spring. São 82 dos 199.

---

## 1. Rodar

```bash
gradle test --tests '*Propriedades*' \
            --tests '*ArquiteturaTest*' \
            --tests '*CalculadoraDeCompetenciaTest*' \
            --tests '*EmissorDeTokenTest*' \
            --tests '*RegistroDeTentativasTest*' \
            --no-daemon
```

Relatório em `build/reports/tests/test/index.html`.

---

## 2. O que existe

| Classe | Testes | Unidade | O que prende |
|---|---|---|---|
| `DinheiroPropriedadesTest` | 18 | U1 | Aritmética monetária e a divisão de D-68 |
| `CompetenciaPropriedadesTest` | 10 | U1 | Ano-mês, virada de ano |
| `EmissorDeTokenTest` | 7 | U1 | Emissão, validação, expiração, assinatura errada |
| `RegistroDeTentativasTest` | 7 | U1 | Bloqueio por força bruta e expiração |
| `TotaisPropriedadesTest` | 7 | U2 | As duas grandezas de RF-97 e a bicondicional de visibilidade |
| `CalculadoraDeCompetenciaTest` | 11 | U3 | Dia efetivo, **monotonicidade**, os 3 cenários de H-20 |
| `ParcelamentoPropriedadesTest` | 10 | U3 | Soma exata e **a invariante após sequências de edição** |
| `InvestimentoPropriedadesTest` | 12 | U4 | Rendimento e **`mensal × meses >= falta`** |
| `ArquiteturaTest` | 4 | U2+ | As regras estruturais de D-66 |

**82 testes.** São os que dão retorno em segundos e os que devem rodar a cada alteração.

---

## 3. Property-based testing (RNF-07, D-05, extensão PBT em modo Parcial)

**Kotest Property**. Regras bloqueantes: PBT-02, PBT-03, PBT-07, PBT-08, PBT-09.

### 3.1 As invariantes que valem a pena conhecer

| Propriedade | Onde | Por quê |
|---|---|---|
| `soma(parcelas) == valorTotal` | `ParcelamentoPropriedadesTest` | RF-32. Se falhar, o usuário vê um total que não bate com o que a loja cobrou |
| A mesma, **após qualquer sequência de edições** | idem | H-29 pede explicitamente. É o único teste que pega a edição regenerando parcelas sem revalidar a soma |
| `mensal × meses >= falta` | `InvestimentoPropriedadesTest` | RF-74. Direção **oposta** à do parcelamento: aqui a soma precisa ser **suficiente**, não exata |
| Visibilidade é **bicondicional** | `TotaisPropriedadesTest` | Não basta que o visível apareça — o invisível não pode aparecer |
| Competência é **monotônica** | `CalculadoraDeCompetenciaTest` | Pega o engano de usar `diaVencimento` no lugar de `diaFechamento` (RF-61) |

### 3.2 Reprodutibilidade (PBT-08)

Ao falhar, o Kotest imprime o **seed**. Para reexecutar o caso exato:

```bash
gradle test --tests '*ParcelamentoPropriedadesTest*' \
  -Dkotest.proptest.seed=<seed> --no-daemon
```

> **O shrinking já pagou neste projeto.** Em U1, o primeiro contraexemplo de `dividirEm` veio com
> 118 partes — número em que ninguém raciocina. O shrinking devolveu 6, e com 6 dá para fazer a
> conta de cabeça e ver o erro (research-log 3.36).

---

## 4. O teste de arquitetura (D-66)

`ArquiteturaTest` não testa comportamento: testa **estrutura**. Quatro regras:

1. `dominio` não conhece JPA nem Spring — a seta aponta para dentro (D-51)
2. Controller não fala com adaptador de persistência
3. **Toda entidade com `dono` tem porta que estende `RepositorioComVisibilidade`**
4. Método de porta que devolve coleção recebe filtro (D-63)

### 4.1 Duas salvaguardas dentro dele

**Guarda contra vacuidade**: o teste falha se a detecção **não encontrar** as 9 entidades com dono.
Um teste de arquitetura que não acha nada passa — e passa em silêncio, para sempre.

**Lista `DENTRO_DE_AGREGADO`**: `Aporte` tem `dono` e **não** é raiz de agregado. A regra supunha,
sem dizer, que uma coisa implica a outra — suposição que valeu por acaso em três unidades. A exceção
é explícita e curta, com a justificativa no código.

### 4.2 O que ele **não** pega

- **N+1** — é comportamental, não estrutural
- Se o predicado de visibilidade devolve as linhas certas — isso é dos testes de isolamento

> Nem toda garantia cabe numa regra de arquitetura, e supor o contrário é como supor que o
> compilador pega lógica errada.
