# Regras de Negócio — U2 Lançamentos

Cada regra tem identificador, enunciado verificável e requisito de origem. As que produzem erro
declaram o código que o `ErroHandler` de U1 devolve.

**Prefixos**: `RN-C*` para categoria, `RN-L*` para lançamento, `RN-T*` para totalização.

**Herdadas de U1 e valendo aqui sem reescrita**: RN-U05 (operação exige autenticação) e RN-V01 a
RN-V04 (predicado de visibilidade). U2 não redefine nenhuma delas — as aplica.

---

## 1. `categoria`

### RN-C01 — Nome é obrigatório e não vazio
Após `trim`, precisa ter ao menos um caractere.

**Origem**: RF-36, H-33 · **Erro**: `NOME_OBRIGATORIO` (400)

### RN-C02 — Nome é único dentro do escopo
Nas categorias PESSOAIS, único por **dono**. Nas de GRUPO, único por **grupo** — de quem quer que
seja o dono.

**Origem**: RF-36, H-33, D-54 · **Erro**: `CATEGORIA_DUPLICADA` (409)

> A comparação é sobre o nome normalizado (`trim`, sem diferenciar maiúsculas). "Mercado" e
> "  mercado " são a mesma categoria — mesma decisão que RN-U01 tomou para e-mail, pelo mesmo
> motivo: o usuário não percebe a diferença, e o sistema não deveria criar duas linhas por ela.

> **Verificar para mensagem, restringir no banco para garantia.** A verificação prévia existe para
> devolver `409` com texto útil; o índice único parcial existe para que duas requisições
> simultâneas não criem as duas. É o padrão de U1 — e, pela lição de `cd310cb`, a gravação precisa
> ser com **flush imediato**, senão a violação escapa do `catch` que a traduziria.

### RN-C03 — Escopo GRUPO exige grupo do qual o dono é membro ativo
Bicondicional: `escopo == GRUPO` exige `grupo != null`; `escopo == PESSOAL` proíbe `grupo`.
E o grupo informado precisa ser um em que quem cria tem associação ativa.

**Origem**: RF-11 (por simetria), D-54 · **Erro**: `GRUPO_INVALIDO` (400), `NAO_E_MEMBRO` (404)

> Responde **404**, não 403, quando o grupo existe mas o usuário não é membro. Mesma razão de
> RN-G03 em U1: um 403 confirmaria a existência do grupo a quem não deveria saber dela.

### RN-C04 — Qualquer membro edita e exclui categoria de GRUPO
Renomear e excluir uma categoria de escopo GRUPO é permitido a qualquer membro ativo, não só ao
dono. O `dono` permanece quem criou.

**Origem**: RF-16 (por simetria) · **Erro**: `NAO_E_MEMBRO` (404)

> Decidida por julgamento, sem consulta, e registrada como tal no plano. RF-16 já estabelece que
> não há hierarquia sobre lançamentos do grupo; aplicar regra diferente à categoria criaria uma
> assimetria que nenhum requisito pede — e um estado sem saída se o dono deixasse o grupo.

### RN-C05 — Categoria com gastos vinculados não é excluída sem realocação
A exclusão é bloqueada. A mensagem informa **quantos** gastos estão vinculados. Com
`realocarPara` informado, os gastos migram para a categoria de destino e a exclusão prossegue,
**na mesma transação**.

**Origem**: RF-37, H-34, E-06 · **Erro**: `CATEGORIA_EM_USO` (409)

> A contagem faz parte da regra, não é enfeite: H-34 pede que a mensagem informe quantos gastos
> estão vinculados, porque é o número que permite ao usuário decidir se realoca ou desiste.

### RN-C06 — A realocação alcança gastos de qualquer dono
Numa categoria de GRUPO, os gastos vinculados podem ser de outros membros. Todos são realocados.

**Origem**: D-59, RF-16 · **Verificação**: teste com dois donos na mesma categoria

### RN-C07 — O destino da realocação precisa ser visível e diferente da origem
`realocarPara` precisa apontar para categoria que o usuário enxerga, e não pode ser a própria
categoria que está sendo excluída.

**Origem**: RF-37 · **Erro**: `CATEGORIA_NAO_ENCONTRADA` (404), `REALOCACAO_INVALIDA` (400)

### RN-C08 — A primeira listagem sem categoria alguma cria o conjunto inicial
Se `listar()` não encontra nenhuma categoria visível ao usuário, as dez iniciais são criadas com
escopo PESSOAL e devolvidas na mesma resposta.

**Origem**: RF-38, H-35, D-56

> **Consequência registrada**: apagar todas as categorias faz as dez ressurgirem na listagem
> seguinte. É o preço de não guardar estado de "já recebeu" — e o caso é raro o bastante para que o
> estado permanente custe mais do que o ressurgimento.

> **O critério é "nenhuma categoria visível", não "nenhuma categoria própria".** Quem entra num
> grupo que já tem categorias não recebe as iniciais: ele já tem com o que classificar. É o
> comportamento que H-35 quer — evitar que o usuário precise configurar antes de lançar.

---

## 2. `gasto`

### RN-L01 — Valor é estritamente positivo
Zero e negativo são rejeitados. Estorno não é gasto negativo — não há requisito de estorno.

**Origem**: RF-18, H-15 · **Erro**: `VALOR_INVALIDO` (400)

### RN-L02 — Categoria é obrigatória e precisa ser visível
Não existe gasto sem classificação. A categoria informada precisa ser visível ao lançador pelo
predicado RN-V01.

**Origem**: RF-18, H-15 · **Erro**: `CATEGORIA_OBRIGATORIA` (400), `CATEGORIA_NAO_ENCONTRADA` (404)

### RN-L03 — O escopo da categoria não precisa casar com o do gasto
Um gasto PESSOAL pode usar categoria de GRUPO, e um gasto de GRUPO pode usar categoria PESSOAL do
lançador. A única exigência é a de RN-L02: que a categoria seja visível.

**Origem**: D-60

> O escopo da categoria governa **quem a vê e quem a edita**, não a que lançamento ela se aplica.
> Sem esta regra escrita, a leitura natural seria a oposta — e obrigaria o usuário a manter
> "Mercado" duplicada nos dois escopos.

### RN-L04 — Escopo GRUPO exige grupo do qual o lançador é membro ativo
Bicondicional escopo↔grupo, mais associação ativa. Rejeita escopo GRUPO de quem não pertence a
grupo algum (E-09).

**Origem**: RF-11, H-09, E-09 · **Erro**: `GRUPO_INVALIDO` (400), `NAO_E_MEMBRO` (404)

### RN-L05 — O dono é quem lançou, e nunca muda
Definido na criação como o usuário autenticado. Nenhuma operação de edição o altera, mesmo quando
quem edita é outro membro.

**Origem**: RF-17, H-14 · **Verificação**: teste em que Rafael edita gasto de Ana e o dono continua
sendo Ana

> **É a invariante mais importante da unidade.** Se `dono` mudasse na edição, `totalPessoal`
> deixaria de significar o que RF-97 diz que significa — e o erro seria silencioso, porque a soma
> continuaria fechando.

### RN-L06 — Qualquer membro do grupo edita e exclui lançamento de escopo GRUPO
Ser dono não confere privilégio. Quem não é membro não edita nem exclui.

**Origem**: RF-16, RF-20, H-13 · **Erro**: `NAO_ENCONTRADO` (404)

> **404 e não 403**, pelo mesmo motivo de RN-G03: quem não enxerga o lançamento não deve descobrir
> que ele existe. O predicado de visibilidade e a permissão de edição coincidem nesta unidade —
> enxergar é poder editar.

### RN-L07 — Lançamento PESSOAL é editável e visível só pelo dono
Nem membros do mesmo grupo o alcançam.

**Origem**: RF-11, RN-V03 · **Erro**: `NAO_ENCONTRADO` (404)

### RN-L08 — O escopo é alterável, e a mudança é retroativa
PESSOAL → GRUPO, GRUPO → PESSOAL e troca de grupo são todas permitidas a quem pode editar o
lançamento. O efeito sobre a visibilidade é imediato e vale para o histórico.

**Origem**: D-58

> **Consequência aceita, não mitigada**: tornar GRUPO um gasto que era PESSOAL expõe aos membros um
> lançamento que eles nunca viram. É coerente com D-13 — membro novo já enxerga todo o histórico do
> grupo —, mas é uma porta de exposição que não existia antes de D-58, e está registrada como tal.

### RN-L09 — Data é livre, inclusive futura
Sem validação de limite superior nem inferior.

**Origem**: D-61

> O gasto futuro não contamina o total do mês corrente: a consulta filtra por intervalo de datas, e
> um gasto de setembro simplesmente não entra no total de agosto.

### RN-L10 — Em U2, `cartao` e `competencia` são sempre nulos
Nenhuma operação desta unidade os define. A entidade os carrega por antecipação a U3.

**Origem**: Units Generation · **Verificação**: teste que falha se algum caminho de U2 os preencher

---

## 3. Consulta e totalização

### RN-T01 — Toda consulta passa pelo predicado de visibilidade
Sem exceção, e por construção: `Gasto` e `Categoria` são acessados pela porta
`RepositorioComVisibilidade`, que não expõe `buscarTodos` nem `buscarPorId`.

**Origem**: RN-V01, D-52, H-03 · **Verificação**: teste de isolamento com dois usuários e um grupo

> **U2 é a primeira unidade a implementar essa porta.** Em U1 ela nasceu sem implementador. Se o
> predicado estiver errado, o erro se propaga para U3 e U4 sem ser notado — daí o teste de
> isolamento ser replicado aqui integralmente.

### RN-T02 — `totalPessoal` soma só o que é do consultante
Soma os gastos do período em que `dono == consultante`, **de qualquer escopo** — pessoais e de
grupo, indistintamente.

**Origem**: RF-97, H-17, D-28

### RN-T03 — `totalGrupo` soma tudo daquele grupo
Soma os gastos do período com `escopo == GRUPO` e `grupo == o grupo consultado`, **de qualquer
dono** — inclusive de quem já saiu (D-62).

**Origem**: RF-97, H-17, D-28, D-62

### RN-T04 — Os dois totais nunca se somam
Não existe operação, campo ou resposta que apresente `totalPessoal + totalGrupo`. São grandezas
distintas, e a soma não tem significado.

**Origem**: RF-97, D-28 · **Verificação**: teste que percorre todos os DTOs de saída procurando um
campo que seja a soma dos dois

> Um gasto de escopo GRUPO cujo dono é o consultante entra **nos dois** totais. Não é dupla
> contagem porque os números nunca se encontram; é a mesma quantia respondendo a duas perguntas
> diferentes.

### RN-T05 — Com mais de um grupo, o filtro de grupo é obrigatório
Se o usuário tem associação ativa em dois ou mais grupos e não informa `grupoId`, a consulta é
rejeitada. Com zero ou um grupo, o filtro é dispensável e o único grupo é assumido.

**Origem**: D-55 · **Erro**: `GRUPO_OBRIGATORIO` (400)

> Sem a regra, `totalGrupo` somaria a casa com a viagem num número que não descreve nenhuma das
> duas. A alternativa era devolver um subtotal por grupo; a exigência do filtro foi preferida por
> manter a resposta com um significado só.

> **Não quebra H-16**: o filtro escolhe *qual* grupo, e a listagem continua misturando os gastos
> pessoais com os daquele grupo, como a história pede.

### RN-T06 — A listagem é paginada; os totais vêm de operação própria
`consultar` devolve a página. `totalizar` é uma operação separada, com os mesmos filtros, que
devolve os dois totais e a quebra por categoria sobre **o período inteiro**.

**Origem**: RF-21, RF-22, D-57

> **Divergência deliberada do `openapi.yaml`**, que desenhou `PaginaGastos` com os totais embutidos.
> O contrato escrito à mão é referência de design; a fonte passou a ser o springdoc, gerado do
> código (D-06). A divergência está registrada aqui e em `business-logic-model.md` §5 para não ser
> descoberta como surpresa em U3.

### RN-T07 — Os totais por categoria seguem a mesma separação
A quebra por categoria existe nas duas perspectivas: por categoria do que é meu, e por categoria do
que é do grupo. Nunca numa terceira lista que misture as duas.

**Origem**: RF-22, H-17

---

## 4. Alvos de property-based testing

Modo **Parcial** da extensão (PBT-02, PBT-03, PBT-07, PBT-08, PBT-09 bloqueantes).

| # | Propriedade | Regra |
|---|---|---|
| 1 | Para qualquer conjunto de gastos, `totalPessoal` = soma dos que têm `dono == consultante`, e nada mais entra | RN-T02 |
| 2 | Para qualquer conjunto, `totalGrupo` = soma dos de escopo GRUPO daquele grupo, independente do dono | RN-T03 |
| 3 | A soma das quebras por categoria é igual ao total correspondente, em ambas as perspectivas | RN-T07 |
| 4 | Para qualquer sequência de edições que não seja a de exclusão, `dono` é invariante | RN-L05 |
| 5 | Para qualquer par (usuário, gasto), o gasto aparece na listagem **se e somente se** o predicado RN-V01 for verdadeiro | RN-T01 |

> A propriedade 5 é a mais valiosa e a mais cara de gerar: exige gerador de usuários, grupos,
> associações e gastos correlacionados. É também a única que testa uma **bicondicional** — não basta
> que o visível apareça, é preciso que o invisível não apareça. Testes de exemplo cobrem bem o
> primeiro lado e mal o segundo.

---

## 5. Rastreabilidade

| História | Regras |
|---|---|
| H-09 Definir escopo | RN-L04 |
| H-13 Editar como qualquer membro | RN-L05, RN-L06, RN-L07 |
| H-14 Registrar o dono | RN-L05 |
| H-15 Gerenciar gastos | RN-L01, RN-L02, RN-L06, RN-L09 |
| H-16 Consultar por período | RN-T01, RN-T05, RN-T06 |
| H-17 Os dois totais | RN-T02, RN-T03, RN-T04, RN-T07 |
| H-33 Gerenciar categorias | RN-C01, RN-C02, RN-C03, RN-C04 |
| H-34 Proteger categoria em uso | RN-C05, RN-C06, RN-C07 |
| H-35 Categorias iniciais | RN-C08 |

**23 regras**, todas com requisito de origem e verificação declarada. As 9 histórias estão cobertas.
