# Resumo de Código — U2 Lançamentos

**CI verde** no run `30715674722`, commit `ed55e3c`. **82 testes.**

---

## 1. Arquivos

### Criados — 12

| Arquivo | Conteúdo |
|---|---|
| `categoria/dominio/Categoria.kt` | Entidade, porta `CategoriaRepositorio`, conjunto inicial, normalização |
| `categoria/aplicacao/CategoriaService.kt` | Serviço + `CriadorDeCategoriasIniciais` |
| `categoria/adaptador/persistencia/CategoriaPersistencia.kt` | JPA, Spring Data, adaptador, mapeadores |
| `categoria/adaptador/web/CategoriaController.kt` | 4 rotas e DTOs |
| `gasto/dominio/Gasto.kt` | Entidade, `FiltroGasto`, `Paginacao`, porta e projeções |
| `gasto/aplicacao/GastoService.kt` | Serviço e `LancarGasto` |
| `gasto/adaptador/persistencia/GastoPersistencia.kt` | JPA, projeção, agregação, predicado |
| `gasto/adaptador/web/GastoController.kt` | 5 rotas e DTOs |
| `db/migration/V2__lancamentos.sql` | 2 tabelas, 2 índices únicos parciais, 3 `CHECK`, 5 índices |
| `ArquiteturaTest.kt` | D-66 — 4 regras + guarda contra vacuidade |
| `IsolamentoDeGastosTest.kt` | 5 casos — o mais importante da unidade |
| `CategoriaIntegracaoTest.kt` · `GastoIntegracaoTest.kt` · `TotaisPropriedadesTest.kt` | 12 + 13 + 7 |

### Modificados — 5

| Arquivo | Mudança | Por quê |
|---|---|---|
| `build.gradle.kts` | ArchUnit 1.3.0 em teste | D-66 |
| `common/web/Erros.kt` | 9 códigos novos | As 23 regras precisam deles |
| `common/web/ErroHandler.kt` | `IllegalArgumentException` → 400 | **Desvio**: sem ele, `?tamanho=1000000` virava 500 |
| `common/dominio/Dinheiro.kt` | `ehPositivo()` | RN-L01, e negação dupla ninguém lê certo |
| `SuporteDeIntegracao.kt` · `ConcorrenciaTest.kt` | Helpers e `TRUNCATE` das tabelas novas | Testes |

---

## 2. As três garantias estruturais

### D-63 — a porta cresce sem abrir buraco

`FiltroGasto` tem `de` e `ate` **obrigatórios**. Não existe forma de pedir "todos os gastos" porque
o tipo não permite construir a pergunta. O critério de visibilidade nunca é parâmetro: o adaptador o
obtém de `ContextoUsuario.criterio()` e o aplica sempre.

### D-65 — leitura por projeção

Uma consulta com `JOIN` monta a linha direto, sem materializar `GastoJpa`. Com `open-in-view: false`,
o N+1 não é evitado por configuração — não tem por onde acontecer.

Duas classes de linha (`LinhaGasto`, `LinhaTotalCategoria`) existem porque `Dinheiro` é value class
de construtor privado, e a expressão `new` do JPQL só chama construtor público. A conversão fica na
borda, num lugar só.

### D-66 — o CI na frente da entidade que escapa

Quatro regras: domínio sem JPA nem Spring; controller sem adaptador; entidade com `dono` sob a
porta; método de porta que devolve coleção recebe filtro.

**Com guarda contra vacuidade**: se a detecção parar de encontrar `Categoria` e `Gasto`, o teste
falha. Um teste de arquitetura que não encontra nada passa — e passa em silêncio, para sempre.

**Rodado contra U1** (Passo 20): nenhuma reprovação. A estrutura entregue estava limpa.

---

## 3. O que o CI encontrou

Primeira execução: **2 falhas em 82**.

### A transação morta — defeito de código

No ramo de corrida de `listar()`, eu capturava a violação de unicidade e **relia na mesma
transação**. No PostgreSQL, uma violação aborta a transação inteira (`SQLSTATE 25P02`).

Corrigido com bean próprio e transação própria para a criação do conjunto inicial. Método do próprio
serviço não resolveria: auto-invocação não passa pelo proxy do Spring.

> **A assimetria com U1 é o que importa.** O mesmo `catch (Duplicada)` existe em `UsuarioService` e
> em `GrupoService` desde U1 e funciona — porque ali ele **lança** em seguida, e o rollback vem
> junto. O padrão só quebra quando se tenta *continuar*. Copiar um padrão que funciona não é o mesmo
> que copiar as condições em que ele funciona.

### O teste que contradizia a própria regra

Afirmei `totalPessoal = 0.00` para um gasto de escopo GRUPO cuja dona era a consultante. RN-T02,
escrita três arquivos antes, diz o contrário: ele entra nos dois totais. O código estava certo.

---

## 4. Cobertura das histórias

| História | Onde |
|---|---|
| H-09 escopo | `GastoService.validar` · `GastoIntegracaoTest` |
| H-13 editar como membro | `Gasto.editado` sem parâmetro de dono · `GastoIntegracaoTest` |
| H-14 registrar dono | `ItemGastoDTO.donoNome` · `IsolamentoDeGastosTest` |
| H-15 gerenciar gastos | `GastoController` · `GastoIntegracaoTest` |
| H-16 consultar | `consultar` paginado · `GastoIntegracaoTest` |
| H-17 os dois totais | `totalizar` · `GastoIntegracaoTest` (cenário Gherkin conferido) · `TotaisPropriedadesTest` |
| H-33 categorias | `CategoriaController` · `CategoriaIntegracaoTest` |
| H-34 categoria em uso | `excluir` com realocação · `CategoriaIntegracaoTest` |
| H-35 iniciais | `CriadorDeCategoriasIniciais` · `CategoriaIntegracaoTest` |

---

## 5. Verificação final (Passo 23)

| Item | Resultado |
|---|---|
| `Double`/`Float` em caminho monetário | Nenhum. `Dinheiro` e `BigDecimal` apenas |
| Divisão monetária em SQL | Nenhuma. Só `SUM` — *o banco pode somar, dividir nunca* |
| Consulta de domínio sem filtro | Nenhuma. Garantido por tipo (D-63) e pelo `ArquiteturaTest` |
| `save` sem flush onde a garantia é do banco | Nenhum. `saveAndFlush`/`saveAllAndFlush` |
| As 23 regras com teste | Sim — ver §4 e a rastreabilidade do plano |

---

## 6. O que atravessa para U3

| Deixado | Consumido por |
|---|---|
| `Gasto` com `cartao` e `competencia` nuláveis | Integração com cartão — **sem `ALTER TABLE`** |
| `Categoria` com escopo | `Compra`, `ContaAPagar`, orçamento de U4 |
| Predicado de visibilidade **implementado e testado** | As 6 entidades de U3 |
| `ArquiteturaTest` | Reprova automaticamente qualquer das 6 que escapar |
| A regra *o banco pode somar; dividir, nunca* | O parcelamento, onde `dividirEm` ganha consumidor |
