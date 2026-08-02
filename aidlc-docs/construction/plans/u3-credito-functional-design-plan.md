# Plano de Functional Design — U3 Crédito

> Fonte única de verdade da Functional Design de U3. Cada passo tem checkbox, marcado no mesmo
> momento em que o trabalho é concluído.
>
> **Este arquivo nasce sem seção de respostas.** Ela é criada depois de as respostas existirem —
> correção estrutural a O-32, adotada desde a NFR Design de U2.

---

## 1. Contexto da unidade

| | |
|---|---|
| **Componentes** | `cartao`, `fatura`, `conta`, `compra`, `gasto` (integração com cartão) |
| **Entidades** | `Cartao`, `Fatura`, `Compra`, `Parcela`, `ContaAPagar`, `ContaRecorrente` |
| **Histórias** | H-18 a H-32, H-42 a H-51 (25) + J-01 e J-03 (2 jornadas) |
| **Requisitos** | RF-23 a RF-35, RF-55 a RF-67, RF-94 a RF-96 (31) |
| **Depende de** | U1 e U2 — ambas entregues, aprovadas e verdes no CI |
| **Bloqueia** | U4 (parcialmente — apenas J-02) |

**A maior unidade do sistema.** Mais entidades que U1 e U2 somadas, e as duas invariantes
monetárias que restam. Concentra **4 das 6 decisões ainda em aberto**.

### 1.1 O que U3 herda e não reescreve

| De U1 | De U2 |
|---|---|
| `Dinheiro`, `Competencia`, `Escopo` | `Gasto` com `cartao` e `competencia` **já nuláveis** — sem `ALTER TABLE` |
| `RepositorioComVisibilidade` + `CriterioVisibilidade` | O padrão de **porta por feature** com filtro obrigatório (D-63) |
| `ContextoUsuario.criterio()` | A **projeção de leitura** (D-65) |
| `ErroHandler`, `CodigoErro` | O **`ArquiteturaTest`** (D-66) — já reprova as 6 entidades novas se escaparem |
| | A regra ***o banco pode somar; dividir, nunca*** |

> A última linha deixa de ser conselho nesta unidade. `dividirEm` — **se** for usado, ver §3 Q1 — é
> o único lugar do sistema onde dinheiro é dividido, e a divisão tem resíduo com regra própria.

### 1.2 As 4 decisões que fecham aqui

| ID | Aberta desde | Assunto |
|---|---|---|
| **D-04** | Requirements Analysis | Fronteira do fechamento em cartão que fecha dia 29, 30 ou 31 (E-04) |
| **D-19** | Requirements Analysis | Mecanismo de geração das ocorrências recorrentes (H-47) |
| **D-20** | Requirements Analysis | Mecanismo de fechamento de fatura |
| **D-33** | Application Design | `Fatura.status = PAGA` é persistido ou derivado da `ContaAPagar`? |

---

## 2. Passos

### Análise

- [x] **Passo 1** — Ler a definição de U3, as 25 histórias, as 2 jornadas e os 31 requisitos
- [x] **Passo 2** — Ler o contrato herdado de U1 e U2 e os métodos da Application Design
- [x] **Passo 3** — Levantar as ambiguidades e **as contradições entre requisitos** (§3)

### Esclarecimento

- [x] **Passo 4** — Formular as questões
- [x] **Passo 5** — Coletar as respostas via AskUserQuestion e criar a §5 com elas
- [x] **Passo 6** — Reanalisar em busca de contradição ou ambiguidade residual

### Modelagem

- [x] **Passo 7** — `domain-entities.md`: as 6 entidades, com atributos, nulabilidade, invariantes e
      o que **não** é atributo
- [x] **Passo 8** — `business-rules.md`: regras `RN-K*` (cartão), `RN-F*` (fatura), `RN-P*` (compra e
      parcela), `RN-A*` (conta a pagar), `RN-R*` (recorrência)
- [x] **Passo 9** — `business-logic-model.md`: o **algoritmo da competência**, o ciclo de vida da
      fatura, o parcelamento, a geração da conta a pagar e a visão de vencimentos
- [x] **Passo 10** — Modelar o **bloqueio de fatura paga** (RF-95) como regra transversal, invocada
      por `gasto`, `compra` e `conta`
- [x] **Passo 11** — Resolver as jornadas **J-01** e **J-03**
- [x] **Passo 12** — Diagramas Mermaid, validados antes da escrita

### Verificação

- [x] **Passo 13** — Rastreabilidade das 25 histórias e das 2 jornadas
- [x] **Passo 14** — Mapear os alvos de property-based testing
- [x] **Passo 15** — Registrar as decisões novas (numeração continua de D-66) e **fechar D-04, D-19,
      D-20 e D-33**

---

## 3. Questões

> Todas feitas via AskUserQuestion, conforme a preferência registrada do usuário.

### Q1 — A contradição de três pontas no parcelamento

**É o achado mais importante desta análise, e ele muda o escopo da unidade.** Três documentos
aprovados dizem coisas incompatíveis sobre a mesma operação:

| Fonte | O que diz |
|---|---|
| **RF-29 / H-27** e o comando `LancarCompraParcelada(valorParcela, numeroParcelas)` | A entrada é **valor da parcela × quantidade**. O total é calculado: 12 × R$ 100,00 = R$ 1.200,00 |
| **RF-31 / H-28 / E-01** | Descreve dividir um **total** por N com resíduo: *"R$ 100,00 em 3 → 33,33 / 33,33 / 33,34"* |
| **`Dinheiro.dividirEm`**, corrigido em U1 | Distribui o resíduo **um centavo por parte, nas últimas** — o que **contradiz** o "última parcela absorve" de RF-31 |

Se a entrada é sempre (valor da parcela, N), **não há divisão e não há resíduo**: 12 × 100,00 dá
1.200,00 exatamente, sempre. H-28, RF-31 e E-01 ficariam **sem objeto** — e `dividirEm`, a função
cujo defeito o property-based testing encontrou em U1, ficaria **sem consumidor algum**.

Se existe também entrada por valor total, aí há divisão — e aí é preciso escolher qual das duas
regras de resíduo vale, porque as duas estão escritas e são diferentes.

### Q2 — Cartão que fecha dia 29, 30 ou 31 (E-04, D-04)

Aberta desde a Requirements Analysis. Um cartão que fecha dia 31 não tem dia 31 em fevereiro.
A mesma pergunta vale para o vencimento de conta recorrente (E-11), de natureza idêntica.

### Q3 — `Fatura.status = PAGA` é persistido ou derivado? (D-33)

RF-59 gera uma `ContaAPagar` no fechamento; RF-27 diz que marcar a fatura como paga *"equivale a
quitar a conta a pagar correspondente"*. Se o status for persistido nos dois lugares, há duas
fontes de verdade para o mesmo fato — e elas podem divergir.

### Q4 — Quando a fatura fecha? (D-20)

Nenhum requisito diz **o que dispara** o fechamento. H-22 exige que uma fatura aberta acumule
compras novas; H-45 exige que o fechamento gere a conta a pagar. Entre os dois, alguém precisa
decidir que chegou o dia.

### Q5 — Como as ocorrências recorrentes existem? (D-19)

H-47 diz que ao consultar agosto a ocorrência de "Aluguel" está lá. H-48 exige ajustar o valor de
uma ocorrência específica sem mexer nas outras — o que implica que a ocorrência tem **identidade e
estado próprios**. A pergunta é quando ela passa a existir.

---

## 5. Respostas

*Criada depois de as respostas existirem.*

| | Resposta | Decisão |
|---|---|---|
| **Q1** | **Só valor total ÷ quantidade.** A entrada é o valor total da compra e o número de parcelas | **D-67** |
| **Q2** | **Última parcela absorve o resíduo inteiro** — confirmado numa segunda rodada, com os números à vista | **D-68** |
| **Q3** | **Último dia do mês** quando o dia não existe. Vale para fechamento, vencimento e recorrência | **D-69** — fecha **D-04** e resolve E-04 e E-11 |
| **Q4** | **Derivado da `ContaAPagar`.** A fatura tem ABERTA/FECHADA; PAGA vem da conta | **D-70** — fecha **D-33** |
| **Q5** | **Job agendado diário** fecha as faturas do dia | **D-71** — fecha **D-20** |
| **Q6** | **Materializa ao ser tocada.** A consulta projeta; a linha nasce quando ganha estado próprio | **D-72** — fecha **D-19** |

### 5.1 Reanálise (Passo 6)

**Q1 inverteu o comando desenhado na Application Design.** `LancarCompraParcelada` tinha
`valorParcela: Dinheiro`; passa a ter `valorTotal: Dinheiro`. É divergência deliberada de um
artefato aprovado, e está registrada como tal — junto com D-57, que já havia divergido do
`openapi.yaml`.

**Consequência de Q1 sobre RF-29 e H-27**: o texto de ambos diz *"informando valor da parcela e
número de parcelas, calculando o valor total"*. Com D-67 é o inverso. **RF-29 e H-27 ficam
desatualizados** e precisam de correção nos requisitos — não é reinterpretação, é mudança.

**Q2 reverte uma correção de U1, e a reversão foi confirmada explicitamente.** O `Dinheiro.dividirEm`
distribui hoje um centavo por parte, nas últimas — comportamento adotado em U1 **porque o
property-based testing encontrou o defeito da regra original** (research-log 3.36, O-28). D-68
manda voltar ao "última absorve".

O que isso implica, escrito para que ninguém precise reconstruir depois:

| Item | Efeito |
|---|---|
| `Dinheiro.dividirEm` | Alterado na Code Generation de U3 |
| `DinheiroPropriedadesTest` | A propriedade *"partes diferem no máximo 0,01"* **passa a ser falsa** e é reescrita |
| O exemplo canônico | **Não muda**: R$ 100,00 em 3 continua 33,33 / 33,33 / 33,34 |
| R$ 100,00 em 7 | Passa a dar 6× 14,28 + 14,32, em vez de 3× 14,28 + 4× 14,29 |
| R$ 1,19 em 120 | Passa a dar 119 parcelas de R$ 0,00 e uma de R$ 1,19 |
| O-28 no research-log | **Permanece válido como observação metodológica** — o exemplo continua tendo escondido a diferença. O que muda é a regra escolhida, não o fato de o exemplo ser insuficiente para distingui-las |

A decisão é do usuário, foi apresentada duas vezes com os números concretos e reafirmada. Segue
registrada com a consequência ao lado, conforme O-36.

**Q5 cria o primeiro componente agendado do sistema, e isso tem preço.** A NFR Design de U1 listou
"fila / job / agendador" na tabela do que **deliberadamente não existe**. D-71 acrescenta um.

| Consequência | Registro |
|---|---|
| A tabela de U1 precisa de uma linha nova, agora com justificativa oposta | Passo 15 |
| Duas instâncias fechariam a mesma fatura duas vezes | É a **segunda** coisa do sistema que quebra com escala horizontal, ao lado do `RegistroDeTentativas`. Vai para a NFR Design de U3 |
| Se o job não rodar, a fatura não fecha e a conta a pagar não nasce | Modo de falha novo, silencioso. Precisa de tratamento no Passo 9 — a alternativa recusada (sob demanda) não tinha esse modo |

**Q4 e Q6 se combinam bem.** As duas escolhem *derivar em vez de duplicar*: o status da fatura vem
da conta, e a ocorrência recorrente vem da regra. Nos dois casos, a linha só existe quando tem
estado próprio a guardar.

**Nenhuma contradição entre as respostas.**

---

## 4. Riscos identificáveis antes das respostas

| Risco | Tratamento |
|---|---|
| **A contradição de Q1 não ser resolvida e virar código ambíguo** | É a primeira questão por isso. Nenhuma linha de parcelamento é escrita antes da resposta |
| O bloqueio de fatura paga (RF-95) ser esquecido em algum dos três chamadores | Modelado como **regra transversal** no Passo 10, com teste por chamador — `gasto`, `compra` e `conta` |
| 6 entidades novas escaparem do padrão de visibilidade | O `ArquiteturaTest` (D-66) já reprova o build automaticamente. É o primeiro retorno concreto de U2 |
| A fatura ter estado derivado **e** persistido, divergindo | Objeto de Q3 |
| Recálculo de fatura entrar em laço com a conta a pagar que ela gera | A gerar/recalcular é operação de mão única: fatura → conta. A conta nunca altera a fatura. Verificar no Passo 9 |
| A unidade ser grande demais para uma Code Generation só | Avaliar ao fim da Functional Design. Dividir a stage de código em duas entregas é preferível a um plano de 50 passos |
