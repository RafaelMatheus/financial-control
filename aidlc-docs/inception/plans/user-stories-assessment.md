# User Stories Assessment

**Stage**: INCEPTION - User Stories - Part 1, Step 1 (validação obrigatória)
**Timestamp**: 2026-07-30T16:11:59Z

---

## Request Analysis

- **Original Request**: *"Gostaria de construir um sistema de controle de gastos financeiros que me
  permita cadastrar gastos e parcelas de cartão de crédito"* — ampliado ao longo de 6 revisões para
  um sistema multi-usuário com grupos, rateio configurável, contas a pagar, investimentos, IaC e
  pipeline CI/CD.
- **User Impact**: **Direto**. Praticamente todo o escopo é funcionalidade que o usuário final
  opera diretamente pela API (e, adiante, pelo front-end em outro repositório).
- **Complexity Level**: **Complex**. 92 requisitos funcionais ativos, 10+ agregados de domínio,
  regras de rateio configurável, cálculo de competência de fatura a partir do ciclo do cartão,
  motor de recorrência e aritmética monetária com resíduo.
- **Stakeholders**: o usuário como product owner e desenvolvedor do backend; ele próprio (ou outra
  pessoa) como desenvolvedor do front-end em repositório separado, consumindo a API; os membros de
  grupo como usuários finais com permissões e visibilidades distintas.

---

## Assessment Criteria Met

### ✅ High Priority (execução obrigatória) — 4 critérios atendidos

- [x] **New User Features** — praticamente todo o escopo é funcionalidade nova voltada ao usuário:
      gastos, grupos, cartões, parcelamento, contas a pagar, investimentos, orçamento.
- [x] **Multi-Persona Systems** — os requisitos já revelam papéis com direitos diferentes: quem cria
      o grupo e administra membros (RF-06, RF-08), quem participa e enxerga gastos compartilhados
      (RF-09, RF-16), e o proprietário do cartão — que pode ser um usuário **ou um grupo** (RF-24).
- [x] **Customer-Facing APIs** — a API é consumida por um front-end em **outro repositório**
      (RNF-08, RF-78). O contrato precisa ser compreensível por quem não participou desta análise.
- [x] **Complex Business Logic** — múltiplos cenários por regra. Exemplos: rateio com divisão igual,
      por percentual ou por valor absoluto (RF-13, RF-14); competência de fatura dependente do dia
      de fechamento com janela entre fechamento e vencimento (RF-25, RF-61); resíduo de centavos no
      parcelamento (RF-31); contas recorrentes com valor ajustável no pagamento (RF-64).

### ✅ Medium Priority — 2 critérios adicionais

- [x] **Security Enhancements** — autenticação, isolamento de dados por usuário e permissões de
      grupo são requisitos funcionais (RF-01 a RF-05, RF-16), com a extensão Security desligada.
      Histórias ajudam a explicitar quem enxerga o quê em cada situação.
- [x] **Data Changes** — o modelo de dados nasce inteiro nesta iteração; toda decisão de modelagem
      afeta o que o usuário consegue consultar e registrar.

### Complexity Assessment Factors

- [x] **Scope** — o escopo cruza múltiplos componentes e pontos de contato do usuário
- [x] **Ambiguity** — restam casos de borda sem tratamento definido: E-03 (compra no exato dia do
      fechamento), E-10 (membro que entra num grupo com histórico), E-12 (lançamento retroativo em
      fatura fechada), E-13 (exclusão de compra após a fatura virar conta a pagar)
- [x] **Risk** — alto impacto: erro de rateio ou de competência de fatura produz valores incorretos
      de dinheiro, com efeito entre pessoas
- [x] **Testing** — os critérios de aceitação das histórias alimentam diretamente os testes de
      exemplo que complementam o PBT (regra PBT-10, modo advisory)
- [x] **Options** — existem múltiplas implementações válidas para pontos ainda abertos (D-13, D-19,
      D-20)

### Critérios de exclusão — nenhum se aplica

- [ ] ~~Pure Refactoring~~ — não há refatoração; é domínio novo
- [ ] ~~Isolated Bug Fixes~~ — não há bugs a corrigir
- [ ] ~~Infrastructure Only~~ — há infraestrutura no escopo (RF-45 a RF-54, RF-81 a RF-93), mas ela
      é uma **parte** do escopo, não o todo. As histórias cobrirão o domínio; os requisitos de
      infraestrutura e CI/CD seguem tratados como requisitos técnicos, sem histórias de usuário
- [ ] ~~Developer Tooling~~ — não se aplica
- [ ] ~~Documentation~~ — não se aplica

---

## Decision

**Execute User Stories**: ✅ **Sim**

**Reasoning**: quatro critérios de alta prioridade são atendidos de forma inequívoca — qualquer um
deles isoladamente já obrigaria a execução. O caso decisivo é a combinação de **múltiplas personas**
com **lógica de negócio de dinheiro compartilhado**: os requisitos descrevem *o que* o sistema deve
fazer, mas não deixam explícito *quem* faz cada coisa e *o que cada papel enxerga* em cada situação.

Um exemplo concreto da lacuna: RF-16 permite que **qualquer membro** do grupo edite um gasto de
escopo GRUPO. Combinado com RF-24 (cartão pode pertencer a um grupo) e RF-27 (marcar fatura como
paga), surge a pergunta que nenhum requisito responde — *qualquer membro pode marcar como paga a
fatura de um cartão do grupo, mesmo que quem efetivamente pague seja outro?* É o tipo de vazio que
histórias com critérios de aceitação expõem antes da implementação, e não depois.

---

## Expected Outcomes

1. **Personas explícitas** — transformar os papéis hoje implícitos nos requisitos (administrador de
   grupo, membro, proprietário de cartão) em personas com motivações e permissões declaradas, ou
   descartar a distinção se o sistema não a fizer
2. **Critérios de aceitação testáveis** — insumo direto para os testes de exemplo exigidos pela
   regra PBT-10, complementando o property-based testing de RF-15, RF-31 e RF-32
3. **Resolução de casos de borda em aberto** — E-03, E-10, E-12 e E-13 têm boa chance de serem
   fechados ao escrever a história correspondente
4. **Base para a decomposição em unidades** — as histórias agrupadas dão o material que a Units
   Generation usará para dividir o trabalho, dado que 92 requisitos não cabem numa unidade só
5. **Contrato compreensível para o front** — quem construir o front-end em outro repositório
   precisa entender os fluxos de usuário, não só a lista de endpoints

---

## Escopo das histórias

**Coberto por histórias**: RF-01 a RF-44 e RF-55 a RF-77 — domínio de negócio (usuários, grupos,
compartilhamento, gastos, cartões, parcelamento, categorias, receitas, orçamento, contas a pagar,
investimentos).

**Não coberto por histórias** — permanecem como requisitos técnicos: RF-45 a RF-54
(infraestrutura), RF-78 a RF-80 (contrato de API) e RF-81 a RF-93 (CI/CD). São entregáveis de
engenharia sem interação de usuário final, e forçá-los em formato de história produziria narrativas
artificiais ("como desenvolvedor, quero um pipeline...") sem ganho de clareza.
