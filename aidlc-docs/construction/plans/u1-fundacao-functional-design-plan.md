# Plano de Functional Design — U1 Fundação

**Unidade**: U1 — Fundação · **Componentes**: `common`, `usuario`, `grupo`
**Entidades**: `Usuario`, `Grupo`, `MembroGrupo`
**Histórias**: H-01 a H-08 · **Requisitos**: RF-01 a RF-10, RNF-01, RNF-05, RNF-09, RNF-10

> **Unidade mais crítica do projeto.** `Visibilidade` e `Dinheiro` são usados por todas as demais —
> um erro aqui se propaga para o sistema inteiro.

---

## 1. Passos

- [x] 1.1 Modelar `Usuario` — atributos, invariantes, unicidade de e-mail
- [x] 1.2 Modelar `Grupo` e `MembroGrupo` — ciclo de vida da associação
- [x] 1.3 Especificar `Dinheiro` — aritmética, escala, arredondamento, `dividirEm`
- [x] 1.4 Especificar `Escopo` e `Competencia`
- [x] 1.5 Especificar `ContextoUsuario` e `Visibilidade` — o predicado de isolamento
- [x] 1.6 Especificar `ErroHandler` — taxonomia de erro e formato de resposta
- [x] 1.7 Detalhar as regras de negócio de `usuario` (RF-01 a RF-05)
- [x] 1.8 Detalhar as regras de negócio de `grupo` (RF-06 a RF-10)
- [x] 1.9 Definir o schema inicial e a convenção de migrations Flyway (D-01)
- [x] 1.10 Mapear os alvos de property-based testing desta unidade
- [x] 1.11 Gerar `domain-entities.md`
- [x] 1.12 Gerar `business-rules.md`
- [x] 1.13 Gerar `business-logic-model.md`

Sem `frontend-components.md`: o front-end está fora deste repositório.

---

## 2. Questões

> Coletadas via `AskUserQuestion`, conforme a preferência registrada do usuário. As respostas são
> transcritas aqui para preservar o rastro documental exigido pelo método.

### Q1 — Onde mora a credencial do usuário?

`components.md` atribui `senhaHash` a `Usuario`, o que implica autenticação própria. A alternativa é
delegar a um provedor externo, e nesse caso a entidade não guarda credencial nenhuma.

Afeta diretamente o modelo de domínio, então não dá para adiar junto com D-02 — o *mecanismo* de
sessão pode esperar pela NFR Requirements, mas a *posse da credencial* não.

**[Answer]**: **Autenticação própria, com `senhaHash` na entidade `Usuario`.**

### Q2 — Política de arredondamento de `Dinheiro`

RNF-01 exige arredondamento explícito, sem dizer qual.

**[Answer]**: **`HALF_UP`** — é o arredondamento de fatura e extrato bancário brasileiro, previsível
para quem confere a conta na mão.

### Q3 — O que um ex-membro enxerga do grupo?

H-08 diz que quem sai "deixa de enxergar novos lançamentos", sem dizer o que acontece com os
antigos. H-07 diz que quem entra enxerga todo o histórico. As duas juntas deixam o caso do ex-membro
em aberto.

**[Answer]**: **Corte total.** Ao sair, perde a visibilidade de tudo do grupo, inclusive do passado.
A visibilidade acompanha a associação atual. Os lançamentos de que ele é dono continuam dele e
seguem no total pessoal.

### Q4 — Reentrada no mesmo grupo

`MembroGrupo` tem `entrouEm` e `saiuEm`. Se a pessoa voltar, é uma linha nova ou a mesma reaberta?

**[Answer]**: **Linha nova a cada entrada.** `MembroGrupo` vira um histórico de participações.

### Q5 — Unicidade de e-mail

Comparação sensível a maiúsculas ou não.

**[Answer]**: **Normalizar** para minúsculas, com `trim`. A unicidade vale sobre a forma normalizada.

### Q6 — Grupo sem membros

Se o último membro sair, o grupo fica órfão.

**[Answer]**: **Permitir o grupo vazio**, sem exclusão automática. Ninguém o enxerga até que alguém
seja adicionado, e os lançamentos vinculados permanecem íntegros.

---

## 3. Decisões fechadas por esta stage

| ID | Decisão | Origem |
|---|---|---|
| D-42 | Autenticação própria; `senhaHash` em `Usuario` | Q1 |
| D-43 | Arredondamento `HALF_UP`, escala 2 | Q2 |
| D-44 | Ex-membro sofre corte total de visibilidade | Q3 |
| D-45 | Reentrada cria nova linha de `MembroGrupo` | Q4 |
| D-46 | E-mail normalizado para minúsculas, com `trim` | Q5 |
| D-47 | Grupo sem membros é permitido | Q6 |

**Não fecha**: D-02 (mecanismo de sessão) — segue para a NFR Requirements desta mesma unidade.

---

## 4. Artefatos gerados

`aidlc-docs/construction/u1-fundacao/functional-design/`

- `domain-entities.md`
- `business-rules.md`
- `business-logic-model.md`
