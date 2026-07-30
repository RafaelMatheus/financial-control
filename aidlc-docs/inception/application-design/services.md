# Camada de Serviço

**Stage**: INCEPTION - Application Design
**Timestamp**: 2026-07-30T16:11:59Z

---

## 1. Padrão adotado

**Serviço por feature**, coerente com a organização de pacotes (D-03). Cada serviço:

- É a **fronteira transacional** — `@Transactional` vive aqui, não no controller nem no repositório
- Orquestra entidades, repositórios e outros serviços
- Aplica `Visibilidade` antes de qualquer leitura
- Mapeia entidades para DTO — **mesmo modelo para escrita e leitura** (D-29)
- **Não** contém regra de negócio que pertença à entidade

> **Regra de altitude**: cálculo puro fica na entidade ou em objeto de domínio dedicado
> (`Cartao.competenciaDe`, `Dinheiro.dividirEm`). O serviço coordena — carrega, invoca, persiste.
> Isso mantém a lógica sensível testável sem banco, o que importa para os alvos de PBT.

---

## 2. Serviços

| Serviço | Responsabilidade | Orquestra |
|---|---|---|
| `UsuarioService` | Cadastro e perfil | — |
| `GrupoService` | Grupos e composição de membros | — |
| `CategoriaService` | Classificação; proteção de categoria em uso | `GastoRepository` (verificação de vínculo) |
| `GastoService` | Lançamentos avulsos e consulta com os dois totais | `CartaoService`, `FaturaService`, `Visibilidade` |
| `CartaoService` | CRUD de cartões | — |
| `CompraService` | Compras parceladas e geração das parcelas | `CartaoService`, `FaturaService` |
| `FaturaService` | Consolidação, fechamento e recálculo | `GastoRepository`, `ParcelaRepository`, `ContaService` |
| `ContaService` | Contas a pagar, pagamento e visão de vencimentos | `FaturaService` (contas derivadas) |
| `ContaRecorrenteService` | Recorrência e geração de ocorrências | `ContaService` |
| `ReceitaService` | Receitas e balanço do período | `GastoRepository`, `InvestimentoRepository` |
| `OrcamentoService` | Teto e acompanhamento | `GastoRepository`, `CategoriaService` |
| `InvestimentoService` | Objetivos e aportes | — |

---

## 3. Orquestrações relevantes

### 3.1 Lançar uma compra parcelada

Envolve três componentes e precisa ser **atômica** (RNF-02).

```
CompraService.lancar(cmd)
  |
  1. ProtecaoFatura.verificarAlteracaoPermitida(...)   -> bloqueia se fatura PAGA (RF-95)
  2. Cartao.competenciaDe(dataCompra)                  -> competencia da 1a parcela (RF-25)
  3. DivisorDeParcelas.dividir(total, n)               -> valores, ultima absorve residuo (RF-31)
  4. cria Compra + N Parcelas                          -> invariante soma == total (RF-32)
  5. FaturaService.recalcular(por competencia afetada) -> atualiza faturas ABERTAS (RF-60)
  |
  +-- tudo numa unica transacao
```

**Ponto crítico**: os passos 4 e 5 precisam estar na mesma transação. Uma compra gravada sem
recalcular a fatura deixaria o total da fatura divergente dos lançamentos.

---

### 3.2 Fechar a fatura e gerar a conta a pagar

Costura entre `fatura` e `conta` — é o que unifica a fatura de cartão à visão de vencimentos
(RF-59, D-14).

```
FaturaService.fechar(cartaoId, competencia)
  |
  1. consolida gastos de cartao + parcelas da competencia   (RF-26)
  2. Fatura.status: ABERTA -> FECHADA, valorTotal congelado
  3. Cartao.vencimentoDe(competencia)                       -> data de vencimento
  4. ContaService.criarContaDerivada(
       tipo = FATURA_CARTAO,
       valor = fatura.valorTotal,
       vencimento = ...,
       faturaId = fatura.id)                                (RF-59)
  |
  +-- transacao unica
```

> A conta derivada **não é editável diretamente** (P-11). Seu valor vem da consolidação.

---

### 3.3 Consultar gastos com os dois totais

Materializa RF-97 e a decisão D-28.

```
GastoService.consultar(filtro)
  |
  1. Visibilidade.aplicar(spec)         -> so o que o usuario enxerga (RF-03)
  2. repository.findAll(spec + filtro)
  3. mapeia para GastoDTO, cada um com seu DONO
  4. totalPessoal = soma dos itens onde dono == usuarioAtual
  5. totalGrupo   = soma dos itens com escopo GRUPO
  |
  +-- os dois totais NUNCA se somam
```

> Os passos 4 e 5 percorrem o mesmo conjunto com predicados diferentes. É a regra mais fácil de
> implementar errado do sistema — somar o valor cheio dos lançamentos de grupo no total pessoal
> produziria um número contendo dinheiro de outra pessoa.

---

### 3.4 Marcar conta como paga

Simples, mas com uma consequência transversal.

```
ContaService.marcarPaga(id, data, valorAjustado?)
  |
  1. valida que a conta esta EM_ABERTO
  2. se valorAjustado != null -> atualiza o valor (RF-64, contas variaveis)
  3. status -> PAGA, registra dataPagamento
  4. se tipo == FATURA_CARTAO -> Fatura correspondente passa a bloquear alteracoes (RF-95)
```

E a operação inversa (RF-94):

```
ContaService.desmarcarPagamento(id)
  |
  1. status -> EM_ABERTO, limpa dataPagamento
  2. se tipo == FATURA_CARTAO -> Fatura volta a aceitar alteracoes
```

> **RF-94 é o que impede o beco sem saída.** Sem ele, um lançamento errado numa fatura paga ficaria
> preso para sempre, já que RF-95 bloqueia toda alteração.

---

### 3.5 Lançamento retroativo em fatura fechada

```
GastoService.lancar(cmd) com data em competencia ja fechada
  |
  +-- fatura PAGA?
  |     SIM -> bloqueia; orienta a desmarcar o pagamento (RF-95, E-13)
  |     NAO -> FaturaService.recalcular(fatura)
  |              status: FECHADA -> ABERTA
  |              recalcula valorTotal com o novo lancamento     (RF-96, E-12)
```

---

### 3.6 Balanço do período

Cruza `receita`, `gasto` e `investimento`.

```
ReceitaService.balanco(competencia)
  |
  1. soma receitas do periodo         (do usuario; receitas nao tem escopo de grupo)
  2. soma gastos do periodo           (apenas onde o usuario e DONO)
  3. soma aportes do periodo          (RF-76 — aporte conta como gasto)
  |
  resultado = receitas - (gastos + aportes)
```

> **Semântica declarada (D-18)**: o balanço mede **fluxo de caixa**, não variação patrimonial.
> Investir R$ 2.000 reduz o resultado do mês, embora o patrimônio não diminua.
>
> O passo 2 usa **apenas os gastos de que o usuário é dono** — coerente com D-28. Somar gastos de
> grupo de outro dono inflaria o balanço com dinheiro alheio.

---

## 4. Fronteiras transacionais

| Operação | Escopo da transação | Motivo |
|---|---|---|
| Lançar compra parcelada | Compra + N Parcelas + recálculo de faturas | RNF-02 — parcelas órfãs ou fatura divergente |
| Editar compra parcelada | Exclusão + regeneração + recálculo | Estado intermediário sem parcelas seria inválido |
| Fechar fatura | Fatura + ContaAPagar derivada | Fatura fechada sem conta some da visão de vencimentos |
| Excluir compra | Parcelas + recálculo das faturas afetadas | Fatura ficaria com valor de parcela inexistente |
| Marcar conta paga | Conta + reflexo no bloqueio da fatura | Consistência do bloqueio de RF-95 |
| CRUD simples | Uma entidade | Sem efeito colateral |

---

## 5. Tratamento de erro (RNF-09)

Formato único, produzido pelo `ErroHandler` em `common`:

```json
{
  "codigo": "FATURA_PAGA_NAO_ALTERAVEL",
  "mensagem": "A fatura de agosto/2026 já foi paga. Desmarque o pagamento antes de alterar.",
  "detalhes": []
}
```

Códigos previstos por área:

| Código | Origem |
|---|---|
| `RECURSO_NAO_ENCONTRADO` | Qualquer consulta por id |
| `ACESSO_NEGADO` | `Visibilidade` — RF-04 |
| `VALIDACAO` | Bean Validation — RNF-10; `detalhes` traz campo e motivo |
| `EMAIL_JA_CADASTRADO` | `UsuarioService` — RF-01 |
| `USUARIO_INEXISTENTE` | `GrupoService.adicionarMembro` — E-07 |
| `ESCOPO_GRUPO_SEM_GRUPO` | Lançamento com escopo GRUPO sem grupo válido — E-09 |
| `CATEGORIA_EM_USO` | `CategoriaService.excluir` — E-06 |
| `FATURA_PAGA_NAO_ALTERAVEL` | `ProtecaoFatura` — RF-95, E-13 |
| `CONTA_DERIVADA_NAO_EDITAVEL` | Edição direta de conta FATURA_CARTAO — P-11 |
| `PARCELA_NAO_EDITAVEL` | Edição de parcela isolada — RF-33 |
| `SOMA_PARCELAS_INCONSISTENTE` | Violação da invariante — RF-32 |

> As mensagens são **acionáveis**: dizem o que fazer, não apenas o que deu errado. `FATURA_PAGA_NAO_ALTERAVEL`
> aponta o caminho (desmarcar o pagamento) porque, sem essa orientação, o usuário fica sem saída
> aparente.

---

## 6. O que **não** é responsabilidade dos serviços

| Item | Onde vive | Motivo |
|---|---|---|
| Cálculo de competência de fatura | `Cartao` (entidade) | Lógica pura, testável sem banco |
| Divisão em parcelas com resíduo | `Dinheiro` / `DivisorDeParcelas` | Alvo de PBT — precisa ser função pura |
| Predicado de visibilidade | `Visibilidade` (common) | Estrutural; não pode depender de cada serviço lembrar |
| Formato de erro | `ErroHandler` (common) | Consistência global |
| Validação de entrada | Bean Validation nos DTOs | RNF-10; falha antes de chegar ao serviço |
