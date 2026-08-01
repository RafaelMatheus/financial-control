# Plano de NFR Design — U2 Lançamentos

> Fonte única de verdade da NFR Design de U2. Cada passo tem checkbox, marcado no mesmo momento em
> que o trabalho é concluído.

---

## 1. Nota de pré-requisito

A regra desta stage pede que a **NFR Requirements da unidade** esteja concluída. U2 não tem uma:
ela foi executada **uma vez, em U1**, porque as decisões que ela fecha — D-02 (autenticação),
D-05 (framework de PBT), D-06 (OpenAPI) e a stack inteira — são da aplicação, não da unidade. O
plano de execução previu isso desde a Workflow Planning, e a stage não aparece na lista de U2.

**Insumo desta stage**, portanto: `aidlc-docs/construction/u1-fundacao/nfr-requirements/` (14 NFRs
e as decisões de stack), mais a Functional Design de U2, recém-aprovada.

---

## 2. Correção estrutural do processo de perguntas

O deslize de pré-escrever as respostas do usuário aconteceu na Functional Design de U1 e **se
repetiu** na de U2, onde duas das sete divergiram do que ele respondeu (research-log 3.38, O-32).
Registrar o erro no audit não impediu a repetição — registro não é mecanismo.

**A partir deste plano, a mudança é estrutural**: o arquivo nasce **sem seção de respostas**. As
perguntas ficam em §4; a seção de respostas (§5) **não existe** até que as respostas existam. Não há
campo para preencher indevidamente porque não há campo.

---

## 3. Passos

### Análise

- [x] **Passo 1** — Ler os 14 NFRs de U1 e as decisões de stack que valem para toda a aplicação
- [x] **Passo 2** — Ler a Functional Design de U2 e identificar o que ela exige da infraestrutura
- [x] **Passo 3** — Avaliar as **5 categorias obrigatórias** (Resilience, Scalability, Performance,
      Security, Logical Components), declarando aplicabilidade com justificativa — inclusive as
      não-aplicáveis, que precisam de motivo escrito e não de silêncio

### Esclarecimento

- [x] **Passo 4** — Formular as questões (§4)
- [x] **Passo 5** — Coletar as respostas via AskUserQuestion e criar a §5 com elas
- [x] **Passo 6** — Reanalisar em busca de contradição ou ambiguidade residual

### Design

- [x] **Passo 7** — `nfr-design-patterns.md`: como a porta de visibilidade cresce sem perder a
      garantia; agregação monetária; estratégia de carga da listagem; paginação
- [x] **Passo 8** — `logical-components.md`: componentes novos de U2 e a tabela do que
      **deliberadamente não existe**, estendendo a de U1
- [x] **Passo 9** — Diagramas Mermaid, validados antes da escrita
- [x] **Passo 10** — Registrar as decisões novas (numeração continua de D-62)

---

## 4. Questões

> Todas feitas via AskUserQuestion, conforme a preferência registrada do usuário.

**Q1 — Como a porta de visibilidade cresce para caber uma consulta com filtros?**

É o problema central desta stage, e U2 é a primeira unidade a encontrá-lo. A porta que U1 escreveu é:

```kotlin
interface RepositorioComVisibilidade<T> {
    fun buscarVisivel(id: UUID): T?
    fun listarVisiveis(): List<T>
}
```

`listarVisiveis()` **não recebe filtro nenhum**. U2 precisa consultar por intervalo de datas, com
filtros de categoria, grupo, escopo e dono, paginado (RN-T06). Usar `listarVisiveis()` e filtrar em
memória traria toda a base para dentro da aplicação a cada consulta.

A garantia de D-52 — *"consulta sem filtro não vira bug, vira erro de compilação"* — depende de
**como** essa porta cresce. Crescer errado a desfaz sem que ninguém perceba.

**Q2 — Onde a soma do dinheiro acontece?**

`totalizar` (D-57) precisa somar valores de milhares de linhas potenciais. Somar no banco é uma
linha de SQL; somar na aplicação passa por `Dinheiro`, o value object que U1 escreveu com escala 2
e HALF_UP, e que tem testes de propriedade cobrindo a aritmética.

**Q3 — Como a listagem carrega categoria, dono e grupo sem cair no N+1?**

`open-in-view: false` está ligado desde U1 — acesso preguiçoso fora da transação **estoura**, e isso
foi escolhido de propósito para transformar um problema de desempenho difícil de notar num erro
difícil de ignorar. A listagem de gastos precisa do nome da categoria e do nome do dono em cada
item.

**Q4 — Como garantir que a próxima entidade com dono não escape do padrão?**

U1 provou o isolamento com um teste dedicado. U2 acrescenta duas entidades; U3 acrescenta seis. A
pergunta é se a garantia continua sendo escrita à mão, entidade por entidade, ou se vira algo que
falha sozinho quando alguém esquece.

---

## 5. Respostas

*Esta seção foi criada depois de as respostas existirem, conforme §2.*

| | Resposta | Decisão |
|---|---|---|
| **Q1** | **Porta por feature, com filtro obrigatório.** `GastoRepositorio` estende a base e declara `consultar(filtro, paginacao)` e `totalizar(filtro)`. O critério de visibilidade nunca é parâmetro | **D-63** |
| **Q2** | **`SUM` no banco**, sem o teste de comparação entre as duas aritméticas | **D-64** |
| **Q3** | **Projeção direta para DTO** na consulta de leitura | **D-65** |
| **Q4** | **Teste de arquitetura que falha sozinho** quando uma entidade com dono escapa do padrão | **D-66** |

### 5.1 Reanálise (Passo 6)

**Um risco fica aberto por escolha explícita.** A opção escolhida em Q2 foi a de somar no banco
**sem** o teste de propriedade que compara o `SUM` com a soma feita por `Dinheiro`. A opção com o
teste estava disponível e não foi a escolhida, então o risco não é omissão — é decisão. Fica
registrado: a aplicação passa a ter **dois lugares onde dinheiro é somado**, e nada verifica que
concordam.

A mitigação parcial que sobrevive sem custo: a coluna é `numeric(_,2)` e a soma de decimais exatos
não arredonda, porque não há divisão envolvida. A divergência só apareceria se a escala do banco e a
de `Dinheiro` divergissem — e essa é uma condição que o `ddl-auto: validate` pega.

**Q3 cria uma tensão real com D-29, e ela precisa ficar escrita.** D-29 decidiu *"mesmo modelo para
escrita e leitura"*. A projeção direta para DTO cria um segundo caminho de leitura, que não passa
pela entidade de domínio.

A tensão é real, mas menor do que parece: não há segunda tabela, segunda fonte de verdade, nem
sincronização. É a **mesma tabela lida de duas formas** — a entidade quando se vai escrever, a
projeção quando só se vai exibir. D-29 recusava CQRS com armazenamentos separados; isto não é isso.
**Registrado como refinamento de D-29, não como revogação**, e escrito em
`nfr-design-patterns.md` §4 para que U3 não trate como precedente para separar de verdade.

**Q1 e Q3 se combinam bem.** A porta por feature é o lugar exato onde a projeção mora: `consultar`
devolve o modelo de leitura, `buscarVisivel` devolve a entidade. Quem for editar recebe entidade;
quem for listar recebe projeção. Os dois métodos já são diferentes, então a distinção não precisa
ser lembrada.

---

## 6. Categorias obrigatórias — avaliação

| Categoria | Aplicabilidade em U2 | Justificativa |
|---|---|---|
| **Resilience** | **Não-aplicável** | Nenhuma integração externa nova. A única falha possível continua sendo a indisponibilidade do banco, tratada em U1. Nada em U2 chama nada fora do processo |
| **Scalability** | **Não-aplicável, com registro** | RNF-12: uso doméstico, dezenas de usuários, instância única. U2 não adiciona componente com estado — o único do sistema continua sendo o `RegistroDeTentativas` de U1 |
| **Performance** | **Aplicável** | É a categoria desta unidade. Primeira consulta com volume de verdade, primeira agregação, primeira paginação — Q1, Q2 e Q3 |
| **Security** | **Aplicável** | U2 é a primeira unidade a **implementar** o predicado de visibilidade. Se ele estiver errado aqui, o erro se propaga a U3 e U4 — Q1 e Q4 |
| **Logical Components** | **Parcial** | Nenhum componente de infraestrutura novo é esperado. O trabalho é estender a tabela do que deliberadamente não existe |

---

## 7. Riscos

| Risco | Tratamento |
|---|---|
| A porta crescer de um jeito que reabra a possibilidade de consulta sem filtro | É o objeto de Q1, e o critério de aceitação é o mesmo de D-52: escrever consulta sem filtro tem que ser erro de compilação |
| Agregação no banco divergir da aritmética de `Dinheiro` | **Risco aceito por decisão** (D-64). A opção com teste de comparação foi apresentada e não foi a escolhida. Mitigação residual: escala 2 no banco, verificada pelo `ddl-auto: validate` |
| `open-in-view: false` transformar um N+1 em exceção em produção | Objeto de Q3. O comportamento é o desejado — o que não pode é descobri-lo em produção. Teste de integração que conta as consultas emitidas |
| A estrutura de teste de isolamento não acompanhar as entidades novas | Objeto de Q4 |
