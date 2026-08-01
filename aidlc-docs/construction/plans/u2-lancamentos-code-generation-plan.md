# Plano de Code Generation — U2 Lançamentos

> **Este plano é a fonte única de verdade da Code Generation de U2.** Cada passo tem checkbox e é
> marcado no mesmo momento em que o trabalho é concluído. Desvios vão para §6, inclusive os que
> derem certo.

---

## 1. Contexto da unidade

| | |
|---|---|
| **Componentes** | `categoria`, `gasto` (**à vista apenas**) |
| **Entidades** | `Categoria`, `Gasto` |
| **Histórias** | H-09, H-13, H-14, H-15, H-16, H-17, H-33, H-34, H-35 (9) |
| **Requisitos** | RF-11, RF-16 a RF-22, RF-36 a RF-38, RF-97 |
| **Regras de negócio** | 23 (RN-C01 a C08, RN-L01 a L10, RN-T01 a T07) |
| **Decisões** | D-54 a D-66 |
| **Depende de** | **U1 — entregue, aprovada, CI verde** |
| **Bloqueia** | U3, U4 |

**Estado do repositório**: brownfield com U1 em produção do ponto de vista do código. 24 arquivos
Kotlin em `src/main`, 11 em `src/test`, migration `V1__fundacao.sql` aplicada.

### 1.1 O que U2 consome de U1, sem alterar

| Artefato de U1 | Uso em U2 |
|---|---|
| `Dinheiro` | Tipo do `valor` do gasto |
| `Escopo` | PESSOAL / GRUPO, em gasto **e** categoria (D-54) |
| `Competencia` | Só o campo nulo do gasto; nenhum uso ativo até U3 |
| `ContextoUsuario.criterio()` | Origem do `CriterioVisibilidade` em todo adaptador |
| `RepositorioComVisibilidade` | Porta-base que as duas portas novas estendem |
| `CodigoErro` / `ErroDeNegocio` / `ErroHandler` | Erros da unidade |
| `ConsultaDeGrupos` | Já implementada pelo adaptador de `grupo`; U2 só consome |

### 1.2 O que U2 deixa como contrato para U3 e U4

`GastoRepositorio` e `CategoriaRepositorio` como **modelo** de porta por feature (D-63); o padrão de
projeção de leitura (D-65); o teste de arquitetura (D-66), que passa a valer para toda entidade nova.

---

## 2. Onde o código vai

**Raiz do workspace**, nunca em `aidlc-docs/`. Mesma forma de U1 (D-51, D-03):

```
src/main/kotlin/com/rafaelmatheus/financialcontrol/
├── common/
│   └── web/Erros.kt                          MODIFICADO — códigos novos
├── categoria/
│   ├── dominio/Categoria.kt                  Categoria + CategoriaRepositorio (porta)
│   ├── aplicacao/CategoriaService.kt
│   └── adaptador/
│       ├── web/CategoriaController.kt        Controller + DTOs
│       └── persistencia/CategoriaPersistencia.kt   JPA + SpringData + adaptador + mapeadores
└── gasto/
    ├── dominio/Gasto.kt                      Gasto + GastoRepositorio + FiltroGasto + projeções
    ├── aplicacao/GastoService.kt
    └── adaptador/
        ├── web/GastoController.kt
        └── persistencia/GastoPersistencia.kt

src/main/resources/db/migration/V2__lancamentos.sql
src/test/kotlin/com/rafaelmatheus/financialcontrol/
├── ArquiteturaTest.kt                        NOVO — D-66
├── CategoriaIntegracaoTest.kt
├── GastoIntegracaoTest.kt
├── IsolamentoDeGastosTest.kt                 o mais importante da unidade
├── TotaisPropriedadesTest.kt
└── categoria|gasto/aplicacao/*Test.kt        unidade, sem banco
```

**Um arquivo por camada de feature**, como em U1: `GrupoPersistencia.kt` reúne entidade JPA, Spring
Data, adaptador e mapeadores. Manter a convenção importa mais que a granularidade ideal.

---

## 3. Passos

### Preparação

- [x] **Passo 1** — `build.gradle.kts`: adicionar **ArchUnit** (`com.tngtech.archunit:archunit-junit5`)
      como dependência de teste — *D-66*
- [x] **Passo 2** — `common/web/Erros.kt`: acrescentar os códigos que as 23 regras exigem —
      `VALOR_INVALIDO`, `CATEGORIA_OBRIGATORIA`, `CATEGORIA_NAO_ENCONTRADA`, `CATEGORIA_DUPLICADA`,
      `CATEGORIA_EM_USO`, `REALOCACAO_INVALIDA`, `GRUPO_INVALIDO`, `GRUPO_OBRIGATORIO`,
      `LANCAMENTO_NAO_ENCONTRADO`. **Modificação, não arquivo novo**

### `categoria` — H-33, H-34, H-35

- [x] **Passo 3** — `dominio/`: `Categoria` puro (sem JPA), com a bicondicional escopo↔grupo na
      construção, e a porta `CategoriaRepositorio` estendendo `RepositorioComVisibilidade` —
      *RN-C01, RN-C03, D-63*
- [x] **Passo 4** — `aplicacao/CategoriaService`: criar, renomear, excluir (com realocação
      transacional), listar (com o conjunto inicial sob demanda) — *RN-C02, C04 a C08*
- [x] **Passo 5** — `adaptador/persistencia/`: `CategoriaJpa`, Spring Data, adaptador aplicando o
      critério, mapeadores. **`saveAndFlush`**, pela lição do defeito 1 de `cd310cb`
- [x] **Passo 6** — `adaptador/web/`: `CategoriaController` e DTOs com Bean Validation.
      **Sem `@Email`-style de validação que duplique regra de domínio**, pela lição do defeito 2
- [x] **Passo 7** — Testes de `categoria`: unidade do serviço; integração com Testcontainers para
      nome duplicado (inclusive a corrida), unicidade por grupo vs. por dono, exclusão bloqueada com
      a contagem, realocação alcançando gasto de outro dono, conjunto inicial na primeira listagem e
      o ressurgimento após apagar todas

### `gasto` — H-09, H-13 a H-17

- [x] **Passo 8** — `dominio/`: `Gasto` puro com as 5 invariantes; `FiltroGasto` com `de`/`ate`
      **obrigatórios**; `GastoRepositorio` com `consultar`, `totalizar`, `contarPorCategoria` e
      `realocarCategoria`; projeções `ItemGasto`, `PaginaDeGastos`, `TotaisDeGastos`,
      `TotalPorCategoria` — *RN-L01 a L10, **D-63, D-65***
- [x] **Passo 9** — `aplicacao/GastoService`: lançar, editar, excluir, consultar, totalizar.
      `lancar` **não recebe `donoId`** — a regra fica na assinatura — *RN-L05*
- [x] **Passo 10** — `adaptador/persistencia/`: `GastoJpa` (com `cartao_id` e `competencia`
      nuláveis), consulta de projeção com `JOIN`, consulta de agregação com `SUM`, e o predicado de
      visibilidade aplicado nas duas — *D-64, D-65*
- [x] **Passo 11** — `adaptador/web/`: `GastoController` com `GET /gastos` (paginado, teto 100) e
      `GET /gastos/totais` — *D-57*
- [x] **Passo 12** — Testes de `gasto`: unidade do serviço; integração para valor não positivo,
      gasto sem categoria, escopo GRUPO sem grupo (E-09), edição por outro membro preservando o
      dono, troca de escopo com efeito retroativo, data futura aceita

### Persistência

- [x] **Passo 13** — `V2__lancamentos.sql`: tabelas `categoria` e `gasto`; **dois índices únicos
      parciais** por escopo em categoria; `CHECK` das bicondicionais; `CHECK (valor > 0)`; FK de
      categoria como **`RESTRICT`**; índices `gasto(dono_id, data)` e `gasto(grupo_id, data)` —
      *RNF-04, D-01*
- [x] **Passo 14** — 🔒 **Teste de isolamento de gastos** — o mais importante da unidade. Dois
      usuários, um grupo, cobrindo: gasto PESSOAL invisível a membro do mesmo grupo; gasto GRUPO
      visível a todos os membros; nada visível a quem não é membro; ex-membro perdendo acesso e os
      gastos dele permanecendo para os demais — *RN-T01, RN-L07, D-62, H-03*
- [x] **Passo 15** — Teste de concorrência: dois cadastros simultâneos da mesma categoria no mesmo
      grupo, verificando que o índice parcial barra o segundo

### Os dois totais

- [x] **Passo 16** — 🔬 **Testes de propriedade dos totais** com Kotest: `totalPessoal` contém
      exatamente os gastos do consultante; `totalGrupo` contém exatamente os de escopo GRUPO
      daquele grupo; a soma das quebras por categoria bate com o total correspondente —
      *PBT-02, PBT-03, PBT-07, PBT-08*
- [x] **Passo 17** — Teste de que **nenhum DTO de saída** contém a soma dos dois totais — *RN-T04*
- [x] **Passo 18** — Teste de propriedade da bicondicional de visibilidade: para qualquer par
      (usuário, gasto), o gasto aparece na listagem **se e somente se** RN-V01 for verdadeiro —
      *o alvo mais valioso e mais caro de gerar*

### Imposição estrutural

- [x] **Passo 19** — 🔒 **`ArquiteturaTest`** (D-66): reprova o build quando uma entidade com campo
      `dono` tem repositório que não estende a porta; quando um repositório de domínio devolve
      entidade sem filtro obrigatório; quando um adaptador de persistência é injetado direto num
      controller; quando uma classe de `dominio` importa `jakarta.persistence` ou `org.springframework`
- [x] **Passo 20** — Rodado contra U1: **nenhuma reprovação**. A estrutura entregue estava limpa
      nas quatro regras. Achado colateral em §7

### Fechamento

- [x] **Passo 21** — Compilar e rodar a suíte local (a que não depende de Docker)
- [x] **Passo 22** — CI **verde** no run `30715674722` (commit `ed55e3c`). A primeira execução
      (`30715540122`) reprovou 2 de 82 — os dois estão em §7
- [x] **Passo 23** — Verificação final: nenhum `Double`/`Float` em caminho monetário; nenhuma
      divisão monetária em SQL; nenhuma consulta de domínio sem filtro; nenhum `save` sem flush em
      caminho que dependa de restrição do banco; as 23 regras com teste que falha se a regra sair
- [x] **Passo 24** — Resumo em `aidlc-docs/construction/u2-lancamentos/code/code-summary.md`

---

## 4. Rastreabilidade

| História | Passos |
|---|---|
| H-09 Definir escopo | 8, 9, 12 |
| H-13 Editar como qualquer membro | 9, 12, **14** |
| H-14 Registrar o dono | 8, 9, 12 |
| H-15 Gerenciar gastos | 8, 9, 11, 12 |
| H-16 Consultar por período | 8, 10, 11, 12, 18 |
| H-17 Os dois totais | 10, 11, **16**, **17** |
| H-33 Gerenciar categorias | 3, 4, 5, 6, 7 |
| H-34 Proteger categoria em uso | 4, 7, 13 |
| H-35 Categorias iniciais | 4, 7 |

---

## 5. Escopo e riscos

**24 passos.** Cerca de **12 arquivos novos** e **2 modificados** (`build.gradle.kts`, `Erros.kt`).

| Risco | Tratamento |
|---|---|
| **O `ArquiteturaTest` reprovar código de U1 já entregue** | É o Passo 20, e é o risco mais interessante da unidade. Se acontecer, o achado é real e vale mais que a regra — pode ser que U1 tenha um desvio, ou que a regra esteja mal escrita. **Analisar antes de ajustar qualquer um dos dois**, e registrar em §6 |
| Duas aritméticas monetárias divergirem | **Risco aceito por decisão** (D-64). Sem teste de comparação, por escolha registrada na NFR Design |
| `ddl-auto: validate` reprovar o mapeamento contra `V2` | Ajustar a migration, nunca desligar o `validate`. Os índices parciais e os `CHECK` são invisíveis ao `validate` e vivem só na migration |
| A projeção divergir da entidade numa mudança de schema | As duas leem a mesma tabela; uma mudança quebra as duas ao mesmo tempo, que é o comportamento desejável. Coberto pelo teste de integração |
| O gerador do Passo 18 ficar caro e a propriedade virar lenta | Se ficar, reduzir o número de casos e **manter a propriedade**, nunca enfraquecer a asserção |
| Testes de integração só rodarem no CI | Reconhecido de antemão. É a razão do Passo 22 existir como passo, e não como esperança |

**O que este plano não faz**: nada de cartão, fatura, parcela, compra ou conta a pagar; nada de
receita, orçamento ou investimento; nenhum front-end; nenhuma alteração em entidade de U1.

---

## 6. Desvios de execução

| Passo | Desvio | Motivo |
|---|---|---|
| 2 | **Três** arquivos modificados, não dois: `ErroHandler.kt` entrou na lista | Sem ele, `?tamanho=1000000` cairia no handler genérico e viraria **500** — erro de cliente respondido como falha de servidor. Descoberto ao escrever o teste de paginação |
| 3 | `Dinheiro` de U1 ganhou `ehPositivo()` | Adição pura de 3 linhas. A alternativa era repetir `!ehZero() && !ehNegativo()` em cada ponto, e ninguém lê negação dupla certo na primeira passada |
| 8 | Duas classes de linha (`LinhaGasto`, `LinhaTotalCategoria`) que o plano não previa | `Dinheiro` é value class de construtor privado, e a expressão `new` do JPQL só chama construtor público com os tipos que o banco devolve. A conversão ficou na borda, em um lugar só |
| 19 | O `ArquiteturaTest` ganhou uma **guarda contra vacuidade** | Um teste de arquitetura que não encontra nada passa — e passa em silêncio, para sempre. Se um refactor quebrar a detecção, a regra deixaria de proteger qualquer coisa sem sinal algum |
| 15 | Helper de teste `adicionarMembro` renomeado para `adicionarMembroAoGrupo` | Colidia com helpers privados de dois testes de U1. Renomear o meu foi preferível a alterar arquivos de uma unidade fechada |

---

## 7. O que o CI encontrou

**Primeira execução: 2 falhas em 82 testes** (run `30715540122`). As duas invisíveis sem banco real.

### 1. Defeito no código — a transação morta

No ramo de corrida de `listar()`, eu capturava a violação de unicidade e **relia na mesma
transação**. No PostgreSQL, uma violação de restrição aborta a transação inteira (`SQLSTATE 25P02`)
e todo comando seguinte falha até o fim do bloco — a releitura acontecia de dentro de uma transação
morta.

**Correção**: a criação do conjunto inicial passou para bean próprio, com transação própria. Quando
falha, rola atrás de si, e a releitura roda numa transação limpa. Um método do próprio serviço não
resolveria: auto-invocação não passa pelo proxy do Spring, e a transação seria a mesma.

> **A assimetria com U1 é o que torna o defeito interessante.** O mesmo `catch (Duplicada)` existe
> em `UsuarioService` e em `GrupoService` desde U1, e funciona — porque ali ele **lança** em
> seguida, e o rollback vem junto. O padrão só quebra quando se tenta *continuar*. Copiar um padrão
> que funciona não é o mesmo que copiar as condições em que ele funciona.

### 2. Defeito no teste — a regra que eu mesmo escrevi

Afirmei `totalPessoal = 0.00` para um gasto de escopo GRUPO cuja dona é a própria consultante.
RN-T02, escrita por mim três arquivos antes, diz o contrário: ele entra **nos dois** totais.
O código estava certo; o teste, errado.

### Achado colateral do Passo 20

O `ArquiteturaTest` **não reprovou nada** do código de U1. As quatro regras — domínio sem JPA nem
Spring, controller sem adaptador, entidade com dono sob a porta, método de coleção com filtro —
passaram contra tudo que já estava entregue. O risco que o plano destacou não se materializou.
