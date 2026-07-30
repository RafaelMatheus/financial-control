# User Stories — financial-control

**Stage**: INCEPTION - User Stories - Part 2
**Timestamp**: 2026-07-30T16:11:59Z
**Persona**: Usuário (única — ver `personas.md`)
**Fonte**: `requirements.md` revisão 7 (67 requisitos de domínio)

---

## Como ler este documento

**Organização**: 11 épicos por área + 3 jornadas transversais (fluxos que cruzam 3 ou mais áreas).

**Prioridade**: `[M]` must · `[S]` should · `[C]` could — herdada dos requisitos.

**`NÚCLEO`**: história que compõe o **primeiro corte utilizável** do sistema — cadastrar-se, lançar
gastos, gerenciar cartões e parcelas, e dividir despesas em grupo. Sem investimentos, orçamento ou
receitas. É a base sugerida para a primeira unidade de trabalho na Units Generation.

**Critérios de aceitação**: Gherkin (Dado/Quando/Então) nas histórias com regra de negócio; lista de
asserções nas de CRUD simples.

**🔬 PBT**: marca histórias cuja invariante é alvo direto de property-based testing (RNF-07).

---

# ÉPICO E1 — Identidade e Acesso

Cobre RF-01 a RF-05. Base de tudo: sem identidade não há isolamento de dados nem grupos.

---

### H-01 — Criar conta `[M]` `NÚCLEO`
**Como** usuário, **quero** criar uma conta com meu e-mail e uma senha, **para** que meus dados
financeiros fiquem associados a mim.

**Critérios de aceitação**
- Permite cadastro informando e-mail e credencial de acesso
- Rejeita cadastro com e-mail já existente
- Rejeita e-mail em formato inválido
- A credencial nunca é armazenada nem retornada em texto claro

**Requisitos**: RF-01

---

### H-02 — Autenticar `[M]` `NÚCLEO`
**Como** usuário, **quero** me autenticar, **para** acessar meus dados financeiros.

```gherkin
Dado que possuo uma conta ativa
Quando me autentico com credenciais corretas
Então recebo uma credencial de sessão válida
E consigo executar operações sobre meus dados

Dado que possuo uma conta ativa
Quando me autentico com credenciais incorretas
Então o acesso é negado
E a resposta não revela se o e-mail existe ou se a senha está errada

Dado que não estou autenticado
Quando tento qualquer operação sobre dados financeiros
Então a operação é rejeitada
```

**Requisitos**: RF-02

> **Nota**: o mecanismo (JWT, sessão, OAuth2) é decisão D-02, adiada para a NFR Requirements. Esta
> história especifica o comportamento, não a tecnologia.

---

### H-03 — Isolamento de dados entre usuários `[M]` `NÚCLEO`
**Como** usuário, **quero** que ninguém acesse meus dados sem que eu tenha compartilhado,
**para** que minha vida financeira permaneça privada.

```gherkin
Dado que sou membro do grupo "Apartamento 42"
E existe o usuário Carlos, que não pertence a esse grupo
Quando Carlos tenta acessar um gasto de escopo GRUPO desse grupo
Então o acesso é negado

Dado que lancei um gasto de escopo PESSOAL
Quando qualquer outro usuário tenta acessá-lo
Então o acesso é negado
E isso vale mesmo que sejamos membros do mesmo grupo

Dado que consulto qualquer listagem do sistema
Quando a consulta é executada
Então ela retorna somente o que eu possuo ou enxergo por pertencer a um grupo
```

**Requisitos**: RF-03, RF-04

> Esta é uma história de **regra transversal**: vale para todo recurso do sistema, não apenas para
> gastos. Serve de critério de revisão para toda consulta implementada.

---

### H-04 — Gerenciar o próprio perfil `[S]`
**Como** usuário, **quero** consultar e atualizar meus dados de perfil, **para** mantê-los corretos.

**Critérios de aceitação**
- Permite consultar os próprios dados de perfil
- Permite atualizar os dados editáveis
- Não permite consultar nem alterar o perfil de outro usuário

**Requisitos**: RF-05

---

# ÉPICO E2 — Grupos

Cobre RF-06 a RF-10. Um grupo é uma coleção nomeada de pessoas que compartilham gastos — casa,
república, casal, viagem.

---

### H-05 — Gerenciar grupos `[M]` `NÚCLEO`
**Como** usuário, **quero** criar e administrar grupos, **para** organizar com quem compartilho
despesas.

**Critérios de aceitação**
- Permite criar grupo informando um nome identificador
- Permite renomear um grupo do qual sou membro
- Permite listar os grupos dos quais sou membro
- Quem cria o grupo **não** recebe privilégio sobre os demais membros
- Participar de grupo é opcional — o sistema funciona integralmente sem nenhum grupo

**Requisitos**: RF-06, RF-07

---

### H-06 — Gerenciar membros do grupo `[M]` `NÚCLEO`
**Como** usuário, **quero** adicionar e remover pessoas de um grupo, **para** refletir quem
efetivamente divide as despesas.

**Critérios de aceitação**
- Permite adicionar um usuário existente como membro
- Permite remover um membro
- Não há limite fixo de membros
- Rejeita a adição de usuário inexistente, com erro de validação (E-07)
- Qualquer membro pode adicionar ou remover — não há hierarquia

**Requisitos**: RF-08

---

### H-07 — Visibilidade do histórico ao entrar num grupo `[M]` `NÚCLEO`
**Como** usuário que acabou de entrar num grupo, **quero** enxergar o histórico de gastos dele,
**para** entender o padrão de despesas de que passo a participar.

```gherkin
Dado que o grupo "Apartamento 42" existe desde janeiro
E possui gastos lançados de janeiro a julho
Quando entro no grupo em agosto
Então enxergo todos os gastos do grupo, inclusive os de janeiro a julho

Dado que enxergo um gasto de grupo lançado antes da minha entrada
Quando consulto minha cota nesse gasto
Então não possuo cota — o rateio envolve apenas quem era membro à época
E o gasto aparece nos totais do grupo, mas não nos meus valores pessoais
```

**Requisitos**: RF-09 · **Resolve**: E-10, D-13

> **Consequência de modelagem**: visibilidade e rateio são **desacoplados**. As consultas de "total
> do grupo" e "minha cota" produzem resultados diferentes para um membro recém-adicionado.

---

### H-08 — Sair de um grupo `[S]`
**Como** usuário, **quero** sair de um grupo, **para** deixar de participar das despesas dele sem
apagar o passado.

```gherkin
Dado que sou membro de um grupo com gastos compartilhados lançados
Quando saio do grupo
Então deixo de enxergar novos gastos do grupo
E os gastos que lancei permanecem registrados com minha autoria
E minhas cotas em gastos anteriores permanecem inalteradas
```

**Requisitos**: RF-10 · **Cobre**: E-05

---

# ÉPICO E3 — Compartilhamento e Rateio

Cobre RF-11, RF-13 a RF-17. **A área de maior complexidade do sistema** — envolve dinheiro dividido
entre pessoas.

---

### H-09 — Definir o escopo de um gasto `[M]` `NÚCLEO`
**Como** usuário, **quero** marcar cada gasto como pessoal ou do grupo, **para** controlar quem o
enxerga.

**Critérios de aceitação**
- Permite marcar um gasto com escopo **PESSOAL** — visível apenas ao autor
- Permite marcar um gasto com escopo **GRUPO**, indicando de qual grupo
- Escopo GRUPO exige um grupo válido do qual o autor é membro
- Rejeita escopo GRUPO quando o autor não pertence a nenhum grupo (E-09)

**Requisitos**: RF-11 · **Cobre**: E-09

---

### H-10 — Dividir um gasto igualmente `[M]` `NÚCLEO`
**Como** usuário, **quero** que um gasto de grupo seja dividido igualmente por padrão, **para** não
precisar configurar rateio a cada lançamento.

```gherkin
Dado o grupo "Apartamento 42" com os membros Rafael e Ana
Quando lanço um gasto de R$ 400,00 com escopo GRUPO
E não configuro divisão
Então a cota de Rafael é R$ 200,00
E a cota de Ana é R$ 200,00

Dado um grupo com 3 membros
Quando lanço um gasto de R$ 100,00 com divisão igual
Então as cotas somam exatamente R$ 100,00
E o resíduo de centavos é atribuído de forma determinística
```

**Requisitos**: RF-13

---

### H-11 — Personalizar a divisão de um gasto `[M]` `NÚCLEO`
**Como** usuário, **quero** ajustar quanto cabe a cada pessoa num gasto, **para** refletir acordos
onde a divisão não é meio a meio.

```gherkin
Dado um gasto de R$ 400,00 no grupo com Rafael e Ana
Quando configuro a divisão por percentual, com 70% para Rafael e 30% para Ana
Então a cota de Rafael é R$ 280,00
E a cota de Ana é R$ 120,00

Dado um gasto de R$ 400,00 no grupo com Rafael e Ana
Quando configuro a divisão por valor absoluto, com R$ 250,00 e R$ 150,00
Então as cotas são exatamente esses valores

Dado um gasto com divisão personalizada
Quando altero o valor total do gasto
Então a divisão é recalculada mantendo a proporção configurada
```

**Requisitos**: RF-14

---

### H-12 — Garantir a integridade do rateio `[M]` `NÚCLEO` 🔬 **PBT**
**Como** usuário, **quero** que a soma das cotas seja sempre exatamente o valor do gasto,
**para** que nenhum centavo apareça ou desapareça na divisão.

```gherkin
Dado qualquer gasto compartilhado, com qualquer número de membros
Quando as cotas são calculadas ou reconfiguradas
Então a soma das cotas é exatamente igual ao valor total do gasto

Dado que configuro uma divisão manual
Quando a soma das cotas informadas difere do valor total do gasto
Então a operação é rejeitada
E a mensagem indica a diferença encontrada
```

**Requisitos**: RF-15 · **Cobre**: E-02

> 🔬 **Invariante para property-based testing** (RNF-07, regra PBT-03): para qualquer valor e
> qualquer número de membros, `soma(cotas) == valorTotal`. Alvo direto do Kotest Property Testing.

---

### H-13 — Editar gasto compartilhado como qualquer membro `[M]` `NÚCLEO`
**Como** membro de um grupo, **quero** poder corrigir um gasto do grupo mesmo sem tê-lo lançado,
**para** que erros sejam corrigidos por quem perceber primeiro.

```gherkin
Dado um gasto de escopo GRUPO lançado por Rafael
Quando Ana, membro do mesmo grupo, edita esse gasto
Então a alteração é aceita
E a autoria original (Rafael) é preservada

Dado um gasto de escopo GRUPO
Quando um usuário que não é membro do grupo tenta editá-lo
Então a operação é negada
```

**Requisitos**: RF-16

---

### H-14 — Registrar a autoria do lançamento `[M]`
**Como** usuário, **quero** saber quem lançou cada gasto, **para** poder tirar dúvidas sobre um
registro que não reconheço.

**Critérios de aceitação**
- Todo gasto registra quem o lançou
- A autoria é exibida nas consultas de gasto
- A autoria não muda quando outro membro edita o gasto
- A autoria **não** confere privilégio de edição ou exclusão

**Requisitos**: RF-17

---

# ÉPICO E4 — Gastos

Cobre RF-18 a RF-22.

---

### H-15 — Gerenciar gastos `[M]` `NÚCLEO`
**Como** usuário, **quero** registrar, corrigir e apagar gastos, **para** manter o registro do que
gastei.

**Critérios de aceitação**
- Permite cadastrar informando descrição, valor, data e categoria
- Permite associar forma de pagamento: à vista ou cartão de crédito
- Permite editar e excluir, respeitando as regras de permissão de H-13
- Rejeita valor menor ou igual a zero
- Rejeita gasto sem categoria

**Requisitos**: RF-18, RF-19, RF-20

---

### H-16 — Consultar gastos por período `[M]` `NÚCLEO`
**Como** usuário, **quero** consultar meus gastos de um período com filtros, **para** entender para
onde meu dinheiro foi.

**Critérios de aceitação**
- Permite filtrar por intervalo de datas
- Permite filtrar por categoria, por grupo e por escopo
- Retorna apenas o que enxergo, conforme H-03
- Em gastos de grupo, apresenta o valor total e a minha cota

**Requisitos**: RF-21

---

### H-17 — Totalizar gastos consultados `[S]`
**Como** usuário, **quero** ver o total dos gastos consultados, **para** avaliar o período sem
somar manualmente.

**Critérios de aceitação**
- Apresenta o total geral do período consultado
- Apresenta o total por categoria
- Em gastos de grupo, totaliza pela **minha cota**, não pelo valor cheio

**Requisitos**: RF-22

---

# ÉPICO E5 — Cartões de Crédito

Cobre RF-23 a RF-28 e RF-94 a RF-96. Concentra as regras de ciclo de fatura — a segunda área mais
complexa do sistema.

---

### H-18 — Gerenciar cartões `[M]` `NÚCLEO`
**Como** usuário, **quero** cadastrar meus cartões com seus ciclos, **para** que o sistema calcule
sozinho em qual fatura cada compra cai.

**Critérios de aceitação**
- Permite cadastrar informando apelido, dia de fechamento e dia de vencimento
- Permite editar e excluir cartões
- Rejeita dias fora do intervalo válido de 1 a 31
- Não exige limite de crédito — está fora do escopo

**Requisitos**: RF-23

---

### H-19 — Cartão pessoal ou do grupo `[M]` `NÚCLEO`
**Como** usuário, **quero** que um cartão possa pertencer a mim ou a um grupo, **para** refletir
cartões de uso comum.

```gherkin
Dado um cartão cadastrado como pertencente a um usuário
Quando outro usuário tenta consultar sua fatura
Então o acesso é negado

Dado um cartão cadastrado como pertencente ao grupo "Apartamento 42"
Quando qualquer membro desse grupo consulta a fatura
Então a fatura é exibida integralmente
```

**Requisitos**: RF-24 · **Cobre**: E-08

---

### H-20 — Determinar a fatura de competência `[M]` `NÚCLEO`
**Como** usuário, **quero** que o sistema decida sozinho em qual fatura cada compra cai, **para**
que os valores batam com o extrato do banco.

```gherkin
Dado um cartão com fechamento no dia 28
Quando lanço uma compra em 27/07
Então ela cai na fatura de agosto

Dado um cartão com fechamento no dia 28
Quando lanço uma compra em 28/07, o exato dia do fechamento
Então ela cai na fatura de setembro
E o corte é exclusivo: o dia do fechamento já pertence ao ciclo seguinte

Dado um cartão com fechamento no dia 28 e vencimento no dia 5
Quando lanço uma compra em 30/07
Então ela cai na fatura de setembro
E isso vale ainda que a fatura de agosto não tenha vencido
```

**Requisitos**: RF-25, RF-61 · **Resolve**: E-03, D-04 (parcialmente)

> **Ponto ainda aberto**: cartão com fechamento em dia 29, 30 ou 31 em meses mais curtos (E-04).
> Continua adiado para a Functional Design.

---

### H-21 — Consultar a fatura consolidada `[M]` `NÚCLEO`
**Como** usuário, **quero** ver tudo o que compõe a fatura de um mês, **para** conferir antes de
pagar.

**Critérios de aceitação**
- Lista todos os lançamentos e parcelas com competência naquela fatura
- Apresenta o valor total
- Apresenta a data de vencimento
- Indica se a fatura está aberta, fechada ou paga
- Em parcelas, indica a posição (ex.: "3/12")

**Requisitos**: RF-26

---

### H-22 — Fatura aberta acumula novas compras `[M]` `NÚCLEO`
**Como** usuário, **quero** que uma compra nova entre na fatura que ainda está aberta, **para** que
o valor reflita o que já comprei no ciclo.

```gherkin
Dado que a fatura de agosto do Nubank está aberta com R$ 389,90
E o cartão fecha no dia 28
Quando lanço uma compra de R$ 150,00 em 20/07
Então a fatura de agosto passa a somar R$ 539,90

Dado que a fatura de agosto já fechou em 28/07
Quando lanço uma compra em 30/07
Então a fatura de agosto permanece com o mesmo valor
E o valor entra na fatura de setembro
```

**Requisitos**: RF-60

---

### H-23 — Marcar e desmarcar o pagamento da fatura `[M]` `NÚCLEO`
**Como** usuário, **quero** registrar que paguei a fatura — e poder desfazer isso —, **para** saber
o que já quitei e ter saída caso me engane.

```gherkin
Dado uma fatura fechada e em aberto
Quando a marco como paga informando a data do pagamento
Então seu status passa a PAGA
E a data do pagamento fica registrada

Dado uma fatura marcada como paga
Quando desmarco o pagamento
Então seu status volta para EM ABERTO
E a data de pagamento é removida
E passo a poder corrigir lançamentos que a afetam
```

**Requisitos**: RF-27, RF-94

> **RF-94 nasceu desta história**: sem a operação inversa, um lançamento errado numa fatura paga
> ficaria preso para sempre, já que H-24 bloqueia alterações.

---

### H-24 — Proteger fatura paga contra alterações `[M]` `NÚCLEO`
**Como** usuário, **quero** que uma fatura já paga não mude sozinha, **para** que meu histórico
continue batendo com o extrato do banco.

```gherkin
Dado uma fatura marcada como PAGA
Quando tento excluir uma compra que a compõe
Então a operação é bloqueada
E a mensagem orienta a desmarcar o pagamento antes de corrigir

Dado uma fatura marcada como PAGA
Quando lanço uma compra retroativa cuja competência seria essa fatura
Então a operação é bloqueada
E a mensagem orienta a desmarcar o pagamento antes de lançar
```

**Requisitos**: RF-95 · **Resolve**: E-13

---

### H-25 — Lançamento retroativo em fatura fechada `[M]`
**Como** usuário, **quero** poder lançar uma compra que esqueci, mesmo depois de a fatura fechar,
**para** que meu registro fique completo.

```gherkin
Dado que a fatura de agosto fechou em 28/07 e NÃO foi paga
Quando lanço em 05/08 uma compra com data de 20/07
Então a fatura de agosto é reaberta e recalculada
E a compra fica na competência correta, pela data
E o novo valor da fatura reflete a inclusão

Dado que a fatura de agosto fechou e JÁ foi paga
Quando lanço uma compra com data que cairia nela
Então a operação é bloqueada, conforme H-24
```

**Requisitos**: RF-96 · **Resolve**: E-12

---

### H-26 — Consultar faturas futuras `[S]`
**Como** usuário, **quero** ver quanto já está comprometido nos próximos meses, **para** planejar
antes de assumir uma compra nova.

**Critérios de aceitação**
- Permite consultar faturas de meses futuros
- Projeta as parcelas já lançadas que cairão em cada uma
- Indica que são valores projetados, não fechados

**Requisitos**: RF-28

---

# ÉPICO E6 — Compras Parceladas

Cobre RF-29 a RF-35. Concentra as invariantes monetárias mais sensíveis do sistema.

---

### H-27 — Lançar uma compra parcelada `[M]` `NÚCLEO`
**Como** usuário, **quero** lançar uma compra informando o valor da parcela e quantas são,
**para** registrar como a loja me apresentou.

```gherkin
Dado um cartão com fechamento no dia 28
Quando lanço uma compra em 30/07 de 12 parcelas de R$ 100,00
Então o valor total calculado é R$ 1.200,00
E são geradas 12 parcelas
E a parcela 1/12 cai na fatura de setembro
E cada parcela seguinte cai no mês subsequente
```

**Critérios adicionais**
- Rejeita número de parcelas menor que 1
- Rejeita valor de parcela menor ou igual a zero

**Requisitos**: RF-29, RF-30

---

### H-28 — Distribuir o resíduo de centavos `[M]` `NÚCLEO` 🔬 **PBT**
**Como** usuário, **quero** que a divisão em parcelas nunca perca centavos, **para** que a soma
feche com o valor da compra.

```gherkin
Dado uma compra cujo valor total não divide exatamente pelo número de parcelas
Quando as parcelas são geradas
Então as primeiras N-1 parcelas recebem o valor informado
E a última parcela absorve a diferença residual

Dado uma compra de R$ 100,00 em 3 parcelas
Quando as parcelas são geradas
Então os valores são R$ 33,33 / R$ 33,33 / R$ 33,34
E a soma é exatamente R$ 100,00
```

**Requisitos**: RF-31 · **Cobre**: E-01

> 🔬 **Invariante para property-based testing** (RNF-07, regra PBT-03): para qualquer valor e
> qualquer número de parcelas, `soma(parcelas) == valorTotal` e nenhuma parcela é negativa.

---

### H-29 — Garantir a integridade do parcelamento `[M]` `NÚCLEO` 🔬 **PBT**
**Como** usuário, **quero** que a soma das parcelas seja sempre o total da compra, **inclusive
depois de eu editá-la**, para que o valor nunca fique inconsistente.

```gherkin
Dado qualquer compra parcelada
Quando ela é criada ou editada
Então a soma das parcelas é exatamente igual ao valor total da compra

Dado uma compra de 12x R$ 100,00, totalizando R$ 1.200,00
Quando a altero para 10 parcelas de R$ 120,00
Então as 12 parcelas anteriores são descartadas
E 10 novas parcelas são geradas
E a soma continua sendo exatamente R$ 1.200,00
```

**Requisitos**: RF-32

> 🔬 **Invariante para property-based testing**: a igualdade deve valer após qualquer sequência de
> operações de criação e edição.

---

### H-30 — Corrigir uma compra parcelada `[M]` `NÚCLEO`
**Como** usuário, **quero** corrigir uma compra parcelada por inteiro, **para** ajustar um
lançamento errado sem mexer parcela a parcela.

```gherkin
Dado uma compra parcelada já lançada
Quando altero o valor da parcela ou o número de parcelas
Então todas as parcelas são descartadas e regeradas
E as competências de fatura são recalculadas

Dado uma compra parcelada já lançada
Quando tento editar uma parcela individualmente
Então a operação é rejeitada
E a mensagem indica que a edição é sempre da compra inteira
```

**Requisitos**: RF-33

---

### H-31 — Excluir uma compra parcelada `[M]` `NÚCLEO`
**Como** usuário, **quero** excluir uma compra parcelada, **para** remover um lançamento indevido.

```gherkin
Dado uma compra parcelada com 12 parcelas
Quando excluo a compra
Então todas as 12 parcelas são removidas
E as faturas afetadas que não estejam pagas são recalculadas

Dado uma compra parcelada com parcelas em faturas já pagas
Quando tento excluí-la
Então a operação é bloqueada, conforme H-24
```

**Requisitos**: RF-34

---

### H-32 — Identificar a posição da parcela `[S]`
**Como** usuário, **quero** ver "3/12" em cada parcela, **para** saber quanto ainda falta.

**Critérios de aceitação**
- Cada parcela exibe sua posição e o total (ex.: "3/12")
- A identificação aparece na fatura e nas consultas de gasto

**Requisitos**: RF-35

---

# ÉPICO E7 — Categorias

Cobre RF-36 a RF-38.

---

### H-33 — Gerenciar categorias `[M]` `NÚCLEO`
**Como** usuário, **quero** organizar meus gastos por categoria, **para** entender em que áreas
gasto mais.

**Critérios de aceitação**
- Permite criar, renomear e excluir categorias
- Rejeita nome duplicado
- Rejeita nome vazio

**Requisitos**: RF-36

---

### H-34 — Proteger categoria em uso `[S]`
**Como** usuário, **quero** ser impedido de apagar uma categoria com gastos, **para** não perder a
classificação do histórico.

```gherkin
Dado uma categoria com gastos vinculados
Quando tento excluí-la
Então a operação é bloqueada
E a mensagem informa quantos gastos estão vinculados
E me é oferecida a realocação desses gastos para outra categoria
```

**Requisitos**: RF-37 · **Cobre**: E-06

---

### H-35 — Categorias iniciais `[C]`
**Como** usuário novo, **quero** encontrar categorias comuns já criadas, **para** começar a lançar
sem configurar nada.

**Critérios de aceitação**
- No primeiro acesso, um conjunto de categorias comuns é disponibilizado
- As categorias iniciais podem ser renomeadas ou excluídas como quaisquer outras

**Requisitos**: RF-38

---

# ÉPICO E8 — Receitas

Cobre RF-39 a RF-41.

---

### H-36 — Gerenciar receitas `[M]`
**Como** usuário, **quero** registrar o dinheiro que entra, **para** enxergar o quadro completo e
não só as saídas.

**Critérios de aceitação**
- Permite cadastrar informando descrição, valor e data
- Permite editar e excluir
- Rejeita valor menor ou igual a zero
- Receitas são individuais — não são compartilhadas em grupo (premissa P-05)

**Requisitos**: RF-39

---

### H-37 — Consultar receitas por período `[M]`
**Como** usuário, **quero** consultar minhas receitas de um período, **para** conferir o que
entrou.

**Critérios de aceitação**
- Permite filtrar por intervalo de datas
- Apresenta o total do período

**Requisitos**: RF-40

---

### H-38 — Ver o balanço do período `[S]`
**Como** usuário, **quero** ver quanto sobrou no mês, **para** saber se fechei no positivo.

```gherkin
Dado um período com receitas e gastos registrados
Quando consulto o balanço
Então recebo o resultado de receitas menos gastos

Dado que aportei R$ 2.000,00 num objetivo de investimento no período
Quando consulto o balanço
Então o aporte é contabilizado como gasto
E o balanço mede fluxo de caixa, não variação patrimonial
```

**Requisitos**: RF-41, RF-76

> **Semântica declarada** (decisão D-18): investir reduz o resultado do mês, embora o patrimônio
> não diminua. É escolha consciente do usuário e define o significado do indicador.

---

# ÉPICO E9 — Orçamento por Categoria

Cobre RF-42 a RF-44.

---

### H-39 — Definir orçamento por categoria `[M]`
**Como** usuário, **quero** estabelecer um teto mensal por categoria, **para** me disciplinar.

**Critérios de aceitação**
- Permite definir um valor de teto mensal por categoria
- Permite alterar e remover o teto
- Rejeita valor negativo

**Requisitos**: RF-42

---

### H-40 — Acompanhar orçado × realizado `[M]`
**Como** usuário, **quero** comparar o que planejei com o que gastei, **para** corrigir a rota
durante o mês.

```gherkin
Dado um teto de R$ 1.500,00 para "Alimentação" em agosto
E gastos de R$ 1.200,00 nessa categoria no mês
Quando consulto o orçamento de agosto
Então vejo o orçado de R$ 1.500,00
E o realizado de R$ 1.200,00
E o saldo disponível de R$ 300,00
```

**Requisitos**: RF-43

---

### H-41 — Sinalizar estouro de orçamento `[S]`
**Como** usuário, **quero** que categorias estouradas fiquem evidentes, **para** perceber sem
procurar.

```gherkin
Dado um teto de R$ 1.500,00 para "Alimentação"
E gastos de R$ 1.720,00 nessa categoria no mês
Quando consulto o orçamento
Então a categoria é sinalizada como estourada
E o valor excedente de R$ 220,00 é apresentado
```

**Requisitos**: RF-44

---

# ÉPICO E10 — Contas a Pagar

Cobre RF-55 a RF-67. Reúne numa visão única tudo o que tem vencimento: fatura de cartão, PIX,
boleto e fatura de serviço.

---

### H-42 — Gerenciar contas a pagar `[M]` `NÚCLEO`
**Como** usuário, **quero** registrar tudo o que tenho a pagar com sua data própria, **para** não
perder vencimento.

**Critérios de aceitação**
- Permite cadastrar informando descrição, valor, data de vencimento, tipo e categoria
- Suporta os tipos **FATURA_CARTAO**, **PIX**, **BOLETO** e **FATURA_SERVICO**
- Permite editar e excluir
- Cada conta tem sua **própria** data de vencimento, independente das demais
- Rejeita valor menor ou igual a zero

**Requisitos**: RF-55, RF-56

---

### H-43 — Ver tudo o que vence no período `[M]` `NÚCLEO`
**Como** usuário, **quero** uma lista única de vencimentos, **para** planejar os pagamentos do mês
sem consultar quatro lugares.

```gherkin
Dado que tenho em agosto uma fatura de cartão, uma conta de energia,
     um PIX a fazer e um boleto
Quando consulto os vencimentos de agosto
Então vejo os quatro numa lista única
E a lista está ordenada por data de vencimento
E o total do período é apresentado
E cada item indica seu tipo e status
```

**Requisitos**: RF-58

---

### H-44 — Marcar conta como paga `[M]` `NÚCLEO`
**Como** usuário, **quero** registrar o que já paguei, **para** saber o que ainda falta.

**Critérios de aceitação**
- Permite marcar uma conta como **PAGA**, registrando a data do pagamento
- Permite desmarcar o pagamento, voltando a conta para **EM ABERTO**
- Uma conta paga não pode ter seu valor alterado (mesma regra de H-24)

**Requisitos**: RF-57, RF-94, RF-95

---

### H-45 — Fatura de cartão vira conta a pagar `[M]` `NÚCLEO`
**Como** usuário, **quero** que a fatura do cartão apareça sozinha na minha lista de vencimentos,
**para** não precisar cadastrá-la manualmente todo mês.

```gherkin
Dado um cartão com fechamento no dia 28 e vencimento no dia 5
E uma fatura consolidada de R$ 539,90
Quando a fatura fecha em 28/07
Então uma conta a pagar do tipo FATURA_CARTAO é criada automaticamente
E seu valor é R$ 539,90
E seu vencimento é 05/08
E ela aparece na visão de vencimentos junto às demais contas

Dado uma conta a pagar gerada a partir de uma fatura
Quando tento alterar seu valor diretamente
Então a operação é rejeitada
E a mensagem explica que o valor deriva dos lançamentos da fatura
```

**Requisitos**: RF-59 · **Premissa**: P-11 (a conta derivada não é editável diretamente)

---

### H-46 — Cadastrar conta recorrente `[M]`
**Como** usuário, **quero** informar que uma conta se repete, **para** não recadastrá-la todo mês.

**Critérios de aceitação**
- Ao cadastrar uma conta, o sistema pergunta se ela se repete
- Contas recorrentes e avulsas convivem no mesmo modelo
- Para recorrente, permite informar dia de vencimento e frequência
- Frequência suportada no MVP: **mensal** (premissa P-10)

**Requisitos**: RF-62

---

### H-47 — Gerar as ocorrências de uma conta recorrente `[M]`
**Como** usuário, **quero** que cada mês da conta recorrente apareça sozinho, **para** que meus
vencimentos estejam sempre completos.

```gherkin
Dado uma conta recorrente "Aluguel", vencimento dia 5, mensal
Quando consulto os vencimentos de agosto
Então existe uma ocorrência de "Aluguel" com vencimento em 05/08

Dado uma conta recorrente com ocorrências já geradas
Quando marco uma ocorrência como paga
Então as demais ocorrências permanecem inalteradas
E cada mês tem seu próprio status de pagamento
```

**Requisitos**: RF-63

> **Ponto em aberto**: o mecanismo de geração — job agendado, cálculo derivado na consulta, ou
> híbrido — é a decisão **D-19**, adiada para a Functional Design. Esta história especifica o
> comportamento observável, não a implementação.

---

### H-48 — Ajustar o valor no pagamento `[M]`
**Como** usuário, **quero** corrigir o valor de uma conta recorrente na hora de pagar, **para**
refletir contas que variam, como energia e gás.

```gherkin
Dado uma conta recorrente "Energia" com valor base de R$ 180,00
Quando a conta de agosto chega no valor de R$ 213,40
E ajusto o valor da ocorrência antes de marcá-la como paga
Então a ocorrência de agosto registra R$ 213,40
E o valor base da conta recorrente permanece R$ 180,00
E as ocorrências futuras continuam usando o valor base
```

**Requisitos**: RF-64

---

### H-49 — Conta a pagar compartilhada no grupo `[M]`
**Como** usuário, **quero** marcar uma conta como do grupo, **para** que a divisão do aluguel e da
energia siga as mesmas regras dos gastos.

**Critérios de aceitação**
- Permite marcar uma conta com escopo **PESSOAL** ou **GRUPO**
- Contas de escopo GRUPO seguem as mesmas regras de visibilidade e rateio dos gastos
  (H-09 a H-13)
- Qualquer membro do grupo pode editar ou excluir uma conta de grupo

**Requisitos**: RF-65

---

### H-50 — Consultar contas a vencer e vencidas `[S]`
**Como** usuário, **quero** ver o que está próximo de vencer e o que já venceu sem pagamento,
**para** agir antes de tomar juros.

**Critérios de aceitação**
- Permite consultar contas a vencer num horizonte configurável (ex.: 7 ou 30 dias)
- Identifica contas com vencimento passado e status EM ABERTO
- Ordena por proximidade do vencimento

**Requisitos**: RF-66

---

### H-51 — Encerrar uma conta recorrente `[S]`
**Como** usuário, **quero** parar de gerar ocorrências de uma conta que não tenho mais, **para**
que meus vencimentos futuros fiquem corretos.

```gherkin
Dado uma conta recorrente ativa
Quando a encerro
Então nenhuma nova ocorrência é gerada
E as ocorrências já existentes são preservadas com seu histórico de pagamento
```

**Requisitos**: RF-67

---

# ÉPICO E11 — Investimentos

Cobre RF-68 a RF-77. Objetivos nomeados onde o usuário guarda dinheiro com propósito.

---

### H-52 — Gerenciar objetivos de investimento `[M]`
**Como** usuário, **quero** criar bolsos nomeados para guardar dinheiro, **para** separar o que é
da viagem do que é reserva.

**Critérios de aceitação**
- Permite criar objetivo informando um nome (ex.: "Viagem", "Geral")
- Permite renomear e excluir
- Rejeita nome vazio ou duplicado

**Requisitos**: RF-68

---

### H-53 — Registrar aportes `[M]`
**Como** usuário, **quero** registrar quanto coloquei em cada objetivo, **para** acompanhar o
quanto já juntei.

**Critérios de aceitação**
- Permite registrar aporte informando valor e data
- Acumula o total aportado no objetivo
- Rejeita valor menor ou igual a zero

**Requisitos**: RF-69, RF-70

---

### H-54 — Atualizar o saldo à mão `[M]`
**Como** usuário, **quero** informar o saldo real do meu investimento, **para** refletir o
rendimento sem que o sistema precise conhecer meus ativos.

```gherkin
Dado um objetivo com R$ 6.000,00 aportados
Quando consulto o extrato do banco e vejo R$ 6.240,00
E atualizo o saldo atual para esse valor
Então o objetivo passa a apresentar saldo de R$ 6.240,00
E o total aportado permanece R$ 6.000,00
```

**Requisitos**: RF-71

---

### H-55 — Ver o rendimento do objetivo `[M]`
**Como** usuário, **quero** saber quanto meu dinheiro rendeu, **para** avaliar se o investimento
vale a pena.

```gherkin
Dado um objetivo com R$ 6.000,00 aportados e saldo atual de R$ 6.240,00
Quando consulto o objetivo
Então o rendimento apresentado é R$ 240,00

Dado um objetivo com R$ 6.000,00 aportados e saldo atual de R$ 5.800,00
Quando consulto o objetivo
Então o rendimento apresentado é −R$ 200,00
E o valor negativo é exibido normalmente, não rejeitado
```

**Requisitos**: RF-72 · **Cobre**: E-14

> **Nota**: rendimento negativo pode indicar prejuízo **ou** uma retirada não registrada — o sistema
> não distingue os dois casos, já que resgate está fora do escopo (premissa P-08).

---

### H-56 — Definir meta e acompanhar progresso `[M]`
**Como** usuário, **quero** estabelecer um alvo para o objetivo, **para** saber o quanto falta.

```gherkin
Dado um objetivo "Viagem" com meta de R$ 15.000,00 e saldo de R$ 6.240,00
Quando consulto o objetivo
Então o progresso apresentado é 41,6%
E o valor restante apresentado é R$ 8.760,00

Dado um objetivo "Geral" sem meta definida
Quando consulto o objetivo
Então o saldo é apresentado sem progresso nem valor restante
E a ausência de meta não é tratada como erro
```

**Requisitos**: RF-73 · **Cobre**: E-15 (objetivo com prazo vencido é sinalizado, sem bloquear
novos aportes)

---

### H-57 — Definir prazo e calcular o aporte necessário `[M]`
**Como** usuário, **quero** saber quanto preciso guardar por mês, **para** chegar na meta dentro do
prazo.

```gherkin
Dado um objetivo com meta de R$ 15.000,00, saldo de R$ 6.240,00
     e prazo alvo em julho/2027
E faltam 11 meses para o prazo
Quando consulto o objetivo
Então o aporte mensal necessário apresentado é R$ 796,36

Dado um objetivo cujo prazo alvo já passou sem a meta atingida
Quando consulto o objetivo
Então ele é sinalizado como atrasado
E novos aportes continuam permitidos
```

**Requisitos**: RF-74 · **Cobre**: E-15

---

### H-58 — Objetivo compartilhado no grupo `[M]`
**Como** usuário, **quero** criar um objetivo do grupo, **para** que todos aportem e acompanhem
juntos.

```gherkin
Dado um objetivo "Reforma" pertencente ao grupo "Apartamento 42"
Quando qualquer membro do grupo registra um aporte
Então o aporte é somado ao total do objetivo
E todos os membros enxergam o saldo e o progresso atualizados
E cada aporte registra quem o fez
```

**Requisitos**: RF-75 · **Cobre**: E-16 (membro que sai do grupo tem o histórico de aportes
preservado)

---

### H-59 — Aporte entra no balanço como gasto `[M]`
**Como** usuário, **quero** que o dinheiro que guardo apareça como saída do mês, **para** que meu
balanço reflita o que sobrou de fato.

```gherkin
Dado um mês com R$ 8.000,00 de receitas e R$ 5.000,00 de gastos
Quando aporto R$ 2.000,00 num objetivo de investimento
E consulto o balanço do mês
Então o total de gastos considerado é R$ 7.000,00
E o balanço do mês é R$ 1.000,00
```

**Requisitos**: RF-76

---

### H-60 — Ver a posição consolidada `[S]`
**Como** usuário, **quero** ver todos os meus objetivos de uma vez, **para** saber quanto tenho
guardado ao todo.

**Critérios de aceitação**
- Apresenta, para todos os objetivos, o total aportado, o saldo atual e o rendimento agregado
- Inclui objetivos pessoais e de grupo dos quais participo
- Indica o escopo de cada objetivo

**Requisitos**: RF-77

---

# JORNADAS TRANSVERSAIS

Fluxos que atravessam **três ou mais áreas**, conforme o critério definido no plano. Existem para
capturar as costuras entre épicos — é onde os vazios de especificação costumam aparecer.

---

### J-01 — Compra parcelada em cartão de grupo, com rateio `[M]` `NÚCLEO`
**Áreas**: Cartões + Parcelamento + Grupo + Rateio

**Como** membro de um grupo, **quero** lançar uma compra parcelada num cartão do grupo e dividi-la
entre os membros, **para** que cada um saiba quanto lhe cabe em cada mês.

```gherkin
Dado o grupo "Apartamento 42" com Rafael e Ana
E um cartão "Conta Casa" pertencente a esse grupo, fechamento dia 28
Quando Rafael lança em 30/07 uma compra de 10 parcelas de R$ 120,00
E marca o escopo como GRUPO
E configura a divisão em 70% para si e 30% para Ana
Então o valor total é R$ 1.200,00
E são geradas 10 parcelas, começando na fatura de setembro
E a cota de Rafael em cada parcela é R$ 84,00
E a cota de Ana em cada parcela é R$ 36,00
E a soma das cotas de cada parcela é exatamente o valor da parcela
E Ana enxerga a compra e suas cotas nas faturas futuras
E qualquer um dos dois pode corrigir a compra por inteiro
```

**Requisitos**: RF-11, RF-14, RF-15, RF-24, RF-25, RF-29, RF-30, RF-32

> **Costura exposta por esta jornada**: o rateio precisa incidir **sobre cada parcela**, não apenas
> sobre o total da compra — do contrário Ana não saberia quanto lhe cabe em cada mês. A invariante
> de H-12 passa a valer em dois níveis: soma das cotas por parcela, e soma das parcelas por compra.
> Ponto de atenção para a Application Design.

---

### J-02 — Fechar o mês `[M]` `NÚCLEO`
**Áreas**: Contas a Pagar + Cartões + Gastos + Orçamento

**Como** usuário, **quero** revisar tudo o que devo, pagar, e conferir se estourei o orçamento,
**para** encerrar o mês sabendo como foi.

```gherkin
Dado o mês de agosto com faturas, contas e gastos lançados
Quando consulto a visão de vencimentos de agosto
Então vejo, ordenados por data: a fatura do cartão gerada automaticamente,
     a conta de energia recorrente, o PIX e o boleto
E vejo o total do período

Quando confiro a fatura do cartão
Então vejo os lançamentos e parcelas que a compõem

Quando marco a fatura e as contas como pagas
Então cada uma registra sua data de pagamento
E deixam de aparecer como pendentes

Quando consulto o orçamento de agosto
Então vejo o realizado por categoria contra o teto definido
E as categorias estouradas estão sinalizadas
```

**Requisitos**: RF-26, RF-27, RF-43, RF-44, RF-57, RF-58, RF-59

> **Costura exposta**: o "realizado" do orçamento precisa de uma definição explícita — conta o gasto
> pela **data da compra** ou pela **competência da fatura**? Uma compra de 30/07 no cartão aparece
> no orçamento de julho ou de setembro? **Ponto em aberto para a Functional Design.**

---

### J-03 — Entrar num grupo e começar a compartilhar `[S]`
**Áreas**: Grupo + Rateio + Gastos + Cartões

**Como** pessoa recém-adicionada a um grupo, **quero** entender o que já existe e começar a
participar, **para** me integrar às despesas comuns.

```gherkin
Dado o grupo "Apartamento 42", ativo desde janeiro, com gastos e um cartão próprio
Quando sou adicionado ao grupo em agosto
Então enxergo todo o histórico de gastos do grupo, de janeiro em diante
E não possuo cota nos gastos anteriores à minha entrada
E passo a enxergar a fatura do cartão do grupo

Quando lanço um gasto de escopo GRUPO
Então a divisão igual passa a considerar todos os membros atuais, inclusive eu

Quando consulto meus totais pessoais
Então eles refletem apenas minhas cotas, a partir da minha entrada
```

**Requisitos**: RF-08, RF-09, RF-11, RF-13, RF-24

> **Costura exposta**: as consultas de "total do grupo" e "meus totais" divergem para um membro
> recém-adicionado. A API precisa deixar essa diferença explícita, e o front precisa comunicá-la —
> caso contrário o usuário verá dois números diferentes sem entender o motivo.

---

# MATRIZ DE RASTREABILIDADE

Cobertura dos 67 requisitos de domínio. Requisitos de infraestrutura (RF-45 a RF-54), contrato de
API (RF-78 a RF-80) e CI/CD (RF-81 a RF-93) não são cobertos por histórias, conforme decidido no
`user-stories-assessment.md`.

| Requisito | Histórias |
|---|---|
| RF-01 | H-01 |
| RF-02 | H-02 |
| RF-03 | H-03 |
| RF-04 | H-03 |
| RF-05 | H-04 |
| RF-06 | H-05 |
| RF-07 | H-05 |
| RF-08 | H-06, J-03 |
| RF-09 | H-07, J-03 |
| RF-10 | H-08 |
| RF-11 | H-09, J-01, J-03 |
| RF-13 | H-10, J-03 |
| RF-14 | H-11, J-01 |
| RF-15 | H-12, J-01 |
| RF-16 | H-13 |
| RF-17 | H-14 |
| RF-18 | H-15 |
| RF-19 | H-15 |
| RF-20 | H-15 |
| RF-21 | H-16 |
| RF-22 | H-17 |
| RF-23 | H-18 |
| RF-24 | H-19, J-01, J-03 |
| RF-25 | H-20, J-01 |
| RF-26 | H-21, J-02 |
| RF-27 | H-23, J-02 |
| RF-28 | H-26 |
| RF-29 | H-27, J-01 |
| RF-30 | H-27, J-01 |
| RF-31 | H-28 |
| RF-32 | H-29, J-01 |
| RF-33 | H-30 |
| RF-34 | H-31 |
| RF-35 | H-32 |
| RF-36 | H-33 |
| RF-37 | H-34 |
| RF-38 | H-35 |
| RF-39 | H-36 |
| RF-40 | H-37 |
| RF-41 | H-38 |
| RF-42 | H-39 |
| RF-43 | H-40, J-02 |
| RF-44 | H-41, J-02 |
| RF-55 | H-42 |
| RF-56 | H-42 |
| RF-57 | H-44, J-02 |
| RF-58 | H-43, J-02 |
| RF-59 | H-45, J-02 |
| RF-60 | H-22 |
| RF-61 | H-20 |
| RF-62 | H-46 |
| RF-63 | H-47 |
| RF-64 | H-48 |
| RF-65 | H-49 |
| RF-66 | H-50 |
| RF-67 | H-51 |
| RF-68 | H-52 |
| RF-69 | H-53 |
| RF-70 | H-53 |
| RF-71 | H-54 |
| RF-72 | H-55 |
| RF-73 | H-56 |
| RF-74 | H-57 |
| RF-75 | H-58 |
| RF-76 | H-38, H-59 |
| RF-77 | H-60 |
| RF-94 | H-23, H-44 |
| RF-95 | H-24, H-44 |
| RF-96 | H-25 |

**Cobertura**: 70/70 requisitos de domínio (67 originais + RF-94 a RF-96). ✅ Sem lacunas.

## Cobertura dos casos de borda

| Caso | História |
|---|---|
| E-01 (resíduo de centavos) | H-28 |
| E-02 (soma das cotas divergente) | H-12 |
| E-03 (dia exato do fechamento) | H-20 |
| E-04 (fechamento dia 29–31) | ⏳ Em aberto — D-04, Functional Design |
| E-05 (membro sai com gastos abertos) | H-08 |
| E-06 (categoria com gastos vinculados) | H-34 |
| E-07 (usuário inexistente no grupo) | H-06 |
| E-08 (fatura de outro grupo) | H-19 |
| E-09 (escopo GRUPO sem grupo) | H-09 |
| E-10 (membro entra com histórico) | H-07, J-03 |
| E-11 (recorrente dia 29–31) | ⏳ Em aberto — Functional Design |
| E-12 (retroativo em fatura fechada) | H-25 |
| E-13 (alteração em fatura paga) | H-24 |
| E-14 (rendimento negativo) | H-55 |
| E-15 (prazo vencido sem meta) | H-56, H-57 |
| E-16 (membro sai do objetivo de grupo) | H-58 |

---

# RESUMO

## Contagem

| | |
|---|---|
| Épicos | 11 |
| Histórias | 60 (H-01 a H-60) |
| Jornadas transversais | 3 (J-01 a J-03) |
| **Total** | **63** |

## Por prioridade

| Prioridade | Quantidade |
|---|---|
| `[M]` Must | 48 |
| `[S]` Should | 14 |
| `[C]` Could | 1 |

## Núcleo mínimo utilizável

**30 histórias** marcadas como `NÚCLEO` — o primeiro corte que entrega um sistema usável:
identidade, grupos, compartilhamento com rateio, gastos, cartões com ciclo de fatura,
parcelamento, categorias e contas a pagar.

**Fora do núcleo**: receitas (E8), orçamento (E9), investimentos (E11) e as histórias `[S]`/`[C]`
dos demais épicos. São candidatos naturais a uma segunda unidade de trabalho.

## Histórias com invariante para property-based testing 🔬

Alvos diretos do Kotest Property Testing (RNF-07, regra PBT-03):

| História | Invariante |
|---|---|
| H-12 | `soma(cotas) == valorTotal` — para qualquer valor e número de membros |
| H-28 | `soma(parcelas) == valorTotal` — para qualquer valor e número de parcelas |
| H-29 | A igualdade se mantém após qualquer sequência de criação e edição |

J-01 revela que essas invariantes se compõem: numa compra parcelada e rateada, a soma das cotas
deve fechar **por parcela**, e a soma das parcelas deve fechar **por compra**.

## Pontos em aberto levantados pelas histórias

| # | Questão | Destino |
|---|---|---|
| 1 | Cartão com fechamento em dia 29–31 e meses curtos (E-04) | D-04 — Functional Design |
| 2 | Conta recorrente com vencimento em dia 29–31 (E-11) | Functional Design |
| 3 | Mecanismo de geração de ocorrências recorrentes (H-47) | D-19 — Functional Design |
| 4 | Mecanismo de fechamento automático da fatura (H-45) | D-20 — Functional Design |
| 5 | **O "realizado" do orçamento conta pela data da compra ou pela competência da fatura?** (J-02) | 🆕 Novo — Functional Design |
| 6 | **Rateio incide sobre cada parcela, não só sobre o total** (J-01) | 🆕 Novo — Application Design |
| 7 | **API deve distinguir "total do grupo" de "minhas cotas"** (J-03) | 🆕 Novo — Application Design |

> Os itens 5, 6 e 7 **não existiam antes desta stage** — foram descobertos ao escrever as jornadas
> transversais. É a justificativa prática de por que o formato híbrido foi escolhido.
