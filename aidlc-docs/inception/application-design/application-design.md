# Application Design — Consolidado

**Stage**: INCEPTION - Application Design
**Timestamp**: 2026-07-30T16:11:59Z
**Base**: `requirements.md` revisão 8 · `stories.md` · `execution-plan.md`

Documento de consolidação. O detalhamento está em:
`components.md` · `component-methods.md` · `services.md` · `component-dependency.md` · `openapi.yaml`

---

## 1. Decisões desta stage

| ID | Decisão | Justificativa |
|---|---|---|
| **D-03** | Estrutura de pacotes **por feature** | Mantém junto o que muda junto; o corte espelha as unidades de trabalho do plano |
| **D-29** | **Mesmo modelo** para escrita e leitura | Adequado ao volume de RNF-12; otimização pontual se alguma consulta ficar lenta |
| **D-30** | Cada item traz o **dono**; a resposta traz **ambos os totais** | Uma chamada monta a tela do mês; a lista mistura pessoais e de grupo naturalmente |
| **D-31** | Fatura é **entidade persistida** | Carrega estado que não deriva dos lançamentos: momento do fechamento e pagamento. Necessária para o bloqueio de RF-95 |
| **D-32** | Identificadores **UUID** | Não revela volume nem permite enumeração — relevante com a extensão Security desligada |

---

## 2. Arquitetura

**Monolito Spring Boot single-module**, organizado por feature.

```
com.rafaelmatheus.financialcontrol
|
+-- common/          Dinheiro, Escopo, Competencia,
|                    ContextoUsuario, Visibilidade, ErroHandler
|
+-- usuario/  grupo/                     U1  Fundacao
+-- categoria/  gasto/                   U2  Lancamentos
+-- cartao/  compra/  fatura/  conta/    U3  Credito
+-- receita/  orcamento/  investimento/  U4  Planejamento
```

Cada pacote de feature contém entidade, repositório, serviço, controller e DTOs.

**11 componentes de feature + 1 compartilhado · 15 entidades · 12 agregados**

### Camadas dentro de cada feature

```
Controller   REST, validacao de entrada (Bean Validation)
    |
Service      fronteira transacional, orquestracao, mapeamento para DTO
    |        aplica Visibilidade antes de qualquer leitura
Repository   Spring Data JPA
    |
Entidade     estado + logica pura (competenciaDe, dividirEm)
```

> **Regra de altitude**: cálculo puro vive na entidade ou em objeto de domínio dedicado. O serviço
> coordena — carrega, invoca, persiste. Isso mantém a lógica sensível testável sem banco, o que
> importa para os alvos de property-based testing.

---

## 3. Como as três questões estruturais foram resolvidas

Esta stage existia principalmente para fechar três pontos herdados das jornadas transversais.

### 3.1 D-03 — Estrutura de pacotes ✅

Resolvida: **por feature**. Alternativas consideradas e descartadas — camada técnica (espalha uma
feature por 4 pacotes) e hexagonal (cerimônia desproporcional ao tamanho do projeto).

### 3.2 J-01 — Rateio por parcela ou por compra ⚪ **EXTINTA**

Não foi resolvida — **deixou de existir**. A remoção do rateio na revisão 8 (D-27) eliminou o
conceito de cota, e com ele a pergunta sobre onde ancorá-la. A entidade `Cota` sai do modelo.

### 3.3 J-03 — "Total do grupo" vs. "total pessoal" ✅

Resolvida em duas camadas:

- **Requisito** — RF-97 e D-28 estabelecem que são **duas grandezas distintas, nunca somadas**
- **API** — D-30: cada item traz seu `dono`, e a resposta traz `totalPessoal` e `totalGrupo`

```json
GET /gastos?de=2026-08-01&ate=2026-08-31

{
  "itens": [
    { "escopo": "PESSOAL", "descricao": "Cafe",    "valor": "12.00",  "dono": {"nome": "Rafael"} },
    { "escopo": "GRUPO",   "descricao": "Mercado", "valor": "400.00", "dono": {"nome": "Ana"} },
    { "escopo": "GRUPO",   "descricao": "Energia", "valor": "180.00", "dono": {"nome": "Rafael"} }
  ],
  "totalPessoal": "192.00",
  "totalGrupo":   "580.00"
}
```

> `totalPessoal` = R$ 12 + R$ 180 (só o que é do Rafael). `totalGrupo` = R$ 400 + R$ 180 (tudo de
> escopo GRUPO). **Somar os dois daria um número sem significado.**

---

## 4. Dois pontos estruturais do design

### 4.1 Isolamento é estrutural, não disciplinar

`Visibilidade.aplicar()` é obrigatório em toda consulta, e **nenhum repositório expõe método que
retorne dados sem ele**.

```kotlin
// predicado unico, em common
fun <T> aplicar(spec: Specification<T>): Specification<T> =
    spec.and(dono eq usuarioAtual or (escopo eq GRUPO and grupo isIn gruposDoUsuario))
```

> A alternativa — cada serviço lembrar de filtrar — funciona até alguém esquecer. Com dados
> financeiros de múltiplos usuários e a extensão Security desligada, o vazamento seria silencioso:
> a consulta retornaria dados a mais sem erro nenhum. Tornar o filtro estrutural remove a
> possibilidade do erro, em vez de depender de revisão.

### 4.2 O ciclo `fatura` ↔ `conta`, e como foi quebrado

Único ciclo do grafo de dependências, decorrente da unificação de D-14 (fatura vira conta a pagar).

Quebrado por uma interface declarada em `common`:

```kotlin
interface ProtecaoFatura {
    fun verificarAlteracaoPermitida(competencia: Competencia, cartaoId: UUID)
}
```

`conta` guarda apenas `faturaId: UUID?` e consulta pela interface. **A dependência real fica
unidirecional**: `fatura → conta`.

Alternativa descartada: fundir `Fatura` e `ContaAPagar`. A fatura tem ciclo de vida próprio
(ABERTA → FECHADA, recálculo, projeção de futuras) que uma conta comum não tem, e fundir faria
`ContaAPagar` carregar campos irrelevantes para PIX e boleto.

---

## 5. Contrato de API — entregável para o front

**`openapi.yaml`** — OpenAPI 3.1, validado.

| | |
|---|---|
| Paths | 31 |
| Operações | 51 |
| Schemas | 39 |
| Referências quebradas | nenhuma |

Atende RF-78 a RF-80 e é suficiente para gerar cliente TypeScript por ferramenta.

### Convenções do contrato

| Convenção | Motivo |
|---|---|
| Valores monetários como **string decimal** (`"1200.00"`) | `number` em JSON é ponto flutuante e perderia exatidão (RNF-01) |
| Competência como `YYYY-MM` | Identifica fatura e período de orçamento |
| Erro com `codigo` + `mensagem` **acionável** | RNF-09. A mensagem diz o que fazer, não só o que falhou |
| `409` com `FATURA_PAGA_NAO_ALTERAVEL` | RF-95. Orienta a desmarcar o pagamento antes de corrigir |

> **Aviso ao consumidor do contrato**: o `securityScheme` está como `bearerAuth` provisoriamente. O
> mecanismo real (JWT, sessão ou OAuth2/OIDC) é a decisão **D-02**, que será fechada na NFR
> Requirements. **É a única parte do contrato com mudança prevista.** O restante — recursos,
> schemas, códigos de erro — está estável e pode ser usado para começar o front.

---

## 6. Alvos de property-based testing 🔬

Após a remoção do rateio, restam dois alvos — ambos na mesma função pura:

| Função | Invariante | História |
|---|---|---|
| `Dinheiro.dividirEm(n)` | `resultado.sum() == valorOriginal`, para qualquer valor e `n ≥ 1` | H-28 |
| `DivisorDeParcelas.dividir` | A igualdade se mantém após qualquer sequência de criação e edição | H-29 |

**O parcelamento passou a ser a única área do sistema com aritmética monetária de divisão.**
Por isso as duas funções são puras e vivem fora do serviço — precisam ser exercitáveis sem banco.

---

## 7. Ordem de implementação

Derivada do grafo de dependências:

| # | Componentes | Unidade |
|---|---|---|
| 1 | `common` | U1 |
| 2 | `usuario`, `grupo` | U1 |
| 3 | `categoria` | U2 |
| 4 | `cartao` | U3 |
| 5 | `fatura` + `conta` (**juntos** — dependência mútua de comportamento) | U3 |
| 6 | `gasto`, `compra` | U2, U3 |
| 7 | `receita`, `orcamento`, `investimento` | U4 |

### ⚠️ Divergência com o plano de execução

O plano previa **U2 (Lançamentos) antes de U3 (Crédito)**. O grafo mostra que `gasto` depende de
`cartao` e `fatura` — a parte de gasto vinculada a cartão só funciona depois de U3.

**Sugestão para a Units Generation**: dividir `gasto` em duas etapas — gasto à vista em U2, gasto em
cartão em U3 — ou mover `cartao` e `fatura` para antes de U2. É decisão daquela stage; fica
registrado como achado desta.

---

## 8. Cobertura

| Aspecto | Situação |
|---|---|
| Requisitos de domínio | 68/68 mapeados a componentes |
| Histórias | 57 histórias + 3 jornadas cobertas |
| Agregados | 12, todos com componente, serviço e endpoints |
| Invariantes monetárias | 2, ambas com dono definido (`Dinheiro`, `DivisorDeParcelas`) |
| Isolamento (RNF-05) | Estrutural via `Visibilidade` |
| Formato de erro (RNF-09) | 11 códigos definidos, centralizados em `ErroHandler` |

---

## 9. Pontos deixados em aberto

| # | Questão | Stage-alvo | ID |
|---|---|---|---|
| 1 | Fechamento em dia 29–31 com mês curto | Functional Design | D-04 |
| 2 | Base de cálculo do "realizado" do orçamento — data da compra ou competência da fatura | Functional Design | J-02 |
| 3 | Mecanismo de geração das ocorrências recorrentes | Functional Design | D-19 |
| 4 | Mecanismo de fechamento automático da fatura | Functional Design | D-20 |
| 5 | `Fatura.status = PAGA` é persistido ou derivado da `ContaAPagar` vinculada? | Functional Design | 🆕 D-33 |
| 6 | Mecanismo de autenticação | NFR Requirements | D-02 |

**Item 5 é novo desta stage.** Surgiu ao modelar a fatura como entidade persistida (D-31): se tanto
`Fatura` quanto `ContaAPagar` carregam estado de pagamento, existem duas fontes de verdade que
podem divergir. A recomendação registrada é **derivar** — a `ContaAPagar` carrega o pagamento e
`Fatura.status` o projeta —, mas a decisão fica para a Functional Design.
