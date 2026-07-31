# Modelo de Domínio — U1 Fundação

Três entidades e cinco tipos compartilhados. Tecnologia-agnóstico: nomes de tabela e tipos de coluna
aparecem só na §5, e o mapeamento para JPA é assunto da Code Generation.

---

## 1. `Usuario`

Raiz de agregado. Âncora de todo o isolamento de dados do sistema.

| Atributo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | UUID | sim | Gerado pela aplicação, não pelo banco (D-32) |
| `email` | texto | sim | **Único**, normalizado (D-46) |
| `senhaHash` | texto | sim | Nunca sai da camada de domínio (D-42) |
| `nome` | texto | sim | Exibição; sem unicidade |
| `criadoEm` | instante | sim | Imutável após a criação |

**Invariantes**

1. `email` normalizado é único em todo o sistema
2. `senhaHash` nunca é serializado em nenhuma resposta
3. `criadoEm` nunca muda
4. `id` nunca muda

**Campos editáveis por `atualizarPerfil`**: apenas `nome`. Trocar e-mail e trocar senha são
operações distintas, com regras próprias — ficam fora do escopo de U1 e não têm requisito que as
peça (RF-05 fala em "dados de perfil").

> **Por que `senhaHash` e não `senha`**: o nome do atributo carrega a invariante. Um campo chamado
> `senha` convida alguém, meses depois, a atribuir texto claro a ele.

---

## 2. `Grupo`

Raiz de agregado. Coleção nomeada de pessoas que compartilham visibilidade.

| Atributo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | UUID | sim | |
| `nome` | texto | sim | **Sem** unicidade global — duas casas podem se chamar "Apartamento 42" |
| `criadoEm` | instante | sim | |

**Invariantes**

1. `nome` não é vazio nem só espaços
2. Um grupo pode existir com **zero membros** (D-47)

**Sem `criadorId`.** RF-06 e H-05 são explícitos: quem cria não recebe privilégio algum sobre os
demais. Guardar o criador criaria um campo que só serviria para, mais tarde, alguém decidir usá-lo
como autoridade — reintroduzindo por acidente a hierarquia que o requisito nega.

---

## 3. `MembroGrupo`

Associação entre `Usuario` e `Grupo`, com histórico. Pertence ao agregado `Grupo`.

| Atributo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | UUID | sim | |
| `grupoId` | UUID | sim | |
| `usuarioId` | UUID | sim | |
| `entrouEm` | instante | sim | |
| `saiuEm` | instante | não | `null` = associação **ativa** |

**Invariantes**

1. No máximo **uma** associação ativa (`saiuEm == null`) por par (`grupoId`, `usuarioId`)
2. `saiuEm`, quando presente, é posterior a `entrouEm`
3. Uma associação encerrada é **imutável** — não se reabre (D-45)

**Reentrada** cria uma linha nova. A tabela é um histórico de participações, não um estado atual:

```
usuario=U grupo=G  entrouEm=2026-01-10  saiuEm=2026-04-02   <- encerrada
usuario=U grupo=G  entrouEm=2026-07-31  saiuEm=null          <- ativa
```

> A invariante 1 é o que impede duas linhas ativas simultâneas. Ela é responsabilidade do domínio
> **e** do banco: índice único parcial sobre (`grupoId`, `usuarioId`) onde `saiuEm IS NULL`. Duas
> requisições concorrentes de "adicionar membro" só são barradas pela restrição no banco.

---

## 4. Tipos compartilhados — `common`

### 4.1 `Escopo`

Enum de dois valores: `PESSOAL`, `GRUPO`.

Nenhuma entidade de U1 o usa — ele existe aqui porque `Visibilidade` precisa conhecê-lo para
escrever o predicado, e a partir de U2 todo lançamento o carrega.

### 4.2 `Dinheiro`

Value object sobre decimal exato. **Nunca `Double` nem `Float`** (RNF-01).

| Característica | Valor |
|---|---|
| Escala | **2**, sempre; normalizada na construção |
| Arredondamento | **`HALF_UP`** (D-43) |
| Sinal | negativos **permitidos** — rendimento negativo é caso real (H-55) |
| Igualdade | por valor, após normalização de escala |

**Operações**

| Operação | Contrato |
|---|---|
| `de(texto)` | Constrói a partir de representação decimal exata; rejeita entrada não numérica |
| `mais(outro)` | Soma exata |
| `menos(outro)` | Subtração exata |
| `dividirEm(n)` | Divide em `n` partes; a **última absorve o resíduo** |

**Invariante central de `dividirEm`** — 🔬 alvo de property-based testing:

```
Para todo valor V e todo n >= 1:
    soma(V.dividirEm(n)) == V         exatamente, sem tolerância
```

Segunda propriedade, complementar:

```
Para todo valor V e todo n >= 1:
    todas as partes diferem entre si em no maximo 0,01
```

A primeira sozinha permitiria uma implementação absurda (`[V, 0, 0, ..., 0]`). A segunda sozinha
permitiria perder centavos. Juntas, prendem o comportamento.

> `dividirEm` só é **usada** em U3, no parcelamento. Está aqui porque `Dinheiro` é de U1 e porque
> escrever a função com o teste de propriedade junto, agora, é mais barato do que descobrir o
> resíduo errado no meio da unidade mais complexa do sistema.

### 4.3 `Competencia`

Value object ano-mês. Identifica fatura e período de orçamento.

| Operação | Contrato |
|---|---|
| `de(ano, mes)` | Rejeita mês fora de 1–12 |
| `proxima()` / `anterior()` | Atravessa a virada de ano corretamente |
| `comparaCom(outra)` | Ordem cronológica total |

Nenhum consumidor em U1. Mesma justificativa de `dividirEm`.

### 4.4 `ContextoUsuario`

Fonte **única** do usuário autenticado e dos grupos de que ele é membro.

| Operação | Retorno |
|---|---|
| `usuarioAtual()` | UUID do autenticado; falha se não houver autenticação |
| `gruposDoUsuario()` | Conjunto de UUIDs dos grupos com associação **ativa** |

`gruposDoUsuario()` considera apenas `saiuEm == null` — é aqui que D-44 (corte total) se materializa.

### 4.5 `Visibilidade`

Traduz o contexto num predicado de consulta. **Estrutural, não opcional.**

```
visivel(registro) :=
       registro.donoId == contexto.usuarioAtual()
    OU (registro.escopo == GRUPO E registro.grupoId ∈ contexto.gruposDoUsuario())
```

> **A decisão de design que mais importa nesta unidade**: nenhum repositório expõe método que
> retorne dados sem esse predicado aplicado. Esquecer o filtro deixa de ser possível *por
> construção*. A alternativa — confiar em quem escreve cada consulta — falha silenciosamente e
> vaza dado financeiro de terceiros, que é exatamente o que RF-03 proíbe.

---

## 5. Schema inicial e migrations

Flyway (D-01, RNF-04), com `ddl-auto: validate` ativo para detectar divergência entre entidade e
schema.

**Convenção**: `V{n}__{descricao_em_snake_case}.sql`, numeração sequencial, **nunca** editar uma
migration já aplicada.

`V1__fundacao.sql` cria:

| Tabela | Chaves e restrições |
|---|---|
| `usuario` | PK `id`; **único** em `email` |
| `grupo` | PK `id` |
| `membro_grupo` | PK `id`; FK para `grupo` e `usuario`; **único parcial** em (`grupo_id`, `usuario_id`) onde `saiu_em IS NULL` |

Índices adicionais: `membro_grupo(usuario_id)` — é a consulta de `gruposDoUsuario()`, executada em
**toda** requisição autenticada.

> O índice único parcial é PostgreSQL puro (`CREATE UNIQUE INDEX ... WHERE saiu_em IS NULL`). Não é
> expressável como constraint JPA, então `ddl-auto: validate` não o verifica — ele vive só na
> migration, e isso precisa estar escrito em algum lugar. Está aqui.

---

## 6. Rastreabilidade

| Entidade / tipo | Requisitos | Histórias |
|---|---|---|
| `Usuario` | RF-01, RF-02, RF-05 | H-01, H-02, H-04 |
| `Grupo` | RF-06, RF-07 | H-05 |
| `MembroGrupo` | RF-08, RF-09, RF-10 | H-06, H-07, H-08 |
| `Dinheiro` | RNF-01 | — (usado a partir de U2) |
| `Visibilidade` + `ContextoUsuario` | RF-03, RF-04, RNF-05 | H-03 |
| `Escopo`, `Competencia` | RF-11 | — |
