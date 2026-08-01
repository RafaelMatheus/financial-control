# Plano de Functional Design — U2 Lançamentos

> Fonte única de verdade da Functional Design de U2. Cada passo tem checkbox, marcado no mesmo
> momento em que o trabalho é concluído.

---

## 1. Contexto da unidade

| | |
|---|---|
| **Componentes** | `categoria`, `gasto` (**à vista apenas**) |
| **Entidades** | `Categoria`, `Gasto` |
| **Histórias** | H-09, H-13, H-14, H-15 (parcial), H-16, H-17, H-33, H-34, H-35 (9) |
| **Requisitos** | RF-11, RF-16 a RF-22, RF-36 a RF-38, RF-97 |
| **Depende de** | U1 (entregue e verde no CI) |
| **Bloqueia** | U3, U4 |

**O que U1 deixou pronto e U2 consome**: `Dinheiro`, `Competencia`, `Escopo`,
`ContextoUsuario`, `RepositorioComVisibilidade` (porta **sem método cru**), `CriterioVisibilidade`,
`ErroHandler`, `CodigoErro`.

**U2 é a primeira unidade a implementar `RepositorioComVisibilidade`.** Em U1 a porta nasceu sem
implementador — `Usuario`, `Grupo` e `MembroGrupo` não têm dono no sentido de RF-03. `Gasto` e
`Categoria` são as primeiras entidades com dono, e portanto a primeira prova real do predicado.

**Fora do escopo desta unidade** (vão para U3): associação de gasto com cartão, cálculo de
competência de fatura, parcelamento, bloqueio de alteração em fatura paga (RF-95). A entidade
`Gasto` nasce aqui **já com `cartaoId` e `competencia` nuláveis**, para evitar `ALTER TABLE` em U3 —
decisão registrada na Units Generation.

---

## 2. Passos

### Análise

- [x] **Passo 1** — Ler a definição de U2 em `unit-of-work.md` e as 9 histórias atribuídas
- [x] **Passo 2** — Ler o contrato herdado de U1 (`Escopo`, `RepositorioComVisibilidade`,
      `CriterioVisibilidade`) e os métodos desenhados na Application Design
- [x] **Passo 3** — Levantar as ambiguidades que afetam o design (§3)

### Esclarecimento

- [x] **Passo 4** — Formular as questões — **9 questões**, em três rodadas (7 previstas + 2 que
      nasceram das respostas)
- [x] **Passo 5** — Coletar as respostas via AskUserQuestion e registrá-las em §3
- [x] **Passo 6** — Reanalisar as respostas: uma resposta ambígua desempatada numa rodada extra,
      duas questões novas derivadas, uma ressalva minha corrigida (§3.4)

### Modelagem

- [x] **Passo 7** — `domain-entities.md`: `Categoria` e `Gasto` — atributos, tipos, nulabilidade,
      invariantes de construção e o que **não** é atributo, com o motivo
- [x] **Passo 8** — `business-rules.md`: regras numeradas `RN-C*` (categoria) e `RN-L*` (lançamento),
      cada uma com requisito de origem e o teste que falha se a regra sair
- [x] **Passo 9** — `business-logic-model.md`: fluxos de lançar, editar, excluir, consultar e
      totalizar; e o fluxo de exclusão de categoria com realocação
- [x] **Passo 10** — Modelar **os dois totais** (RF-97, D-28) como grandezas separadas, com o
      algoritmo explícito de cada uma e a prova de que nunca se somam
- [x] **Passo 11** — Diagramas Mermaid, validados antes da escrita

### Verificação

- [x] **Passo 12** — Rastreabilidade: cada uma das 9 histórias mapeada para regras e fluxos
- [x] **Passo 13** — Mapear os alvos de property-based testing desta unidade
- [x] **Passo 14** — Registrar as decisões novas (numeração continua de D-53)

---

## 3. Questões

> Conforme a preferência registrada do usuário, **todas as questões são feitas via AskUserQuestion**,
> não por preenchimento de tag neste arquivo. As respostas são transcritas aqui depois de dadas.

### Rodada 1 — estrutura

**Q1 — A categoria pertence a um usuário ou pode ser do grupo?**
A Application Design deu a `Categoria` os atributos `id`, `nome`, `usuario` — uma categoria por
usuário. Mas um gasto de escopo GRUPO carrega uma categoria, e os outros membros veem esse gasto.
Se Ana e Rafael criam cada um a sua categoria "Mercado", o total por categoria do grupo mostra
**duas linhas "Mercado"** com UUIDs diferentes. É o ponto onde a decisão de RF-97 (dois totais)
encosta na modelagem de categoria.

[Resposta]: **Categoria também tem escopo (PESSOAL ou GRUPO)**, simétrica ao gasto. → **D-54**

**Q2 — Com o usuário em dois grupos e sem filtro de grupo, o que é `totalGrupo`?**
RF-97 define `totalGrupo` como "todos os lançamentos de escopo GRUPO daquele grupo". Com dois
grupos e nenhum filtro, somar tudo mistura duas casas num número que não descreve nenhuma delas.

[Resposta]: **Exigir o filtro de grupo** quando o usuário pertence a mais de um. → **D-55**

**Q3 — Quando as categorias iniciais são criadas (RF-38)?**
"No primeiro acesso" não diz o momento técnico. U1 já está entregue e aprovada, então criá-las no
cadastro significa tocar código de uma unidade fechada.

[Resposta]: **Sob demanda, na primeira listagem** — sem nenhuma categoria, o conjunto inicial nasce ali. → **D-56**

**Q4 — A consulta é paginada, e os totais cobrem a página ou o período?**
A Application Design chamou o retorno de `PaginaGastos`, mas não declarou campos de paginação.
Um total que cobre só a página responde a uma pergunta que ninguém fez.

[Resposta]: **Endpoint separado de totais.** A listagem devolve só a página; os totais vêm de chamada própria. → **D-57**

### Rodada 2 — comportamento

**Q5 — O escopo de um gasto pode ser alterado depois de criado?**
Mudar de PESSOAL para GRUPO torna visível, retroativamente, algo que era privado. Mudar de GRUPO
para PESSOAL esconde de quem já via.

[Resposta]: **Sim, livremente** — trocar de escopo e trocar de grupo. → **D-58**

**Q6 — Na exclusão de categoria com realocação, o que fazer com gastos de outros donos?**
H-34 oferece realocar os gastos vinculados. Se a categoria for de grupo, parte dos gastos
vinculados pode ser de outro dono.

[Resposta]: **Realoca tudo** — quem pode editar o gasto (RF-16) pode reclassificá-lo. → **D-59**

**Q7 — Gasto com data futura é aceito?**
Não há requisito. A data é livre, mas um gasto futuro num total de "quanto gastei em agosto"
mistura previsão com realizado.

[Resposta]: **Aceita, sem restrição** — a data é um fato declarado pelo usuário. → **D-61**

### Rodada 3 — questões nascidas das respostas

**Q8 — Um gasto PESSOAL pode usar uma categoria de GRUPO, e vice-versa?**
Só existe porque Q1 deu escopo à categoria. Sem ela, o design ficaria com duas leituras possíveis.

[Resposta]: **Sim, qualquer categoria visível.** O escopo da categoria decide quem a vê e quem a
edita, não a que lançamento ela pode ser aplicada. → **D-60**

**Q9 — Os gastos de quem saiu do grupo continuam contando no total do grupo?**
D-44 corta a visibilidade do ex-membro. A pergunta é a recíproca, que D-44 não responde: o que os
que ficaram continuam vendo.

[Resposta]: **Sim, continuam.** Ana deixa de ver; os demais continuam vendo os gastos dela.
→ **D-62**

### 3.4 Reanálise das respostas (Passo 6)

**Uma ressalva minha estava errada e foi corrigida.** Ao apresentar Q2, escrevi que exigir
`grupoId` "quebra a listagem que mistura pessoal e grupo (H-16)". Não quebra: o filtro escolhe
*qual* grupo, e a listagem continua trazendo os gastos pessoais junto com os daquele grupo. A
resposta escolhida é compatível com H-16.

**Uma resposta precisou de desempate.** A resposta original de Q4 — *"paginada mas você pode deixar
a informação de total já pronta para listagem de maneira mais rápida sem depender dos retornos
daquele momento"* — admitia duas implementações incompatíveis: agregação no banco a cada requisição,
ou uma tabela de totais materializada e mantida na escrita. A diferença entre elas é ter uma ou duas
fontes de verdade para o mesmo número. Desempatada numa rodada extra: **endpoint separado**.

**Nenhuma contradição entre as respostas.** D-58 (trocar escopo livremente) e D-62 (gastos do
ex-membro permanecem) puxam na mesma direção de D-13: o histórico do grupo é do grupo.

**Duas decisões resolvidas por julgamento, sem consultar** — seguem de D-54 por simetria com regras
já aprovadas, e estão registradas para poderem ser contestadas:

| | Decisão | Por quê |
|---|---|---|
| Unicidade do nome de categoria | Por **dono** nas PESSOAIS; por **grupo** nas de GRUPO | Se fosse sempre por dono, Ana e Rafael criariam duas "Mercado" no mesmo grupo — exatamente o que Q1 veio resolver |
| Quem edita e exclui categoria de GRUPO | **Qualquer membro** | Simetria direta com RF-16, que já diz isso dos lançamentos do grupo |

---

## 4. Escopo e riscos

| Risco | Tratamento |
|---|---|
| `Gasto` é a primeira entidade a implementar `RepositorioComVisibilidade`; um erro aqui vale para todas as unidades seguintes | O teste de isolamento de U1 (Passo 22) é replicado sobre `Gasto` e `Categoria`, com os três casos de H-03 |
| `ddl-auto: validate` reprovar o mapeamento contra a migration `V2` | Mesmo tratamento de U1: ajustar a migration, nunca desligar o `validate` |
| Confundir `totalPessoal` com `totalGrupo` em algum caminho de código | Nomes que não se confundem e um teste que falha se os dois forem somados |
| **D-57 divergir do `openapi.yaml`** — o contrato desenhado na Application Design tem `PaginaGastos` com os totais embutidos | Divergência real e deliberada. O `openapi.yaml` é referência de design (D-06); a fonte passa a ser o springdoc. Registrada em `business-logic-model.md` |
| **D-56** disparar duas vezes em requisições simultâneas, criando categorias iniciais em dobro | Restrição de unicidade no banco absorve a corrida; é o mesmo padrão de U1 — verificar para mensagem, restringir no banco para garantia. E agora com `saveAndFlush`, pela lição de `cd310cb` |
| **D-56** faz as categorias padrão ressurgirem para quem apagar todas | Consequência aceita e documentada. Alternativa seria uma marca de "já recebeu as iniciais", que é estado a mais para um caso de borda |
| **D-58** expõe retroativamente um gasto que era privado | Consequência aceita, coerente com D-13. Documentada em `business-rules.md`, não mitigada |

**O que este plano não faz**: nada de cartão, fatura, parcela ou competência; nada de receita,
orçamento ou investimento; nenhum endpoint de U3 ou U4.
