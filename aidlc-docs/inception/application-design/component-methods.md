# Métodos dos Componentes

**Stage**: INCEPTION - Application Design
**Timestamp**: 2026-07-30T16:11:59Z

> **Nível de detalhe**: assinaturas, propósito e tipos de entrada/saída. **As regras de negócio
> detalhadas — validações, cálculos, fronteiras — são definidas na Functional Design**, por unidade.
>
> Notação Kotlin. `UUID` para identificadores (D-32), `Dinheiro` para valores monetários (RNF-01),
> `Competencia` para ano-mês.

---

## 1. `usuario`

```kotlin
interface UsuarioService {
    fun cadastrar(cmd: CadastrarUsuario): UsuarioDTO
    fun consultarPerfil(): UsuarioDTO
    fun atualizarPerfil(cmd: AtualizarPerfil): UsuarioDTO
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `cadastrar` | Cria usuário com e-mail único; rejeita duplicado e formato inválido | RF-01 |
| `consultarPerfil` | Retorna o perfil do usuário autenticado | RF-05 |
| `atualizarPerfil` | Atualiza dados editáveis do próprio perfil | RF-05 |

> A autenticação em si (`login`, emissão de credencial) depende do mecanismo ainda não escolhido
> (D-02) e será especificada na NFR Requirements.

---

## 2. `grupo`

```kotlin
interface GrupoService {
    fun criar(cmd: CriarGrupo): GrupoDTO
    fun renomear(id: UUID, nome: String): GrupoDTO
    fun listarMeusGrupos(): List<GrupoDTO>
    fun consultar(id: UUID): GrupoDetalheDTO

    fun adicionarMembro(grupoId: UUID, usuarioId: UUID): GrupoDetalheDTO
    fun removerMembro(grupoId: UUID, usuarioId: UUID)
    fun sair(grupoId: UUID)
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `criar` | Cria grupo com nome identificador | RF-06 |
| `renomear` | Renomeia grupo de que sou membro | RF-06 |
| `listarMeusGrupos` | Lista grupos do usuário; vazio é resultado válido | RF-07 |
| `consultar` | Retorna o grupo com seus membros | RF-08 |
| `adicionarMembro` | Adiciona usuário existente; rejeita inexistente (E-07) | RF-08 |
| `removerMembro` | Remove membro; **qualquer membro pode** — sem hierarquia | RF-08 |
| `sair` | Sai do grupo preservando o histórico | RF-10 |

---

## 3. `categoria`

```kotlin
interface CategoriaService {
    fun criar(cmd: CriarCategoria): CategoriaDTO
    fun renomear(id: UUID, nome: String): CategoriaDTO
    fun excluir(id: UUID, realocarPara: UUID? = null)
    fun listar(): List<CategoriaDTO>
    fun criarPadroes()
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `criar` | Cria categoria com nome único por usuário | RF-36 |
| `renomear` | Renomeia categoria | RF-36 |
| `excluir` | Exclui; bloqueia se houver vínculos, salvo se `realocarPara` for informado | RF-37, E-06 |
| `listar` | Lista as categorias do usuário | RF-36 |
| `criarPadroes` | Cria o conjunto inicial no primeiro acesso | RF-38 |

---

## 4. `gasto`

```kotlin
interface GastoService {
    fun lancar(cmd: LancarGasto): GastoDTO
    fun editar(id: UUID, cmd: EditarGasto): GastoDTO
    fun excluir(id: UUID)
    fun consultar(filtro: FiltroGasto): PaginaGastos
}

data class FiltroGasto(
    val de: LocalDate,
    val ate: LocalDate,
    val categoriaId: UUID? = null,
    val grupoId: UUID? = null,
    val escopo: Escopo? = null,
    val donoId: UUID? = null,
)

data class PaginaGastos(
    val itens: List<GastoDTO>,
    val totalPessoal: Dinheiro,
    val totalGrupo: Dinheiro,
    val totaisPorCategoria: List<TotalCategoria>,
)
```

| Método | Propósito | Requisito |
|---|---|---|
| `lancar` | Registra gasto com escopo e dono; associa forma de pagamento | RF-18, RF-19, RF-11 |
| `editar` | Edita; qualquer membro pode, em escopo GRUPO; o dono não muda | RF-20, RF-16, RF-17 |
| `excluir` | Exclui respeitando permissão e bloqueio de fatura paga | RF-20, RF-95 |
| `consultar` | Consulta por período com filtros; retorna **os dois totais** | RF-21, RF-22, RF-97 |

> **`PaginaGastos` carrega as duas grandezas separadas** (D-28, D-30). `totalPessoal` soma apenas
> itens em que o consultante é dono; `totalGrupo` soma todos os de escopo GRUPO. **Nunca somar os
> dois.**

---

## 5. `cartao`

```kotlin
interface CartaoService {
    fun cadastrar(cmd: CadastrarCartao): CartaoDTO
    fun editar(id: UUID, cmd: EditarCartao): CartaoDTO
    fun excluir(id: UUID)
    fun listar(): List<CartaoDTO>
}

// metodo de dominio, na entidade — testavel isoladamente
class Cartao {
    fun competenciaDe(dataCompra: LocalDate): Competencia
    fun vencimentoDe(competencia: Competencia): LocalDate
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `cadastrar` | Cria cartão com apelido, fechamento e vencimento; escopo pessoal ou de grupo | RF-23, RF-24 |
| `editar` / `excluir` / `listar` | CRUD | RF-23 |
| `competenciaDe` | **Determina em que fatura a compra cai.** Corte **exclusivo**: compra no dia do fechamento vai para a seguinte | RF-25, RF-61, E-03 |
| `vencimentoDe` | Data de vencimento de uma competência | RF-23 |

> `competenciaDe` é **método da entidade**, não do serviço — é lógica pura, sem I/O, e precisa ser
> testável isoladamente. O caso de fechamento em dia 29–31 (E-04) fica para a Functional Design.

---

## 6. `compra`

```kotlin
interface CompraService {
    fun lancar(cmd: LancarCompraParcelada): CompraDTO
    fun editar(id: UUID, cmd: EditarCompra): CompraDTO
    fun excluir(id: UUID)
    fun consultar(id: UUID): CompraDetalheDTO
}

data class LancarCompraParcelada(
    val descricao: String,
    val valorParcela: Dinheiro,
    val numeroParcelas: Int,
    val dataCompra: LocalDate,
    val cartaoId: UUID,
    val categoriaId: UUID,
    val escopo: Escopo,
    val grupoId: UUID?,
)

// logica pura, testavel isoladamente
object DivisorDeParcelas {
    fun dividir(valorTotal: Dinheiro, numeroParcelas: Int): List<Dinheiro>
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `lancar` | Calcula o total, gera as N parcelas com suas competências | RF-29, RF-30 |
| `editar` | Edita **por inteiro**: descarta e regenera as parcelas | RF-33 |
| `excluir` | Remove a compra e todas as parcelas | RF-34 |
| `consultar` | Retorna a compra com suas parcelas e posições ("3/12") | RF-35 |
| `dividir` | Divide o total em N parcelas; **última absorve o resíduo** | RF-31, RF-32 |

> 🔬 **`DivisorDeParcelas.dividir` é o alvo principal de property-based testing** (H-28, H-29,
> regra PBT-03). Função pura, sem estado. Invariante: para qualquer `valorTotal` positivo e
> qualquer `n ≥ 1`, `dividir(v, n).sum() == v` e nenhum elemento é negativo.

---

## 7. `fatura`

```kotlin
interface FaturaService {
    fun consultar(cartaoId: UUID, competencia: Competencia): FaturaDTO
    fun consultarFuturas(cartaoId: UUID, ate: Competencia): List<FaturaDTO>
    fun fechar(cartaoId: UUID, competencia: Competencia): FaturaDTO
    fun recalcular(faturaId: UUID): FaturaDTO
}
```

| Método | Propósito | Requisito |
|---|---|---|
| `consultar` | Consolida lançamentos e parcelas da competência, com total e vencimento | RF-26 |
| `consultarFuturas` | Projeta faturas futuras com as parcelas comprometidas | RF-28 |
| `fechar` | Fecha a fatura e **gera a conta a pagar** correspondente | RF-59 |
| `recalcular` | Recalcula fatura ABERTA ou reabre FECHADA não paga | RF-60, RF-96 |

**Regra transversal do componente** — bloqueio de fatura paga (RF-95):

```kotlin
// invocado por gasto, compra e conta antes de qualquer alteracao
interface ProtecaoFatura {
    fun verificarAlteracaoPermitida(competencia: Competencia, cartaoId: UUID)
}
```

Lança erro se a fatura correspondente estiver **PAGA**, com mensagem orientando a desmarcar o
pagamento antes (RF-94).

> O pagamento em si **não** é operação deste componente — vive em `conta`, onde a fatura se
> materializa como `ContaAPagar`. Ver o ponto em aberto registrado em `components.md` §2.7.

---

## 8. `conta`

```kotlin
interface ContaService {
    fun cadastrar(cmd: CadastrarConta): ContaDTO
    fun editar(id: UUID, cmd: EditarConta): ContaDTO
    fun excluir(id: UUID)

    fun marcarPaga(id: UUID, dataPagamento: LocalDate, valorAjustado: Dinheiro?): ContaDTO
    fun desmarcarPagamento(id: UUID): ContaDTO

    fun vencimentosDoPeriodo(de: LocalDate, ate: LocalDate): PaginaVencimentos
    fun aVencer(dias: Int): List<ContaDTO>
    fun vencidas(): List<ContaDTO>
}

interface ContaRecorrenteService {
    fun cadastrar(cmd: CadastrarContaRecorrente): ContaRecorrenteDTO
    fun editar(id: UUID, cmd: EditarContaRecorrente): ContaRecorrenteDTO
    fun encerrar(id: UUID)
    fun listar(): List<ContaRecorrenteDTO>
}

data class PaginaVencimentos(
    val itens: List<ContaDTO>,   // ordenados por vencimento
    val totalPessoal: Dinheiro,
    val totalGrupo: Dinheiro,
)
```

| Método | Propósito | Requisito |
|---|---|---|
| `cadastrar` | Cria conta com vencimento próprio, tipo e escopo | RF-55, RF-56, RF-65 |
| `editar` | Edita; bloqueia se PAGA (RF-95) e se for do tipo FATURA_CARTAO (P-11) | RF-95 |
| `marcarPaga` | Quita a conta; `valorAjustado` cobre contas variáveis como energia | RF-57, RF-64 |
| `desmarcarPagamento` | **Reverte para EM_ABERTO** — única via de corrigir conta paga | RF-94 |
| `vencimentosDoPeriodo` | **Visão consolidada**: todos os tipos, ordenados por data | RF-58 |
| `aVencer` / `vencidas` | Horizonte configurável e contas vencidas não pagas | RF-66 |
| `cadastrar` (recorrente) | Cria recorrência com dia de vencimento e frequência | RF-62, RF-63 |
| `encerrar` | Interrompe a geração, preservando as ocorrências existentes | RF-67 |

> **Ponto em aberto (D-19)**: o mecanismo de geração das ocorrências. A assinatura acima não decide
> — se for derivação na consulta, `vencimentosDoPeriodo` materializa as ocorrências faltantes; se
> for job, elas já existem. A Functional Design resolve.

---

## 9. `receita`

```kotlin
interface ReceitaService {
    fun lancar(cmd: LancarReceita): ReceitaDTO
    fun editar(id: UUID, cmd: EditarReceita): ReceitaDTO
    fun excluir(id: UUID)
    fun consultar(de: LocalDate, ate: LocalDate): PaginaReceitas
    fun balanco(competencia: Competencia): BalancoDTO
}

data class BalancoDTO(
    val competencia: Competencia,
    val receitas: Dinheiro,
    val gastos: Dinheiro,      // inclui aportes (RF-76)
    val resultado: Dinheiro,
)
```

| Método | Propósito | Requisito |
|---|---|---|
| `lancar` / `editar` / `excluir` | CRUD de receitas individuais | RF-39 |
| `consultar` | Consulta por período com total | RF-40 |
| `balanco` | Receitas − gastos; **aportes contam como gasto** | RF-41, RF-76 |

---

## 10. `orcamento`

```kotlin
interface OrcamentoService {
    fun definir(cmd: DefinirOrcamento): OrcamentoDTO
    fun remover(id: UUID)
    fun acompanhar(competencia: Competencia): List<AcompanhamentoDTO>
}

data class AcompanhamentoDTO(
    val categoria: CategoriaDTO,
    val orcado: Dinheiro,
    val realizado: Dinheiro,
    val saldo: Dinheiro,       // negativo quando estourado
    val estourado: Boolean,
)
```

| Método | Propósito | Requisito |
|---|---|---|
| `definir` | Define ou altera o teto mensal de uma categoria | RF-42 |
| `remover` | Remove o teto | RF-42 |
| `acompanhar` | Orçado × realizado, com sinalização de estouro | RF-43, RF-44 |

> **Ponto em aberto (J-02)**: o `realizado` conta o gasto de cartão pela **data da compra** ou pela
> **competência da fatura**? A assinatura não decide. Functional Design.

---

## 11. `investimento`

```kotlin
interface InvestimentoService {
    fun criarObjetivo(cmd: CriarObjetivo): ObjetivoDTO
    fun editarObjetivo(id: UUID, cmd: EditarObjetivo): ObjetivoDTO
    fun excluirObjetivo(id: UUID)

    fun aportar(objetivoId: UUID, cmd: RegistrarAporte): ObjetivoDTO
    fun atualizarSaldo(objetivoId: UUID, saldoAtual: Dinheiro): ObjetivoDTO

    fun consultar(objetivoId: UUID): ObjetivoDetalheDTO
    fun posicaoConsolidada(): PosicaoDTO
}

data class ObjetivoDetalheDTO(
    val id: UUID,
    val nome: String,
    val meta: Dinheiro?,            // opcional (RF-73)
    val prazoAlvo: YearMonth?,      // opcional (RF-74)
    val totalAportado: Dinheiro,
    val saldoAtual: Dinheiro,
    val rendimento: Dinheiro,       // saldo - aportado; pode ser negativo (E-14)
    val progressoPercentual: BigDecimal?,   // null sem meta
    val aporteMensalNecessario: Dinheiro?,  // null sem meta ou prazo
    val atrasado: Boolean,          // prazo vencido sem meta atingida (E-15)
    val aportes: List<AporteDTO>,   // cada um com seu dono
)
```

| Método | Propósito | Requisito |
|---|---|---|
| `criarObjetivo` | Cria objetivo com meta e prazo **opcionais** | RF-68, RF-73, RF-74 |
| `aportar` | Registra aporte e acumula o total | RF-69, RF-70 |
| `atualizarSaldo` | Atualiza o saldo à mão, refletindo rendimento | RF-71 |
| `consultar` | Retorna objetivo com rendimento, progresso e aporte necessário | RF-72 a RF-74 |
| `posicaoConsolidada` | Total aportado, saldo e rendimento agregados | RF-77 |

> Campos opcionais são `null` quando não se aplicam — objetivo sem meta não tem progresso, e a
> ausência **não é erro** (RF-73). Rendimento negativo é exibido normalmente (E-14).
>
> O aporte entra no `BalancoDTO.gastos` de `receita` (RF-76) — ver dependência em
> `component-dependency.md`.

---

## 12. `common`

```kotlin
enum class Escopo { PESSOAL, GRUPO }

@JvmInline value class Dinheiro(val valor: BigDecimal) {
    operator fun plus(outro: Dinheiro): Dinheiro
    operator fun minus(outro: Dinheiro): Dinheiro
    fun dividirEm(n: Int): List<Dinheiro>   // ultima parcela absorve o residuo
    companion object { fun de(valor: String): Dinheiro }
}

interface ContextoUsuario {
    fun usuarioAtual(): UUID
    fun gruposDoUsuario(): Set<UUID>
}

interface Visibilidade {
    fun <T> aplicar(spec: Specification<T>): Specification<T>
}
```

| Componente | Propósito | Requisito |
|---|---|---|
| `Escopo` | Enum PESSOAL / GRUPO | RF-11 |
| `Dinheiro` | Aritmética decimal exata, escala 2, arredondamento explícito | RNF-01 |
| `Dinheiro.dividirEm` | Divisão com resíduo na última — 🔬 **alvo de PBT** | RF-31, RF-32 |
| `ContextoUsuario` | Fonte única do usuário autenticado e seus grupos | RF-03 |
| `Visibilidade.aplicar` | **Predicado obrigatório em toda consulta** | RF-03, RF-04, RNF-05 |

> **`Visibilidade.aplicar` é estrutural, não opcional.** Nenhum repositório expõe método que
> retorne dados sem ele. Esquecer o filtro deixa de ser possível por construção — não depende de
> disciplina de quem escreve a consulta.

---

## 13. Resumo dos alvos de property-based testing 🔬

Após a remoção do rateio (revisão 8), restam dois alvos, ambos na mesma função pura:

| Função | Invariante | História | Regra PBT |
|---|---|---|---|
| `Dinheiro.dividirEm(n)` | `resultado.sum() == valorOriginal` para qualquer valor e `n ≥ 1` | H-28 | PBT-03 |
| `DivisorDeParcelas.dividir` | A igualdade se mantém após qualquer sequência de criação e edição | H-29 | PBT-03 |
| `Dinheiro` (serialização) | Round-trip JSON preserva escala e valor | — | PBT-02 |

Framework: **Kotest Property Testing** (D-05, regra PBT-09) — a confirmar na NFR Requirements.
