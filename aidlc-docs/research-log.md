# Registro de Pesquisa — AI-DLC aplicado ao financial-control

**Propósito**: documento acumulativo que registra, em ordem cronológica, a evolução do projeto sob
a metodologia AI-DLC — decisões tomadas, alternativas consideradas, justificativas, e observações
sobre o próprio método. Destina-se a servir de base para um artigo científico ao final do ciclo.

**Como este documento é mantido**: a cada alteração relevante (decisão, mudança de escopo, revisão
de requisito, achado técnico), uma entrada é adicionada aqui. Diferente do `audit.md` — que é a
trilha de auditoria bruta exigida pelo AI-DLC, com o input literal do usuário — este documento é
**analítico**: registra o *porquê*, as alternativas descartadas e o que o episódio revela sobre o
método.

**Início**: 2026-07-30
**Repositório**: https://github.com/RafaelMatheus/financial-control
**Metodologia**: AI-DLC (AI-Driven Development Life Cycle) v1.0.1 — AWS Labs

---

## 1. Contexto do estudo

### 1.1 Objeto

Construção de um sistema de controle de gastos financeiros pessoais e domésticos, conduzida
integralmente pelo fluxo AI-DLC, a partir de um repositório que continha apenas um esqueleto
executável Spring Boot + Kotlin, deliberadamente sem domínio de negócio.

A escolha do ponto de partida é relevante metodologicamente: o repositório é **tecnicamente
brownfield** (existe código, build system, configuração e testes) mas **funcionalmente greenfield**
(zero regra de negócio). Isso permite observar como o método lida com um caso de fronteira que a
sua própria taxonomia não distingue explicitamente.

### 1.2 Stack de partida

| Item | Versão |
|---|---|
| Kotlin | 2.1.21 |
| Spring Boot | 3.5.4 |
| JDK | 21 |
| Gradle | 8.14.2 (Kotlin DSL) |
| PostgreSQL | 16 (docker-compose) |
| Testes | JUnit 5 + Testcontainers |

### 1.3 Configuração do método

| Extensão AI-DLC | Habilitada | Modo |
|---|---|---|
| `security/baseline` | Não | — |
| `resiliency/baseline` | Não | — |
| `testing/property-based` | **Sim** | Parcial (PBT-02, 03, 07, 08, 09 bloqueantes) |

**Desvio de método registrado**: a pedido do usuário, todas as perguntas passaram a ser feitas por
tool interativo em vez de arquivos `.md` com tags `[Answer]:`, contrariando a regra
`common/question-format-guide.md` ("Never Ask Questions in Chat"). Os arquivos de perguntas
continuaram sendo gerados como artefato, com as respostas transcritas. Ver Seção 3.1.

---

## 2. Cronologia das stages

### 2.1 Workspace Detection

**Resultado**: classificado como **brownfield** — 5 arquivos-fonte, build Gradle, docker-compose
com PostgreSQL. Nenhum artefato AI-DLC anterior.

**Observação metodológica**: a regra de detecção do AI-DLC (`inception/workspace-detection.md`)
decide brownfield/greenfield pela presença de arquivos-fonte e build files. Ela não distingue
"código de infraestrutura sem domínio" de "sistema em produção". O resultado foi uma stage de
Reverse Engineering executada sobre um esqueleto — tecnicamente correta, mas cujo achado principal
foi justamente a *ausência* de domínio.

### 2.2 Reverse Engineering

**Artefatos gerados**: 9 documentos (business-overview, architecture, code-structure,
api-documentation, component-inventory, technology-stack, dependencies, code-quality-assessment,
timestamp).

**Achado principal**: zero domínio de negócio. Nenhum controller, service, repository, entidade JPA,
DTO ou migration. Único fluxo funcional: health check do Actuator.

**Achado de maior valor prático — débito bloqueante**:
`spring.jpa.hibernate.ddl-auto: validate` configurado sem nenhuma ferramenta de migration no
classpath. A aplicação sobe hoje apenas porque não há entidades; **a primeira `@Entity` criada
quebraria o startup**.

> Este é o resultado mais interessante da stage do ponto de vista do estudo: uma análise
> automatizada sobre 14 arquivos triviais produziu um achado que teria custado tempo de depuração
> na primeira sessão de implementação. Sugere que o valor da engenharia reversa em bases pequenas
> está menos no mapeamento estrutural (óbvio) e mais na **detecção de configurações
> inconsistentes** entre si.

**Boas práticas já presentes no esqueleto**, registradas por contraste: `open-in-view: false`
(desabilita explicitamente o anti-pattern OSIV), `jdbc.time_zone: UTC`, Testcontainers com
`@ServiceConnection` (PostgreSQL real em vez de H2), escopos de dependência corretos, versões
delegadas ao BOM.

**Gate**: aprovado pelo usuário sem alterações.

### 2.3 Requirements Analysis

**Profundidade adotada**: Comprehensive.

**Volume de esclarecimento**: 17 perguntas em 4 blocos, mais 1 rodada de resolução de contradição,
mais 2 rodadas posteriores de revisão. Ver Seção 4 para a análise quantitativa.

**Resultado consolidado (revisão 6)**: 92 requisitos funcionais ativos (RF-01 a RF-93, com RF-12
removido), 17 requisitos não-funcionais, 10 cenários de usuário, 16 casos de borda, 11 premissas,
26 decisões técnicas e 5 riscos.

### 2.4 User Stories

**Assessment**: quatro critérios de alta prioridade atendidos — funcionalidades novas voltadas ao
usuário, sistema multi-persona aparente, API consumida por cliente externo, e lógica de negócio com
múltiplos cenários. Qualquer um deles isoladamente já obrigaria a execução.

**Decisões de formato**: organização híbrida (épicos por área + jornadas quando o fluxo cruza 3+
áreas), persona única, granularidade mista, critérios em Gherkin para regras e lista para CRUD,
MoSCoW com marcação de núcleo mínimo.

**Resultado**: 11 épicos, 60 histórias, 3 jornadas transversais, 1 persona com 4 contextos,
cobertura 70/70 dos requisitos de domínio.

**Três achados de valor metodológico** registrados abaixo (Seções 3.13 a 3.15).

### 2.5 Workflow Planning

**Resultado**: todas as stages condicionais executam; nenhuma pulada.

**Risco avaliado como Médio**, não Alto — distinção deliberada. A complexidade do domínio é alta
(10+ agregados, invariantes monetárias, regra de fronteira de fatura), mas o *impacto do erro* é
contido: não há produção, dados de usuário, nem integração externa. Rollback é `git revert`. Os dois
riscos de severidade Alta já registrados (R-01, banco sem backup gerenciado; R-05, apply automático
sem gate) só se materializam **após** o primeiro deploy com dados reais — momento posterior ao
encerramento do ciclo AI-DLC.

**Decomposição prevista**: 5 unidades (Fundação, Lançamentos, Crédito, Planejamento,
Infraestrutura), com a de infraestrutura paralelizável e núcleo mínimo nas três primeiras.

**Dois desvios do método, documentados no plano**:

1. **NFR Requirements e Infrastructure Design executam uma vez, não por unidade.** O AI-DLC as
   define como stages per-unit, mas ambas resolvem decisões de *projeto* — mecanismo de
   autenticação, dimensionamento da EC2 — que não variam entre unidades. Rodá-las cinco vezes
   produziria repetição sem ganho.
2. **A Units Generation foi antecipada como premissa.** O plano precisou supor uma decomposição
   para estimar gates e ordenar o trabalho, embora a decomposição definitiva seja produto da stage
   seguinte. Registrado explicitamente como expectativa, não como decisão.

### 2.6 Application Design

**Resultado**: 11 componentes de feature + 1 compartilhado, 15 entidades, 12 agregados, e o
contrato OpenAPI 3.1 validado (31 paths, 51 operações, 39 schemas).

**5 decisões fechadas**: estrutura por feature (D-03), mesmo modelo para escrita e leitura (D-29),
dono + dois totais na API (D-30), fatura persistida (D-31), UUID (D-32).

**Das três questões estruturais que motivavam a stage**: uma resolvida (D-03), uma resolvida em
duas camadas (J-03 → RF-97 + D-30), e uma **extinta** antes de ser respondida (J-01, pela remoção
do rateio na revisão 8).

### 2.7 Units Generation

**Resultado**: 5 unidades — Fundação, Lançamentos, Crédito, Planejamento e Infraestrutura. As 60
histórias atribuídas, nenhuma órfã nem duplicada. Caminho crítico U1 → U2 → U3.

**Decisão central**: resolver a divergência entre a ordem do plano de execução e o grafo de
dependências, **dividindo o componente `gasto`** — à vista em U2, integração com cartão em U3.

**Fim da fase de Inception.** Sete stages executadas, nenhuma pulada.

---

## 3. Decisões e episódios relevantes

### 3.1 Desvio no mecanismo de perguntas

**Episódio**: logo após o início, o usuário instruiu: *"use o ask tool sempre que precisar fazer uma
pergunta ao usuário em 100% dos casos"*.

**Conflito**: a regra `common/question-format-guide.md` do AI-DLC é explícita — *"You must NEVER ask
questions directly in the chat. ALL questions must be placed in dedicated question files"*.

**Resolução adotada**: instrução direta do usuário prevalece sobre a regra do método. As perguntas
passaram a ser feitas por tool interativo, e os arquivos de perguntas continuaram a ser gerados
como artefato, com as respostas transcritas e uma nota explicando o desvio.

**Observação metodológica**: o formato de arquivo tem uma vantagem que se perdeu — permite ao
usuário responder de forma assíncrona, revisar respostas anteriores antes de finalizar, e envolver
outras pessoas. Em contrapartida, o tool interativo produziu **taxa de resposta e velocidade
substancialmente maiores**, e permitiu previews visuais comparativos que se mostraram decisivos em
pelo menos três decisões de modelagem (parcelamento, rateio, ciclo de fatura). O trade-off é entre
**deliberação assíncrona** e **fluência da conversa**.

### 3.2 Resolução do débito bloqueante: Flyway

**Problema**: `ddl-auto: validate` sem ferramenta de migration (achado da Seção 2.2).

**Alternativas apresentadas**: Flyway (SQL versionado), Liquibase (changelogs com abstração de
banco e rollback nativo), manter `ddl-auto` gerando o schema.

**Decisão**: **Flyway**, mantendo `ddl-auto: validate`.

**Justificativa**: `validate` deixa de ser um problema e passa a ser uma proteção — detecta
divergência entre entidades JPA e schema no startup. Liquibase entregaria abstração de dialeto e
rollback declarativo, irrelevantes num projeto com um único banco alvo. Manter `ddl-auto` gerando o
schema eliminaria o versionamento e não teria caminho seguro para produção.

### 3.3 Detecção de contradição: extensão Security vs. multi-usuário

**Episódio**: o usuário respondeu **C — Múltiplos usuários** à pergunta sobre quem usaria o sistema,
e depois **Não** à pergunta de opt-in da extensão Security.

**Contradição detectada**: um sistema multi-usuário com dados financeiros compartilhados exige, por
definição funcional, autenticação e controle de acesso — independentemente do checklist de
hardening da extensão.

**Resolução**: pergunta de clarificação explicitando a distinção entre *extensão* (checklist
bloqueante de hardening aplicado às stages do método) e *requisito funcional* (autenticação,
isolamento de dados, permissões de grupo). O usuário confirmou: extensão desligada, autenticação
mantida como requisito.

> Episódio ilustrativo de um risco real do mecanismo de opt-in por extensão: o nome da extensão
> ("Security") sugere um escopo mais amplo do que ela de fato controla, criando espaço para o
> usuário desligar acidentalmente algo que não pretendia. A regra
> `common/question-format-guide.md` prevê detecção de contradições — e ela funcionou aqui —, mas o
> desenho do opt-in poderia ser menos ambíguo.

### 3.4 Pergunta em vez de resposta: "o que é AWS Well-Architected?"

**Episódio**: na pergunta de opt-in da extensão Resiliency, o usuário respondeu com uma pergunta em
vez de escolher uma opção.

**Tratamento**: fornecida explicação do framework e do Pilar de Confiabilidade (retry com backoff,
health checks, timeouts, idempotência, backup/restore, RTO/RPO, observabilidade), e a pergunta foi
reapresentada. Resposta após o esclarecimento: **Não**.

**Observação metodológica**: o texto de opt-in da extensão (`resiliency-baseline.opt-in.md`) assume
familiaridade com o AWS Well-Architected Framework. Extensões que dependem de vocabulário
especializado deveriam trazer a explicação embutida, não pressupô-la — caso contrário, o opt-in
vira uma decisão tomada sem informação.

### 3.5 Ampliação de escopo durante o esclarecimento

O pedido original foi: *"um sistema de controle de gastos financeiros que me permita cadastrar
gastos e parcelas de cartão de crédito"*.

O escopo final, após esclarecimento, inclui: multi-usuário com autenticação, grupos com
compartilhamento e rateio configurável, receitas, categorias, orçamento por categoria, contas a
pagar com vencimento e recorrência, objetivos de investimento, e infraestrutura como código para
AWS EC2.

**Fator de ampliação**: de 2 capacidades declaradas para 12 grupos de requisitos.

> Este é possivelmente o achado central do estudo. A ampliação não veio de o método "inventar"
> escopo — cada item foi trazido explicitamente pelo usuário em resposta a uma pergunta. O que as
> perguntas fizeram foi **tornar visível um escopo que já existia na cabeça do usuário mas não
> estava no pedido**. O requisito de compartilhamento em grupo, o de maior complexidade de todo o
> sistema, apareceu como texto livre numa resposta de múltipla escolha sobre outro assunto.

### 3.6 Decisão de infraestrutura: Terraform no mesmo repositório

**Episódio**: no gate de aprovação dos requisitos, o usuário informou que o serviço rodaria em AWS
EC2 e perguntou se o Terraform poderia ficar no mesmo repositório.

**Critério apresentado**: a decisão não é de preferência, mas de **quantos serviços a infra atende
e como as permissões de deploy estão separadas**.

- Mesmo repositório: um serviço, um mantenedor, infra pequena; mudanças que exigem app + infra
  fecham em um único PR
- Repositório separado: múltiplos serviços compartilhando a infra, separação real de permissões,
  necessidade de aprovação independente para `terraform apply`

**Decisão**: mesmo repositório, em `infra/terraform/`. Custos assumidos: filtro de path no CI
(RNF-14) e state remoto em S3 (RF-51).

### 3.7 Decisão de infraestrutura: PostgreSQL no EC2 (risco aceito)

**Decisão**: PostgreSQL em container Docker na própria instância EC2, em vez de RDS.

**Justificativa do usuário**: custo e simplicidade.

**Risco registrado (R-01, severidade Alta)**: dados financeiros de múltiplos usuários sem backup
gerenciado, sem patching, sem restauração point-in-time.

**Mitigações acordadas**: volume EBS separado do volume raiz (RF-50) e rotina de backup com
procedimento de restauração documentado (RF-54). Registrado explicitamente que RF-54, embora
classificado como prioridade "S", **deve ser tratado como obrigatório na prática** antes de
qualquer deploy com dados reais.

> Caso de decisão consciente contra a recomendação. O método não impede — e não deveria impedir —
> a escolha; o valor está em ter o risco nomeado, dimensionado e com mitigação acordada no
> documento, em vez de implícito.

### 3.8 Generalização conceitual: "Casa" → "Grupo"

**Episódio**: no gate de aprovação, o usuário solicitou tratar "casa" como "grupo" genérico.

**Análise**: a cardinalidade descrita pelo usuário (*"o usuario pode ou nao participar de um grupo
e pode ter n pessoas neste grupo"*) **já estava especificada** em RF-07 e RF-08. A mudança real era
**conceitual**: de um agregado específico de domínio (grupo doméstico) para um genérico (qualquer
coleção nomeada de pessoas que compartilham gastos — casa, república, casal, viagem).

**Consequência não solicitada, identificada pelo método**: com grupos genéricos, o requisito de
**compartilhamento avulso** (RF-12) tornou-se redundante — o caso que ele cobria (dividir com
pessoas fora da casa) passou a ser resolvido criando um grupo. Levado ao usuário como pergunta, e
o requisito foi **removido**.

**Ganho de simplificação**: eliminou-se um segundo caminho de visibilidade e um segundo caminho de
autorização. O modelo passou de dois esquemas paralelos de compartilhamento para um só. A decisão
D-07 (modelagem de "participante": membro do grupo vs. usuário avulso), que estava adiada para a
Application Design, foi **resolvida por eliminação**.

> Episódio de valor metodológico alto: uma mudança apresentada como terminológica ("chame de grupo")
> teve como consequência a remoção de um requisito e a resolução antecipada de uma decisão de
> design. Sugere que revisões de vocabulário de domínio merecem análise de impacto, não aplicação
> literal — renomear um conceito pode alterar as fronteiras do modelo.
>
> Registra-se também a decisão de **não reaproveitar o número RF-12**, preservando a rastreabilidade
> da numeração ao longo das revisões.

### 3.9 Unificação: fatura de cartão como conta a pagar

**Episódio**: o usuário pediu que cada conta tivesse vencimento próprio, e detalhou os tipos:
fatura de cartão de crédito, PIX a fazer, boleto, fatura de serviço (energia, gás). Em seguida
esclareceu: *"a fatura da conta deve passar a ser um vencimento geral... Mas eu posso cadastrar
itens novos que eu for comprando em determinado cartao, isso vai vencer junto com a conta normal,
porém, vai aumentar a fatura dos próximos meses caso nao tenha passado da data de vencimento"*.

**Decisão (D-14)**: a fatura consolidada do cartão — que já existia como RF-26/RF-27 num módulo
próprio — passa a **materializar-se automaticamente como uma conta a pagar** ao fechar, entrando na
mesma visão de vencimentos que boleto, PIX e fatura de serviço.

**Ambiguidade detectada e resolvida (D-15)**: o usuário escreveu *"caso não tenha passado da data de
**vencimento**"*, mas o comportamento real de cartão de crédito usa o **fechamento** como corte —
há uma janela entre fechamento e vencimento em que a fatura já está fechada mas ainda não foi paga,
e compras nessa janela vão para o mês seguinte. A distinção foi apresentada com diagrama e o
usuário confirmou o fechamento como critério.

> Caso em que a linguagem natural do usuário divergia do domínio real, e a divergência mudaria o
> cálculo — compras na janela entre fechamento e vencimento cairiam na fatura errada, e os valores
> não bateriam com o extrato do banco. Explicitar a diferença antes de escrever o requisito evitou
> um erro que só apareceria em produção, contra dados reais.

### 3.10 Modelagem de investimentos

**Pedido**: *"eu quero poder adicionar valores relacionados a investimentos... Exemplo: investimento
de viagem e investimento de geral"*.

**Alternativas apresentadas**: (a) só aportes acumulados por objetivo; (b) aportes + saldo atual
atualizável à mão; (c) aportes + ativos individualizados com rentabilidade.

**Decisão (D-17)**: opção (b). Rendimento é **derivado** (`saldo atual − total aportado`), não
calculado a partir de taxas ou cotações. Evita trazer para o escopo um módulo de carteira com
indexadores e cotação de ativos.

**Decisão (D-18) — a mais consequente**: o usuário determinou que **o aporte conta como gasto** no
balanço do mês.

> Decisão com implicação conceitual que vale registrar: o balanço passa a medir **fluxo de caixa**,
> não **variação patrimonial**. Investir R$ 2.000 reduz o resultado do mês em R$ 2.000, embora o
> patrimônio do usuário não tenha diminuído. É uma escolha legítima e comum em controle financeiro
> pessoal (o dinheiro de fato saiu do disponível), mas define a semântica do indicador — e foi
> registrada explicitamente para não ser reinterpretada adiante.

### 3.11 Contrato de API como entregável explícito

**Pedido**: *"vou precisar de um documento também com endpoints para montar o front"*.

**Contexto**: o front-end vive em outro repositório (decisão da Question 3), o que torna o contrato
da API a única interface entre os dois times/repositórios — já registrado como RNF-08 desde a
revisão 1, mas sem entregável nomeado.

**Tensão identificada**: o contrato definitivo é produto da **Application Design**, stage que ainda
não rodou. Gerar agora, a partir dos requisitos, produziria um contrato provisório sobre o qual o
front começaria a ser construído — com retrabalho quando o modelo de domínio estabilizasse.

**Alternativas apresentadas**: (a) preliminar agora, atualizado depois; (b) esperar a Application
Design e entregar estável; (c) as duas versões, com diff entre elas.

**Decisão (D-06)**: opção (b) — **após a Application Design**, em **OpenAPI 3.1 YAML**.
Formalizado como RF-78 a RF-80.

> Episódio que expõe uma tensão estrutural do método: as stages do AI-DLC produzem artefatos em
> ordem de dependência lógica, mas o consumidor externo (aqui, o desenvolvedor do front) tem sua
> própria cronologia e pode precisar de um artefato antes de a stage que o produz ter rodado. O
> método não oferece um mecanismo de "entregável antecipado provisório" — a escolha é binária entre
> bloquear o consumidor ou produzir fora de ordem. Neste caso o usuário optou por bloquear, o que
> preserva a integridade da sequência; num contexto com dois times trabalhando em paralelo, a
> pressão pela alternativa (a) seria maior.
>
> Vale registrar também que o pedido tornou explícito um entregável que estava implícito: RNF-08
> mencionava "documentado (OpenAPI)" desde a primeira revisão, mas sem versão, formato, momento de
> entrega ou critério de suficiência. Requisitos não-funcionais formulados como qualidade desejada
> ("precisa ser documentado") tendem a não gerar entregável verificável até que alguém peça o
> artefato concreto.

### 3.12 A lacuna de provisionamento do AI-DLC

**Pergunta do usuário**: *"o provisionamento da infra vai acontecer em qual momento?"*

**Verificação nas regras do método**:

| Stage | Fase | O que produz |
|---|---|---|
| Infrastructure Design | Construction (condicional) | Apenas documentos de design (`infrastructure-design.md`, `deployment-architecture.md`) — mapeia componentes lógicos para serviços de nuvem. Não gera IaC |
| Code Generation | Construction (sempre) | Código, incluindo "Deployment Artifacts Generation" — é aqui que o Terraform sai escrito |
| Build and Test | Construction (sempre) | Compila e testa. Encerra com *"Ready to proceed to Operations phase for deployment planning"* |
| Operations | Operations | **Placeholder vazio** |

O arquivo `operations/operations.md` é explícito: *"The AI-DLC workflow currently ends after the
Build and Test phase in CONSTRUCTION"*, e lista "deployment planning and execution" como escopo
futuro.

**Constatação**: o método **entrega o código da infraestrutura, mas não a infraestrutura**. O
`terraform apply` está fora do fluxo. Isso não fica evidente pela leitura do `CLAUDE.md` — a fase
de Operations aparece no diagrama de três fases como se fosse uma etapa do processo, e só ao abrir
o arquivo de regras se descobre que é um placeholder sem conteúdo.

O risco prático é de expectativa: o usuário havia decidido em D-09 que "a IaC entra neste ciclo", o
que razoavelmente sugere infraestrutura no ar ao final. Entra o *código* da infraestrutura.

**Resolução escolhida pelo usuário**: fechar a lacuna com **GitHub Actions** (D-21 a D-26), em vez
de aceitar o provisionamento manual. O `terraform apply` passa a rodar no CI a partir do merge em
`main` — nem da sessão do agente, nem da máquina do desenvolvedor.

**Decisões associadas**:

| ID | Decisão | Alternativa descartada e por quê |
|---|---|---|
| D-22 | OIDC para autenticação na AWS | Access keys em GitHub Secrets — credencial de longa duração que, se vazar, expõe a conta inteira |
| D-23 | Amazon ECR como registry | GHCR — gratuito, mas exigiria um token do GitHub guardado na instância, criando o segredo de longa duração que o OIDC eliminou |
| D-24 | SSM Run Command para deploy | SSH do Actions — exigiria porta 22 aberta e chave privada nos Secrets |
| D-25 | `apply` automático no merge | GitHub Environment com reviewer — recomendado e recusado pelo usuário; registrado como risco R-05 |
| D-26 | Bootstrap manual e único | Sem alternativa real: é uma dependência circular, não uma preferência |

**O problema do bootstrap** merece registro à parte. Existe uma dependência circular estrutural: o
Terraform no CI precisa de um bucket S3 para o state e de uma role IAM para autenticar, mas ambos
são infraestrutura que só o Terraform criaria. Nenhuma automação resolve isso — a solução é um
módulo `bootstrap/` com state local, aplicado uma vez à mão, criando exatamente os recursos dos
quais o pipeline depende.

> Esta é uma fronteira genuína da automação de infraestrutura, não uma limitação do AI-DLC. Vale
> registrar no artigo porque ilustra que "infraestrutura como código" nunca é 100% código — existe
> sempre um degrau inicial fora do sistema, e a qualidade da documentação desse degrau (aqui, o
> runbook de RF-92) determina se o projeto é reproduzível por outra pessoa.

**Sobre o risco R-05 (`apply` automático)**: registrado com severidade Alta, e não por purismo de
processo. A combinação específica é o que preocupa — `apply` sem gate + PostgreSQL sem backup
gerenciado (R-01) + volume EBS gerenciado pelo mesmo Terraform significa que um `replace` no
recurso do volume, aprovado num PR lido às pressas, destrói os dados financeiros sem ponto de
recuperação. As mitigações acordadas (`prevent_destroy`, plan visível no PR, backup como
pré-requisito de merge em `infra/**`) foram registradas para a Infrastructure Design.

### 3.13 A persona que não existia

**Episódio**: a análise inicial dos requisitos sugeria três papéis distintos — quem cria e
administra o grupo, quem apenas participa, e o proprietário do cartão. A pergunta foi levada ao
usuário como escolha entre uma, duas ou três personas.

**Resposta**: **uma persona só**.

**Verificação nos requisitos**: a resposta estava correta e já estava escrita. **RF-16** permite que
*qualquer membro* edite ou exclua gastos de escopo GRUPO — não há privilégio de autoria nem de
criação. **RF-08** não restringe quem pode adicionar ou remover membros. **RF-24** trata a
propriedade do cartão como definidora de *visibilidade*, não como classe de usuário.

> A hierarquia administrador/membro é tão convencional em sistemas com grupos que ela foi
> **projetada sobre os requisitos** durante a análise, sem que estivesse neles. Modelá-la teria
> criado uma distinção que o sistema não implementa, e todas as histórias teriam herdado uma
> diferença falsa. O registro em `personas.md` inclui uma nota explícita para a Application Design:
> a ausência de hierarquia é decisão de produto, não omissão, e a modelagem não deve antecipá-la
> "por precaução".

### 3.14 Um requisito nasceu de uma proibição

**Episódio**: ao resolver os casos de borda E-12 (lançamento retroativo em fatura fechada) e E-13
(alteração em fatura já paga), o usuário escolheu **bloquear** qualquer operação que altere fatura
paga — justificativa: fatura paga é fato consumado, o dinheiro saiu e o valor bate com o extrato do
banco.

**Consequência não solicitada**: se alterações são bloqueadas e não há caminho de saída, um
lançamento errado numa fatura paga fica **preso para sempre**. A proibição, sozinha, cria um
beco sem saída.

**Resultado**: **RF-94** (desmarcar o pagamento de fatura ou conta) foi criado, sem ter sido pedido.
Junto com RF-95 (a proibição em si) e RF-96 (reabrir e recalcular fatura fechada não paga), forma
um trio coerente: a operação de correção existe, mas é explícita e consciente — o usuário precisa
desmarcar o pagamento deliberadamente, em vez de a fatura ser alterada como efeito colateral.

> Padrão generalizável: **toda regra que proíbe uma operação sobre estado terminal precisa de uma
> operação inversa que reabra esse estado**, ou o usuário fica sem recurso. A proibição isolada
> parece completa quando escrita, e só se revela incompleta ao imaginar o usuário que errou.

### 3.15 As jornadas transversais descobriram o que os épicos escondiam

**Contexto**: a organização híbrida previa histórias de jornada apenas para fluxos que atravessassem
três ou mais áreas. Foram escritas três: compra parcelada em cartão de grupo com rateio (J-01),
fechar o mês (J-02), e entrar num grupo existente (J-03).

**Resultado**: as três jornadas levantaram **três questões que não existiam em nenhum dos 60
requisitos nem em nenhuma das 60 histórias de épico**:

| Origem | Questão descoberta |
|---|---|
| J-01 | O rateio incide sobre **cada parcela**, não apenas sobre o total da compra. As invariantes de H-12 e H-28 se **compõem**: a soma das cotas deve fechar por parcela, e a soma das parcelas por compra |
| J-02 | O "realizado" do orçamento conta pela **data da compra** ou pela **competência da fatura**? Uma compra de 30/07 no cartão entra no orçamento de julho ou de setembro? |
| J-03 | "Total do grupo" e "minhas cotas" **divergem** para um membro recém-adicionado. A API precisa expor a diferença, ou o usuário verá dois números sem entender o motivo |

> Nenhuma das três é visível dentro de um épico. Elas vivem exatamente nas **costuras** entre áreas
> — que é onde a decomposição funcional, por construção, não olha. A questão de J-01 é a mais séria:
> ela altera a estrutura do modelo de dados (a cota referencia a parcela, não a compra), e teria
> sido descoberta na implementação, não no design.
>
> Este é o argumento empírico a favor do formato híbrido sobre o puramente feature-based. O custo
> foi escrever três histórias adicionais; o retorno foi uma questão de modelagem que mudaria o
> schema depois de pronto.

### 3.16 Planejar exige antecipar o que a stage seguinte decidiria

**Episódio**: a Workflow Planning precisa estimar o esforço restante e ordenar o trabalho. Ambos
dependem de saber **em quantas unidades** o sistema será dividido — mas a decomposição é produto da
**Units Generation**, a stage seguinte.

**Tensão**: sem uma hipótese de decomposição, o plano não consegue dizer quantos gates de aprovação
restam nem qual o caminho crítico. Com uma hipótese, o plano antecipa uma decisão que não lhe cabe.

**Resolução adotada**: o plano registra a decomposição em 5 unidades explicitamente como
**expectativa**, derivada da marcação de núcleo mínimo já feita nas User Stories, e declara que a
decomposição definitiva sai na Units Generation. A estimativa de ~20 gates é apresentada como
função dessa premissa, não como número absoluto.

> O AI-DLC coloca a Workflow Planning **antes** da Units Generation, o que é coerente do ponto de
> vista de autoridade — o plano decide *se* a decomposição acontece. Mas cria uma dependência
> invertida na dimensão de conteúdo: para planejar bem, é preciso saber o resultado do que se está
> planejando. A saída foi tratar a antecipação como premissa declarada, em vez de fingir que a
> ordem das stages elimina a dependência.
>
> Vale notar que a marcação de "núcleo mínimo" feita nas User Stories — que não era exigida pelo
> método, e resultou de uma escolha de formato do usuário — foi o que tornou a antecipação
> defensável. Sem ela, a decomposição seria arbitrária.

### 3.17 Stages per-unit que não variam por unidade

**Episódio**: o AI-DLC define NFR Requirements e Infrastructure Design como stages **per-unit**,
executadas dentro do loop de Construction para cada unidade de trabalho.

**Constatação**: ambas resolvem decisões que **não variam entre unidades**:

- **NFR Requirements** fecha D-02 (mecanismo de autenticação), D-05 (framework de PBT) e D-06
  (ferramenta de OpenAPI). São escolhas de stack válidas para o projeto inteiro
- **Infrastructure Design** fecha D-11 (dimensionamento da EC2, AMI, região, rede) e D-12
  (mecanismo de deploy). A infraestrutura é compartilhada por todas as unidades de domínio

Executá-las cinco vezes produziria cinco documentos idênticos, cinco gates de aprovação redundantes,
e o risco real de as respostas divergirem entre execuções.

**Resolução**: ambas executam **uma vez** — NFR Requirements na primeira unidade, Infrastructure
Design na unidade de infraestrutura. As demais unidades herdam as decisões.

> A estrutura per-unit do método assume que cada unidade é um serviço razoavelmente autônomo, com
> suas próprias escolhas de stack e infraestrutura — premissa razoável em arquitetura de
> microsserviços. Num **monolito single-module**, a premissa não se sustenta: existe um único
> classpath, um único deploy, um único banco. O método não oferece um mecanismo para declarar que
> uma stage per-unit é, neste projeto, de escopo global — a adaptação teve de ser feita e
> justificada no plano.

### 3.18 A simplificação que veio tarde — remoção do rateio

**Episódio**: já dentro da Application Design, com requisitos aprovados na revisão 7 e 63 histórias
escritas, o usuário esclareceu: *"Não vamos compartilhar contas do nível de dividir gastos, só
dividir as contas de uma casa. Exemplo: mora eu e minha esposa, conta x o owner é minha esposa,
conta y o owner sou eu, mas todos dois conseguem ver suas contas de uma casa caso sejam membros do
mesmo grupo"*.

**Constatação**: o sistema não precisa de **rateio**. O grupo serve para **visibilidade** — os
membros enxergam as contas uns dos outros —, não para dividir valores. Cada lançamento tem um dono
e o valor é integralmente dele.

**Origem do mal-entendido**: rastreável a uma resposta na primeira rodada de esclarecimento. A
pergunta *"Quando um gasto é compartilhado, o valor precisa ser dividido entre as pessoas?"*
oferecia três opções, incluindo **"Sem divisão — só visibilidade"**. O usuário escolheu **"Divisão
configurável por gasto"**. Sete revisões depois, corrigiu.

> Vale registrar que a opção correta **estava na mesa desde o início** e foi rejeitada. Não foi
> falta de pergunta, nem opção ausente. A hipótese mais provável é que "compartilhar gastos numa
> casa" evoque naturalmente o modelo de aplicativos de divisão de despesas, e a pergunta tenha sido
> lida através dessa expectativa — pelo usuário e pelo modelo — antes de o domínio real ficar claro
> na cabeça de ambos.

**Custo da correção**: 3 requisitos removidos (RF-13, RF-14, RF-15), 3 histórias removidas (H-10,
H-11, H-12), 1 caso de borda extinto (E-02), 2 jornadas reescritas, 10 histórias revisadas,
1 entidade eliminada do modelo (`Cota`), e **1 dos 3 alvos de property-based testing desapareceu**.

**Benefício da correção**: eliminou a área de maior complexidade do sistema. O rateio configurável
por percentual ou valor absoluto, com resíduo de centavos a distribuir e invariante a manter,
era — segundo o próprio documento de requisitos — *"a área de maior complexidade do sistema"*.

**O que a correção custou vs. o que teria custado depois**:

| Momento | Impacto da mudança |
|---|---|
| Onde ocorreu (Application Design) | Reescrita de documentos: ~2 horas de artefato |
| Se ocorresse na Functional Design | Idem + redesenho do modelo de dados |
| Se ocorresse na Code Generation | Idem + código escrito e descartado + migrations |
| Se ocorresse pós-deploy | Idem + migração de dados + tabela `cota` órfã em produção |

> Este é o argumento empírico central a favor de fases de especificação antes da implementação. A
> mudança foi barata **porque nada tinha sido construído**. O AI-DLC não evitou o erro — ele o
> tornou reversível.
>
> Também vale notar o que **tornou a correção segura**: a rastreabilidade. Com a matriz
> requisito ↔ história e as referências cruzadas entre artefatos, foi possível localizar
> mecanicamente tudo o que dependia do rateio. Sem isso, a remoção deixaria resíduos — uma
> premissa aqui, um critério de aceitação ali — que só apareceriam na implementação.

**Decisão adicional gerada (D-28)**: a remoção do rateio criou uma pergunta nova — se ninguém tem
cota, o que significa "quanto eu gastei este mês" quando enxergo contas de outra pessoa? A resposta
(**duas grandezas distintas, nunca somadas**: total pessoal por dono, total do grupo por escopo)
virou **RF-97**, e resolveu de quebra a questão J-03, que estava aberta desde as User Stories.

### 3.19 O desvio da própria instrução

**Episódio**: ao fim de uma rodada de trabalho, o modelo escreveu *"o plano da Application Design
está atualizado e aguardando suas respostas às 5 questões de design restantes"* — **sem ter feito as
perguntas**. O usuário respondeu: *"porque voce nao tá usando o asktool"*.

**Constatação**: a instrução de usar `AskUserQuestion` em 100% dos casos estava registrada em
memória desde o início da sessão e vinha sendo seguida por dezenas de interações. O desvio ocorreu
num momento de alto volume de escrita de artefatos — a mensagem final descrevia o estado do trabalho
e escorregou para uma afirmação sobre algo que não havia sido feito.

> Vale registrar como padrão de falha: a instrução não foi esquecida nem contestada — foi
> **contornada por inércia narrativa**. Ao resumir "o que ficou pendente", o modelo descreveu a
> pendência em vez de agir sobre ela. É um modo de falha específico de tarefas longas com muitos
> artefatos: o relato do trabalho substitui o trabalho.
>
> A correção veio do usuário, não de auto-verificação. Num processo com gates de aprovação, isso
> teria travado o fluxo até alguém perceber.

### 3.20 O grafo de dependências contradisse o plano de execução

**Episódio**: a Workflow Planning propôs cinco unidades de trabalho com U2 (Lançamentos) antes de
U3 (Crédito) — ordem intuitiva, já que gastos parecem mais fundamentais que cartões.

**Constatação da Application Design**: o componente `gasto` **depende de** `cartao` e `fatura`. Um
gasto pago com cartão precisa que o cartão exista e que sua competência de fatura seja calculável.
A ordem proposta não é executável para a parte de gasto vinculada a cartão.

**Resolução**: registrado como achado para a Units Generation, com duas saídas possíveis — dividir
`gasto` em duas etapas (à vista em U2, em cartão em U3), ou antecipar `cartao` e `fatura`.

> A Workflow Planning ordenou as unidades por **afinidade conceitual** (gastos são mais básicos que
> crédito). O grafo de dependências ordena por **acoplamento real**. As duas ordens divergiram, e só
> a construção do grafo revelou a divergência.
>
> Isso reforça a observação O-17: a Workflow Planning precisou antecipar uma decomposição sem ter o
> insumo que a validaria. O insumo — o grafo de componentes — só existe duas stages depois.

### 3.21 Modelar a fatura como entidade criou uma decisão nova

**Episódio**: o usuário escolheu modelar a fatura como **entidade persistida** (D-31), e não como
projeção sobre os lançamentos. Justificativa correta: a fatura carrega estado que não deriva dos
lançamentos — momento do fechamento e situação de pagamento.

**Consequência não prevista**: a fatura fechada **também** se materializa como `ContaAPagar`
(RF-59, decisão D-14 de uma revisão anterior). Se ambas carregarem estado de pagamento, existem
**duas fontes de verdade** que podem divergir — uma fatura marcada como paga com a conta ainda em
aberto, ou o inverso.

**Registrado como D-33**, com recomendação de **derivar**: a `ContaAPagar` carrega o pagamento e
`Fatura.status` o projeta. A decisão fica para a Functional Design.

> Padrão recorrente neste projeto: **uma decisão de modelagem tomada isoladamente colide com outra
> tomada em stage anterior**. D-14 (unificação fatura↔conta) e D-31 (fatura persistida) são ambas
> defensáveis sozinhas; juntas, criam duplicação de estado. Só a modelagem explícita das entidades
> tornou a colisão visível.
>
> É o mesmo mecanismo de O-16 (questões vivem nas costuras), aplicado agora entre *decisões* e não
> entre *áreas funcionais*.

### 3.22 Dividir um componente para preservar a capacidade de entrega

**Episódio**: a Application Design revelou que `gasto` depende de `cartao` e `fatura`, contradizendo
a ordem U2-antes-de-U3 do plano de execução (Seção 3.20). A Units Generation precisou escolher entre
três saídas: dividir o componente, inverter a ordem das unidades, ou fundi-las.

**Escolha**: **dividir `gasto`** — gasto à vista em U2, integração com cartão em U3.

**Critério que decidiu**: o usuário optou por fronteira de unidade definida por **capacidade de
negócio**, não por dependência técnica. Inverter a ordem (opção B) produziria uma unidade de crédito
que termina com cartões cadastrados e **nenhum lançamento para consolidar** — tecnicamente limpa,
mas sem nada demonstrável ao fim.

**Custo assumido**: um componente tocado em duas unidades exige coordenação explícita. Três acordos
foram registrados para evitar retrabalho:

1. A entidade `Gasto` nasce em U2 **já com** as colunas `cartaoId` e `competencia` nuláveis —
   nenhuma migration de `ALTER TABLE` em U3
2. O endpoint aceita `cartaoId` opcional desde U2, mas **rejeita** o campo até U3 existir
3. Ao concluir U3, os testes de U2 devem passar **sem modificação** — vira teste de regressão de
   fronteira

> O acordo (1) é o que torna a divisão barata. Sem ele, U3 exigiria alterar uma tabela já criada e
> em uso — e o custo de dividir o componente passaria a superar o de inverter a ordem. **A decisão
> de decomposição só é boa porque veio acompanhada do acordo de schema.**
>
> Registra-se também que a divisão foi possível porque a dependência é **parcial**: um gasto à vista
> não precisa de cartão. Se toda operação do componente dependesse de `cartao`, a única saída seria
> inverter ou fundir.

### 3.23 A dependência que quase não existe

**Constatação ao montar o grafo de unidades**: **U4 (Planejamento) depende de U3 (Crédito) por causa
de uma única história** — a jornada **J-02 (fechar o mês)**, que cruza vencimentos, faturas, gastos
e orçamento.

Todas as outras 15 histórias de U4 — receitas, orçamento, investimentos — dependem apenas de U1 e
U2. Adiar J-02 tornaria **U3 e U4 paralelizáveis**, encurtando o caminho crítico do projeto.

> Achado de valor prático que só aparece ao cruzar o mapa de histórias com o grafo de dependências.
> A dependência U4 → U3 parece estrutural quando se olha só os componentes; ao olhar história a
> história, revela-se uma amarra pontual.
>
> Vale notar de onde veio a amarra: **das jornadas transversais**. Foram elas que expuseram questões
> de modelagem na Seção 3.15, e agora são elas que criam as únicas dependências não óbvias entre
> unidades. Faz sentido — jornadas existem justamente para cruzar fronteiras, e cruzar fronteiras é
> o que cria acoplamento.

### 3.24 A mudança de arquitetura que fechou um risco

**Episódio**: já com o código de infraestrutura gerado e no GitHub, o usuário informou que passaria
a usar banco gerenciado: *"modifiquei um pouco a ideia, o banco to usando um rds aurora postgres
agora"*.

**Primeira consequência, e a mais relevante**: o **risco R-01 deixou de existir**. Era o único de
severidade Alta que permanecia aberto — PostgreSQL em container na EC2, sem backup gerenciado, com
o usuário tendo decidido explicitamente adiar a rotina de backup (D-36). O RDS traz backup
automático, point-in-time recovery e patching como propriedades do serviço.

> Um risco que atravessou várias stages sendo repetidamente registrado e mitigado parcialmente
> desapareceu por uma decisão de arquitetura tomada por outro motivo. Vale registrar que a
> mitigação estrutural — trocar o componente — foi mais eficaz que as mitigações incrementais que
> vinham sendo acumuladas (volume EBS separado, `prevent_destroy`, runbook de restauração).

**Dois conflitos detectados no snippet que o usuário enviou** — um trecho Java com a URL JDBC real:

| Conflito | Consequência se ignorado |
|---|---|
| Banco em `us-east-2`, infra planejada para `us-east-1` | Latência por query e custo de transferência entre regiões |
| Cluster criado fora do Terraform | O `apply` criaria um cluster **novo ao lado**, sem tocar no existente |

Nenhum dos dois estava explícito no pedido. Ambos vieram de ler o endpoint com atenção.

**Aurora vs. RDS comum**: apresentada a diferença de custo — Aurora ~US$ 43–60/mês contra ~US$ 13 do
RDS `db.t4g.micro` — com a observação de que a arquitetura de instância única **não usa** réplicas
nem failover rápido, que são o diferencial do Aurora. O usuário optou pelo RDS comum.

> O usuário havia escolhido `us-east-1` sobre São Paulo para economizar US$ 11/mês. Adotar Aurora
> teria acrescentado US$ 30–47 — quatro vezes a economia perseguida na decisão anterior. Apontar a
> incoerência de ordem de grandeza entre duas decisões de custo é barato e mudou o resultado.

**Limitação encontrada na implementação**: o Terraform **não consegue criar o usuário da aplicação**.
O banco fica em subnet privada, e o Terraform roda no GitHub Actions, fora da VPC. Criar uma role
PostgreSQL exige conexão SQL ao banco.

Saída adotada: documentar como passo único do runbook (Passo 5b), executado da EC2 via SSM, com o
SQL pronto. Alternativas descartadas: usar o master na aplicação (contraria D-39 e o princípio de
menor privilégio) e provider PostgreSQL no Terraform (exigiria expor o banco ou rodar o Terraform
dentro da VPC).

> Fronteira real de IaC: **Terraform provisiona infraestrutura, não estado interno de aplicação**.
> Usuários e permissões dentro do banco são estado do banco, não da AWS. A tentação de automatizar
> tudo esbarra em o Terraform não ter — nem dever ter — rota de rede até o recurso que provisionou.

### 3.25 A conta errada

**Episódio**: ao instalar a AWS CLI e verificar a autenticação, `aws sts get-caller-identity`
respondeu com a conta **490490484770** (`user/mt-clix`) — diferente da **594116288641** (`rmpcastr`)
que o usuário havia informado.

**O que teria acontecido**: `terraform apply` criaria bucket de state, OIDC provider, IAM role, VPC,
EC2 e RDS na conta errada — aparentemente de trabalho ou cliente. Recursos cobrados, em conta
alheia, e o pipeline apontando para ARNs inexistentes na conta pretendida.

**Por que foi detectado**: o Passo 1 do runbook exige `aws sts get-caller-identity` **antes** de
qualquer apply, e o comando foi executado como parte da verificação de instalação.

> A salvaguarda mais barata do runbook — um comando de uma linha, escrito quase como formalidade —
> pegou o erro mais caro possível na primeira vez que foi executada. Vale registrar que ela existia
> porque o runbook foi escrito antes de haver o que executar; se tivesse sido documentado depois,
> provavelmente descreveria o caminho feliz.

### 3.26 A progressão dos erros como diagnóstico

**Episódio**: o primeiro `terraform apply` real, disparado pelo GitHub Actions, falhou três vezes
seguidas — com erros **diferentes** a cada tentativa:

| # | Erro | Significado |
|---|---|---|
| 1 | `Input required and not supplied: aws-region` | As variables do repositório não existiam |
| 2 | `Could not load credentials from any providers` | Variables ok; a role ainda não existia |
| 3 | `The web identity token provided could not be validated` | Role existe; o token não valida |

> Cada erro novo confirmou que a etapa anterior tinha sido resolvida. Num pipeline com autenticação
> federada, a mensagem de erro funciona como indicador de progresso — e vale registrar isso porque
> a reação instintiva a "falhou de novo" é assumir que nada mudou.

**Causa provável do terceiro erro**: o `oidc.tf` fixava o thumbprint
`6938fd4d98bab03faadb97b34396831e3780aea1`. É um valor real, mas **antigo** — corresponde a um
certificado do GitHub já rotacionado, e circula amplamente em tutoriais e respostas de fórum. Foi
reproduzido no código gerado sem verificação.

Correção: ler o certificado atual via `data.tls_certificate`, mantendo os thumbprints históricos
conhecidos na lista.

> Achado metodológico desconfortável e útil: **valores constantes copiados de conhecimento
> disponível envelhecem silenciosamente**. Um thumbprint, um ARN de policy gerenciada, um ID de
> AMI — todos parecem estáveis, nenhum é. O código gerado ficou correto na estrutura e errado num
> literal, e o erro só apareceu na primeira execução real contra a AWS.
>
> A lição prática não é "não use constantes", e sim: **onde existe um data source que descobre o
> valor, prefira-o à constante** — mesmo que a constante esteja certa hoje.

### 3.27 A permissão que faltava por causa de uma decisão posterior

**Episódio**: ao retomar a sessão para desbloquear o OIDC, a revisão da trust policy levou à leitura
da *permission policy* da mesma role. Ela concede `ec2:*`, `iam:*` granular, `ssm:*` e `s3:*` do
state — mas **nenhuma ação de `rds`**.

A causa é temporal, não descuidada: a policy foi escrita quando valia **D-10** (PostgreSQL em
container no próprio EC2). A revisão 9 substituiu D-10 por **D-37** (RDS gerenciado), criou o módulo
`database` com `aws_db_instance`, `aws_db_subnet_group` e `aws_db_parameter_group` — e o bootstrap,
que é um stack **separado**, não foi revisitado.

> **O-14 — Mudança de arquitetura tem raio de alcance maior que o módulo que ela altera.** A
> migração para RDS foi tratada como uma mudança no stack principal: módulo trocado, requisitos
> revisados, runbook atualizado, risco fechado. O bootstrap ficou fora do raio de revisão porque é
> outro stack, com outro state, executado por outro caminho. O erro só apareceria no primeiro
> `apply` do CI a chegar no módulo `database` — depois de o OIDC ser resolvido, isto é, um bloqueio
> escondido atrás de outro.
>
> Regra prática derivada: quando uma decisão troca um recurso por um serviço gerenciado, **a lista
> de permissões do agente que aplica a infraestrutura é sempre parte do raio da mudança**, mesmo
> quando mora em outro stack.

Correção aplicada ao `oidc.tf` do bootstrap: `rds:*`, `iam:CreateServiceLinkedRole` (o RDS cria a
service-linked role na primeira instância da conta) e `kms:DescribeKey` + `kms:CreateGrant`
(`storage_encrypted = true` com a chave padrão `aws/rds`).

Detalhe de oportunidade: a correção entra **sem custo de execução extra**, porque o bootstrap já
precisava ser reaplicado para levar a correção do thumbprint (3.26). Dois defeitos de origens
distintas, um único `terraform apply`.

### 3.28 O reprodutor mínimo que não reproduzia

**Episódio**: com o OIDC bloqueado, o usuário reduziu o problema ao menor caso possível — apagou os
4 workflows do projeto (commit `0f2a224`, 296 linhas removidas) e colocou no lugar o `main.yml`, o
workflow de exemplo da documentação da AWS. A estratégia é a correta: isolar a variável.

O exemplo, porém, trazia o ARN da role no campo errado:

```yaml
role-session-name: arn:aws:iam::594116288641:role/github-actions   # errado
role-to-assume:    arn:aws:iam::594116288641:role/github-actions   # certo
```

`role-session-name` é apenas o rótulo da sessão. Sem `role-to-assume`, a action não assume role
nenhuma — e o erro observado foi `Could not load credentials from any providers`, que é uma falha
**anterior** ao OIDC. Mais dois defeitos no mesmo arquivo: `aws-region: east-1` (região inexistente)
e o step final copiando um `index.html` que não existe para um bucket com o nome no placeholder.

> **O-15 — Um reprodutor mínimo defeituoso é pior que nenhum teste.** O teste falhou, e a falha
> parecia confirmar o bloqueio original — mas a mensagem era de outra causa, três camadas antes.
> Um reprodutor que erra *antes* do ponto sob investigação produz evidência que se parece com
> confirmação. O sintoma de que isso está acontecendo é a mensagem de erro **mudar de natureza**:
> "could not load credentials" e "web identity token could not be validated" descrevem etapas
> diferentes, e tratar as duas como "o OIDC falhou de novo" apaga a informação.
>
> Vale contrastar com 3.26, onde a progressão dos erros funcionou como diagnóstico. A diferença é
> que lá os erros avançavam; aqui o erro **regrediu**, e regressão de erro num teste recém-escrito
> aponta para o teste, não para o sistema.

**Efeito colateral do experimento**: o `main` ficou sem pipeline algum. Os 4 workflows continuam
recuperáveis em `8726974` (`git checkout 8726974 -- .github/workflows/`), mas enquanto o reprodutor
está no ar não há CI. A restauração deve esperar o smoke test passar, para não reintroduzir ruído.

> **O-16 — Depurar apagando é reversível em git e irreversível em atenção.** O artefato volta com um
> comando; o risco real é a restauração ficar esquecida, porque o pipeline ausente não gera alarme.
> Registrar a pendência num artefato do método é o que fecha esse buraco — foi o que se fez aqui.

### 3.29 O console adiantou o Terraform, e o código teve que correr atrás

**Episódio**: para destravar o OIDC, o usuário criou o provider e a role `github-actions`
**manualmente no console**. Funcionou — o smoke test ficou verde. Só que o bootstrap escrito em U5
previa criar exatamente esses dois recursos, e um OIDC provider é único por conta: o `apply` morreria
com `EntityAlreadyExists`.

Situação clássica de infraestrutura provisionada por dois caminhos que não se conhecem. Três saídas
possíveis, todas legítimas:

| Saída | Custo |
|---|---|
| Apagar o manual e deixar o Terraform criar | derruba a autenticação que acabou de funcionar |
| Deixar o manual fora do código | identidade do CI não versionada, drift permanente |
| **Adotar o manual no state** | o código passa a mandar em algo que já existe — e pode sobrescrevê-lo |

Escolhida a terceira, com `import` blocks (Terraform ≥ 1.5) em vez do comando `terraform import`.
A diferença importa: o comando é um passo manual, fora do código, que só quem estava presente sabe
que aconteceu; o bloco é **declarativo**, vive no repositório, e a adoção acontece durante o próprio
`apply`.

> **O-17 — Adotar é mais barato que recriar, e mais honesto que ignorar.** O reflexo diante de
> infraestrutura criada à mão é "apaga e deixa o Terraform fazer certo". Mas apagar o que funciona
> para recriar igual troca um risco conhecido (drift) por um desconhecido (a recriação não sair
> idêntica). O `import` block converte o recurso manual em recurso versionado sem tocar nele.

**Risco aceito e explicitado**: adotar a role significa que a trust policy do código passa a valer
sobre a trust policy que funciona. Se divergirem — a hipótese da capitalização do owner, ainda não
descartada — o apply quebra a autenticação recém-conquistada. O usuário optou por **confiar no
`plan`**: o diff da trust policy aparece antes do apply, e é avaliado ali.

**Segundo achado, sobre privilégio**: a role manual tem **`AdministratorAccess`** anexado. Isso
resolve por acidente o problema da seção 3.27 — a falta de permissões de `rds` deixa de causar
`AccessDenied`, porque não há permissão que falte. E cria outro: qualquer push na `main` passa a ter
poder total sobre a conta, o que amplifica o risco R-05 (apply automático sem gate).

> **O-18 — Uma permissão ampla demais não falha; ela silencia o teste.** A policy granular escrita em
> U5 tinha um defeito real (faltava `rds`), descoberto por leitura e não por execução. Com
> `AdministratorAccess`, esse defeito nunca se manifestaria — e continuaria lá, invisível, até o dia
> em que alguém restringisse a role. Privilégio excessivo não só aumenta o raio de dano: ele remove
> o mecanismo que informaria que a policy está errada.

### 3.30 O ambiente estava escrito em três lugares e em nenhum

**Decisão D-40**: provisionar **`dev` primeiro**, como ensaio da stack completa, e só depois `prod`.

A decisão parecia trivial — os dois ambientes já existiam em `envs/dev/` e `envs/prod/`, com tfvars
e backend próprios. Mas os workflows estavam **fixados em `prod`** em três pontos independentes:

| Arquivo | Acoplamento |
|---|---|
| `terraform-plan.yml` | `-backend-config=envs/prod/`, `-var-file=envs/prod/`, título do comentário no PR |
| `terraform-apply.yml` | `-backend-config=envs/prod/`, `-var-file=envs/prod/`, nome do job |
| `deploy-app.yml` | alvo do SSM por `tag:Name,Values=financial-control-prod`, em dois comandos |

O terceiro é o interessante. A tag vem de `local.name = "${project_name}-${environment}"`, então em
`dev` a instância se chama `financial-control-dev` — e o `send-command` não encontraria máquina
alguma. O sintoma não seria um erro de configuração legível: seria um deploy que **reporta sucesso
ao enviar o comando para zero alvos**.

> **O-19 — Parametrizar metade de uma dimensão é pior que não parametrizar.** A estrutura `envs/*`
> dava toda a aparência de um sistema multi-ambiente, e por isso ninguém desconfiaria que trocar de
> ambiente exigia editar workflows. O acoplamento sobrevive justamente onde a abstração parece
> completa: `envs/` cobria o Terraform, e o deploy por tag ficou fora do alcance dela.

Resolvido com uma única variável de precedência, repetida nos três workflows:

```yaml
TF_ENV: ${{ inputs.environment || vars.TF_ENVIRONMENT || 'dev' }}
```

Default `dev` enquanto só esse ambiente existe; promover a `prod` é criar a variable
`TF_ENVIRONMENT` no repositório, **sem tocar em código**. O `terraform-apply.yml` ganhou ainda um
input de escolha no `workflow_dispatch`, para aplicar um ambiente sob demanda.

### 3.31 O bootstrap que passou a caber no CI

**Decisão D-41**: o bootstrap deixa de ser executado manualmente e passa a rodar por
`workflow_dispatch` no GitHub Actions — revertendo parcialmente **D-26** (bootstrap manual e único).

O que mudou não foi o desenho, foi um fato: a role manual do CI recebeu `AdministratorAccess`. Com
OIDC funcionando e privilégio total, o Actions passou a poder fazer tudo que o operador humano faria
no CloudShell. A justificativa original de D-26 era a dependência circular — e ela continua real,
mas ela exige apenas que *alguém* crie o bucket antes do primeiro `init` remoto, não que esse alguém
seja uma pessoa.

**O obstáculo residual era o state**, e ele é conceitual: o state do bootstrap não pode morar no
bucket que o bootstrap ainda vai criar. Num operador humano isso se resolve sozinho, porque a máquina
dele persiste. Num runner efêmero, não. Solução adotada: baixar o state do S3 no início (tolerando a
ausência na primeira execução) e devolvê-lo no fim, com `if: always()` para que uma falha no meio do
apply não deixe recursos órfãos fora do state.

> **O-20 — Uma decisão de processo pode ter prazo de validade mais curto que a justificativa que a
> gerou.** D-26 dizia "manual" porque, no momento em que foi tomada, não havia identidade no CI capaz
> de criar IAM. A justificativa era técnica e verdadeira. Quando a identidade passou a existir, a
> decisão continuou registrada como se fosse princípio. Vale distinguir, no registro de decisões,
> **o que é restrição do momento do que é escolha de arquitetura** — a primeira deve ser reavaliada
> quando o momento muda, a segunda não.

**Gate preservado**: o disparo tem input `mode` com default `plan`. A primeira execução só mostra o
diff, publicado no *job summary*; aplicar exige um segundo disparo explícito. Isso mantém a revisão
humana da trust policy que era o ganho do caminho manual — o que se perdeu foi o terminal, não o
controle.

**Sem lock**: o state vai e volta por `aws s3 cp`, sem `use_lockfile`. Aceitável porque o disparo é
manual, único e serializado por `concurrency: terraform-bootstrap`. Não seria aceitável num stack de
aplicação contínua.

### 3.32 O plan que mostrou a armadilha

O `plan` do bootstrap veio limpo em quase tudo — `2 to import, 7 to add, 2 to change, 0 to destroy`
— e com uma linha que justificou sozinha a decisão de olhar antes de aplicar.

A trust policy da role criada no console:

```json
"Action": ["sts:AssumeRoleWithWebIdentity", "sts:TagSession"],
"StringLike": { "…:sub": "repo:RafaelMatheus@25590639/financial-control@1316467420:*" }
```

A que o código geraria:

```
"repo:RafaelMatheus/financial-control:ref:refs/heads/main"
"repo:RafaelMatheus/financial-control:pull_request"
```

O `sub` existente carrega **IDs numéricos** de owner e de repositório, formato que não é o padrão
documentado do GitHub e cuja origem não foi identificada. E `sts:TagSession` sumiria da lista de
ações.

> **O-21 — Adotar um recurso é herdar uma configuração que ninguém leu.** A seção 3.29 tratou o
> `import` block como conversão barata de recurso manual em recurso versionado. É — mas só o
> *endereço* é barato. O **conteúdo** do recurso adotado passa a ser ditado pelo código, e onde o
> código discorda da realidade, o apply impõe o código. Import não é "trazer para o Terraform": é
> "mandar o Terraform sobrescrever isto".

O que tornava o caso perigoso não era a divergência em si, e sim **em qual recurso** ela caía:

> **O-22 — Há uma classe de mudança que remove a capacidade de desfazer a própria mudança.** A role
> sob edição é a identidade que o CI usa para rodar o workflow que faria o conserto. Um apply que
> quebrasse a trust policy tirava, no mesmo instante, a ferramenta de reverter — a saída restante
> seria o console, manualmente. Mudanças no mecanismo de acesso merecem tratamento diferente de
> mudanças na infraestrutura acessada.

**Solução: união, não substituição.** Condições IAM com múltiplos valores são um OU, então a lista
passou a somar o padrão desenhado ao `sub` que já funciona, e `sts:TagSession` foi mantido. Se o
formato com IDs for o real, o pipeline continua; se for irrelevante, não custa nada. A dívida ficou
registrada na descrição de `var.extra_trusted_subs`: confirmado qual `sub` o token carrega, a lista
deve encolher para satisfazer RF-93.

> Note-se que a alternativa "descobrir primeiro qual é o `sub` real" também estava disponível e teria
> produzido uma policy mais enxuta. Escolheu-se a união porque ela é **segura sob as duas hipóteses**
> — e a assimetria de custo justifica: errar para o lado permissivo custa uma condição a mais numa
> policy; errar para o lado restritivo custa o acesso ao CI.

### 3.33 O stack dentro do stack

Um push que alterava `infra/terraform/bootstrap/oidc.tf` disparou o `terraform-apply.yml` da stack
principal, que morreu em `Failed to get existing workspaces: S3 bucket does not exist`.

A causa é puramente de layout: o bootstrap **mora dentro** de `infra/terraform/`, e o filtro
`paths: infra/terraform/**` não distingue os dois. São stacks separados — states separados, ciclos
de vida separados, e o bootstrap tem workflow próprio, por disparo manual. A árvore de diretórios
sugeria uma hierarquia ("bootstrap é parte da infraestrutura") onde a semântica exigia irmandade
("bootstrap é *outro* sistema, que por acaso está guardado ao lado").

> **O-23 — Filtros de path herdam a hierarquia de diretórios, não a de sistemas.** O acoplamento não
> estava em nenhuma decisão: emergiu de onde os arquivos foram postos. Sempre que dois artefatos com
> ciclos de vida independentes compartilham prefixo de caminho, qualquer automação baseada em path
> vai tratá-los como um só até que alguém escreva a exceção.

Resolvido com exclusão negativa — `'!infra/terraform/bootstrap/**'` — nos dois workflows de
Terraform. A alternativa estrutural seria mover o bootstrap para fora de `infra/terraform/`, o que
tornaria a separação visível na árvore em vez de escondida num filtro; não foi feito para não
invalidar os caminhos já documentados no runbook e no README.

**Nota sobre o custo do erro**: nenhum. A falha ocorreu no `init`, antes de qualquer recurso. Vale
observar por quê — a stack principal não consegue nem começar sem o backend, e o backend é produto do
bootstrap. A dependência circular que o bootstrap existe para quebrar funcionou aqui como
**proteção**: tornou impossível aplicar a stack principal fora de ordem.

### 3.34 O idioma que não atravessa a fronteira da API

O primeiro apply da stack de `dev` falhou em três recursos, com duas mensagens distintas e uma causa
única:

```
RDS:  The parameter DBSubnetGroupDescription must not contain non-printable control characters.
EC2:  Invalid rule description. Valid descriptions are strings less than 256 characters
      from the following set:  a-zA-Z0-9. _-:/()#,@[]+=&;{}!$*
```

Os culpados eram um travessão (`—`) numa descrição de subnet group e o apóstrofo de `Let's Encrypt`
em duas descrições de regra de security group. Nada de exótico: pontuação normal de texto escrito
por gente.

O detalhe que torna o caso instrutivo é que **o código está inteiro em português** — comentários,
mensagens, documentação — e isso nunca foi problema. Comentário é para humano e morre no parser.
`description` parece a mesma coisa, tem a mesma cara de prosa, e é o único desses campos que
**atravessa a fronteira do processo** e vira argumento de chamada de API.

> **O-24 — Texto para humano e texto para máquina são indistinguíveis na superfície.** Num arquivo
> `.tf`, comentário, `description` de variável e `description` de recurso têm a mesma aparência e
> destinos completamente diferentes: o primeiro morre no parser, o segundo fica no state, o terceiro
> vai para a AWS e obedece à gramática dela. A revisão humana não separa esses três porque a
> distinção não é visual — e a validação também não pega, porque `terraform validate` valida
> sintaxe, não as regras de conteúdo de cada serviço.

O erro só apareceu no `apply`. O `plan` da mesma configuração passou limpo, `33 to add, 0 to change,
0 to destroy` — porque o valor de uma descrição é sintaticamente válido; ele só é *inaceitável*, e
quem decide isso é o serviço, na hora de criar.

> **O-25 — Há uma classe de defeito que nenhum plan captura.** O `plan` é uma promessa negociada
> entre o Terraform e o state, não entre o Terraform e a API. Tudo que depende de validação do lado
> do serviço — formato de descrição, cota de recurso, disponibilidade de tipo de instância na AZ,
> nome já usado — atravessa o plan intacto e só falha na execução. Isso limita estruturalmente o que
> o gate de revisão do plan pode prometer, e vale saber disso antes de confiar nele como se fosse
> uma verificação completa.

Varredura preventiva no resto da árvore encontrou mais uma ocorrência, em `outputs.tf` — mas
descrição de output é local ao Terraform e nunca vai para a AWS. Ficou como está: corrigir seria
obedecer a uma regra que não se aplica ali.

**Sobre o apply parcial**: os recursos criados antes da falha permaneceram, e o state no S3 os
registra. Reaplicar continua de onde parou, sem recriar nada — que é exatamente o comportamento que
o passo *Guardar o state* com `if: always()` (3.31) existe para garantir.

### 3.35 Quatro applies para um plan

A stack de `dev` subiu na **quarta** tentativa. O `plan` era o mesmo nas quatro, e passou limpo nas
quatro — `33 to add, 0 to change, 0 to destroy`. Cada falha foi uma rejeição do lado do serviço:

| # | Erro | Natureza |
|---|---|---|
| 1 | `must not contain non-printable control characters` / `Invalid rule description` | gramática de conteúdo do campo |
| 2 | `FreeTierRestrictionError: backup retention period exceeds the maximum` | plano comercial da conta |
| 3 | `Cannot find version 16.6 for postgres` | catálogo do serviço mudou |
| 4 | — | sucesso |

São três categorias completamente diferentes de restrição, e **nenhuma delas é expressável no
Terraform**. É a confirmação empírica de O-25: o plan negocia com o state, não com a API.

> **O-26 — A taxa de acerto do plan é uma função de quanto da validação mora no provedor.** Para
> recursos cuja criação é essencialmente estrutural (VPC, subnet, route table), o plan é quase
> profético — todos os 20 e tantos passaram de primeira. Para recursos com regras de negócio do lado
> do serviço (RDS, com catálogo de versões, cotas e plano comercial), ele é pouco mais que uma
> intenção declarada. Não é defeito do Terraform: é o limite de qualquer ferramenta que planeja
> contra um modelo local de um sistema remoto.

Vale notar o formato do progresso: **as falhas avançaram**, cada uma mais fundo que a anterior — o
mesmo padrão de 3.26, e o mesmo valor diagnóstico. Erro novo é progresso; erro repetido é que seria
motivo de preocupação.

**Sobre a versão fixada**: `engine_version = "16.6"` não estava só desatualizada, estava
**contradizendo o próprio módulo**, que já trazia `auto_minor_version_upgrade = true`. O código
declarava simultaneamente "quero a menor mais recente automaticamente" e "quero exatamente a 16.6".
Passou por geração, revisão e um plan sem que a contradição aparecesse, porque cada linha estava
correta isoladamente.

> **O-27 — Contradições entre atributos do mesmo recurso não têm quem as revise.** Erro de sintaxe o
> parser pega; erro de valor o serviço pega. Duas configurações válidas que se anulam não são
> detectadas por ninguém — só pelo comportamento, meses depois, quando alguém pergunta por que o
> banco não atualizou sozinho.

**Sobre o free tier e o risco R-01**: a conta está no plano Free Tier, que recusou retenção de 7
dias. Em `dev` a redução para 1 dia é inconsequente — não há dado real. Em `prod`, é a reabertura de
um risco que o projeto tinha declarado **fechado** na revisão 9, quando a migração para RDS
gerenciado trouxe backup nativo. Registrado como decisão pendente, não como detalhe de configuração:
a diferença entre 1 e 7 dias de retenção é a diferença entre perder um dia de lançamentos financeiros
e perder uma semana.

### 3.36 O exemplo que passava por coincidência

Primeira execução dos testes de propriedade de `Dinheiro`: **falha imediata**, no primeiro caso
gerado.

A especificação, escrita na Functional Design, dizia que `dividirEm` põe o resíduo **na última
parte**, e trazia o exemplo canônico:

```
R$ 100,00 em 3  ->  33,33  33,33  33,34
```

O exemplo está certo. A regra que ele ilustra, não. O property-based testing encontrou
`R$ 10.000.000.000,00 em 6` — e o shrinking reduziu de 118 partes para 6, entregando o caso mínimo
pronto para leitura. Ali sobram **4 centavos**, e "todos na última" produz uma parte 0,04 acima das
outras.

O erro é conceitual, não de digitação: o resíduo de uma divisão em `n` partes vale até `n-1`
centavos. O exemplo de 100,00 em 3 tem resíduo de exatamente um centavo, então **ilustrava a regra
errada com o resultado certo**.

O caso que torna o defeito concreto no domínio: R$ 1,19 em 120 parcelas. A implementação original
daria 119 parcelas de zero e uma de R$ 1,19.

> **O-28 — Um exemplo confirma; ele não delimita.** O exemplo escolhido à mão para ilustrar uma
> regra tende a ser o mais legível, e o mais legível costuma ser aquele em que a regra errada e a
> certa coincidem. Foi por isso que o defeito atravessou a Functional Design, a revisão do design e
> a escrita do código: em todos esses momentos, a única evidência disponível era o exemplo — e o
> exemplo passava.
>
> Onde a geração aleatória ganha não é em achar casos exóticos: é em não ter preferência por casos
> bonitos.

**Sobre o shrinking** (PBT-08): o primeiro contraexemplo foi com 118 partes, número em que ninguém
raciocina. O shrinking devolveu 6, e com 6 dá para fazer a conta de cabeça e ver o erro. Sem isso, o
teste teria dito "falhou" e a depuração começaria do zero.

**Correção**: aritmética em centavos inteiros, com os centavos que sobram distribuídos **um por
parte, nas últimas**. Preserva o exemplo do design — 100,00 em 3 continua dando 33,33 / 33,33 /
33,34 — e limita a diferença entre partes a um centavo de verdade.

> Vale notar o custo evitado. `dividirEm` só tem consumidor em **U3**, a unidade mais complexa do
> sistema, no meio do parcelamento. A decisão de escrever a função e suas propriedades em U1, mesmo
> sem consumidor, foi tomada por economia de contexto (§3 de `business-rules.md`) — e acabou pagando
> antes disso: o defeito foi encontrado num arquivo de 60 linhas, e não no meio de faturas,
> competências e contas a pagar.

---

## 4. Dados quantitativos do processo

### 4.1 Esclarecimento

| Métrica | Valor |
|---|---|
| Perguntas de esclarecimento (Requirements Analysis) | 17 |
| Rodadas de perguntas | 4 blocos + 1 resolução de contradição + 3 rodadas de revisão |
| Contradições detectadas automaticamente | 1 (multi-usuário vs. Security desligada) |
| Ambiguidades detectadas e resolvidas | 4 (arredondamento, interface, permissão de cartão de grupo, fechamento vs. vencimento) |
| Perguntas em que o usuário respondeu com outra pergunta | 2 (AWS Well-Architected; localização do Terraform) |
| Rodadas de pergunta rejeitadas pelo usuário para reformulação | 2 |

### 4.2 Artefatos

| Métrica | Valor |
|---|---|
| Requisitos funcionais ativos | 93 (RF-01 a RF-97; RF-12, RF-13, RF-14 e RF-15 removidos) |
| Histórias de usuário | 60 ativas (57 de épico + 3 jornadas); 3 removidas na rev. 8 |
| Épicos | 11 |
| Personas | 1 (com 4 contextos) |
| Cobertura requisito ↔ história | 68/68 requisitos de domínio ativos |
| Componentes de aplicação | 12 (11 features + common) |
| Entidades / agregados | 15 / 12 |
| Contrato OpenAPI | 31 paths, 51 operações, 39 schemas |
| Alvos de property-based testing | 2 (eram 3 antes da rev. 8) |
| Unidades de trabalho | 5 |
| Histórias atribuídas a unidades | 60/60 (nenhuma órfã, nenhuma duplicada) |
| Componentes divididos entre unidades | 1 (`gasto`) |
| Stages da Inception executadas | 7 de 7 (nenhuma pulada) |
| Stages condicionais avaliadas | 6 |
| Stages condicionais a executar | 6 (nenhuma pulada) |
| Unidades de trabalho previstas | 5 |
| Gates de aprovação restantes | ~20 |
| Decisões ainda abertas ao fim da Inception | 14 (todas com stage-alvo designada) |
| Requisitos não-funcionais | 17 |
| Cenários de usuário | 10 |
| Casos de borda e erro | 16 |
| Premissas registradas | 11 |
| Decisões técnicas | 39 (34 fechadas, 5 adiadas); 2 revertidas (D-10, D-36) |
| Riscos registrados | 5 (R-01 **resolvido** na rev. 9) |
| Revisões do documento de requisitos | 9 |

### 4.3 Evolução do escopo

| Revisão | RF ativos | Evento |
|---|---|---|
| 1 | 44 | Esclarecimento inicial (17 perguntas) |
| 2 | 54 | Infraestrutura (AWS EC2 + Terraform) |
| 3 | 53 | Generalização Casa→Grupo; **RF-12 removido** |
| 4 | 76 | Contas a pagar + Investimentos |
| 5 | 79 | Contrato de API como entregável (OpenAPI 3.1) |
| 6 | 92 | CI/CD e provisionamento (GitHub Actions, OIDC, ECR, SSM) |
| 7 | 95 | Casos de borda resolvidos nas User Stories (RF-94 a RF-96) |
| 8 | **93** | **Rateio removido** — RF-13 a RF-15 eliminados, RF-97 adicionado |

---

## 5. Observações metodológicas consolidadas

Registradas para desenvolvimento no artigo. São hipóteses de trabalho, não conclusões — o ciclo
ainda não passou da fase de Inception.

**O-01 — Brownfield sem domínio é um caso de fronteira não previsto.** A taxonomia
brownfield/greenfield do método classifica por presença de código, não por presença de regra de
negócio. O resultado foi uma engenharia reversa cujo principal achado foi a ausência do objeto de
análise. Ainda assim, a stage produziu valor real — ver O-02.

**O-02 — O valor da engenharia reversa em bases pequenas está na detecção de inconsistência, não no
mapeamento.** Sobre 14 arquivos, o mapeamento estrutural era trivial. O achado que justificou a
stage foi a incompatibilidade entre `ddl-auto: validate` e a ausência de ferramenta de migration —
uma relação entre dois pontos distantes da configuração, não visível na leitura de nenhum deles
isoladamente.

**O-03 — Perguntas estruturadas revelam escopo preexistente, não criam escopo.** A ampliação de 2
capacidades declaradas para 12 grupos de requisitos veio integralmente de respostas do usuário. O
requisito de maior complexidade do sistema (compartilhamento em grupo) apareceu como texto livre
numa resposta sobre outro tema.

**O-04 — Nomes de extensão podem induzir opt-out acidental.** Desligar a extensão "Security" não
remove autenticação, mas o nome sugere o contrário. A detecção de contradição do método corrigiu o
caso, mas por checagem de consistência entre respostas, não por desenho do opt-in.

**O-05 — Opt-ins que pressupõem vocabulário especializado geram decisão desinformada.** O opt-in da
extensão Resiliency assume conhecimento do AWS Well-Architected Framework. O usuário precisou
perguntar o que era antes de poder decidir.

**O-06 — Revisões de vocabulário de domínio propagam para as fronteiras do modelo.** Renomear
"Casa" para "Grupo" — apresentado como mudança terminológica — tornou um requisito redundante
(RF-12, removido) e resolveu por eliminação uma decisão de design que estava adiada (D-07).

**O-07 — Divergência entre linguagem natural do usuário e semântica do domínio é fonte de erro
silencioso.** O usuário disse "vencimento" onde o domínio exige "fechamento". Escrever o requisito
literalmente teria produzido um cálculo de fatura incompatível com o extrato bancário real,
detectável apenas em produção.

**O-08 — Previews comparativos deslocam decisões de modelagem para o usuário.** Decisões que
normalmente ficariam com o desenvolvedor (formato de lançamento de parcelamento, modelo de rateio,
critério de corte da fatura) foram tomadas pelo usuário quando apresentadas como estruturas de
dados concretas lado a lado, em vez de descrições em prosa.

**O-09 — Decisões contra a recomendação são legítimas; o valor do método está em nomear o risco.**
PostgreSQL no EC2 em vez de RDS foi escolha consciente do usuário. O método não bloqueou — registrou
R-01 com severidade, mitigação acordada e a ressalva de que RF-54 é obrigatório na prática apesar
da prioridade "S".

**O-30 — Constantes copiadas de conhecimento pré-existente envelhecem silenciosamente.** O
thumbprint do OIDC provider do GitHub foi gerado com um valor real, amplamente publicado, e já
rotacionado. A estrutura do código estava correta; o literal, não. Onde existir um data source que
descubra o valor em tempo de execução, ele é preferível à constante — inclusive quando a constante
está correta no momento da escrita.

**O-31 — Mensagens de erro sucessivamente diferentes indicam progresso.** Três tentativas de apply
falharam com três erros distintos, cada um confirmando a resolução do anterior. Em cadeias de
autenticação federada, a evolução da mensagem é o principal sinal de avanço — mais informativo que
o sucesso ou fracasso binário.

**O-27 — Mitigação estrutural supera mitigação incremental.** O risco R-01 acumulou mitigações
parciais ao longo de várias stages — volume EBS separado, `prevent_destroy`, runbook de restauração
— e foi eliminado por uma troca de componente decidida por outro motivo. Riscos que persistem
apesar de mitigações sucessivas podem ser sinal de que o problema está na escolha do componente,
não na sua configuração.

**O-28 — IaC tem fronteira no estado interno do recurso provisionado.** O Terraform cria o banco
mas não consegue criar uma role dentro dele: o recurso fica em subnet privada e o Terraform roda
fora da VPC. Provisionar infraestrutura e configurar o que roda dentro dela são problemas distintos,
e a automação completa esbarra em o provisionador não ter — nem dever ter — rota de rede até o
recurso.

**O-29 — Salvaguardas triviais pegam os erros mais caros.** `aws sts get-caller-identity` foi
escrito no runbook quase como formalidade, antes de existir o que executar. Na primeira execução
real, detectou que a CLI apontava para outra conta — evitando provisionar toda a infraestrutura no
lugar errado. Runbooks escritos antes da execução tendem a incluir verificações que os escritos
depois omitem, por já conhecerem o caminho feliz.

**O-25 — Decomposição por capacidade de entrega custa coordenação, e o custo precisa ser
explicitado.** Dividir o componente `gasto` entre duas unidades só é vantajoso porque veio
acompanhado de um acordo de schema — a entidade nasce com as colunas do futuro, nuláveis. Sem esse
acordo, a divisão exigiria `ALTER TABLE` numa tabela em uso, e o custo superaria o da alternativa.
A decisão de fronteira e o acordo de coordenação são inseparáveis.

**O-26 — Dependências entre unidades podem ser criadas por uma única história.** U4 depende de U3
apenas pela jornada J-02; as outras 15 histórias dependem só de U1 e U2. A dependência parece
estrutural no nível de componentes e se revela pontual no nível de histórias — e são justamente as
jornadas transversais, que existem para cruzar fronteiras, que criam o acoplamento não óbvio.

**O-22 — Em tarefas longas, o relato do trabalho pode substituir o trabalho.** O modelo afirmou
estar aguardando respostas a perguntas que não havia feito, apesar de a instrução de usar o tool
estar em memória e ter sido seguida por dezenas de interações. Não foi esquecimento nem
discordância — foi inércia narrativa ao resumir pendências. A correção veio do usuário, não de
auto-verificação.

**O-23 — Ordem por afinidade conceitual diverge de ordem por acoplamento real.** A Workflow
Planning ordenou as unidades intuitivamente (gastos antes de cartões); o grafo de dependências
mostrou o oposto. Só a construção explícita do grafo, duas stages depois, revelou a divergência.

**O-24 — Decisões defensáveis isoladamente colidem quando combinadas.** D-14 (fatura vira conta a
pagar) e D-31 (fatura é entidade persistida) são corretas separadamente; juntas, criam duas fontes
de verdade sobre pagamento. É o mecanismo de O-16 aplicado entre decisões, e não entre áreas
funcionais — e sugere que revisões de consistência entre decisões acumuladas deveriam ser um passo
explícito do método.

**O-19 — A opção correta pode estar na mesa e ser rejeitada.** O modelo "sem divisão, só
visibilidade" foi oferecido explicitamente na primeira rodada de esclarecimento e recusado em favor
de "divisão configurável". A correção veio sete revisões depois. Não foi falta de pergunta nem
opção ausente — foi o domínio ainda não estar claro para nenhuma das partes. Sugere que a qualidade
das opções apresentadas não garante a qualidade da escolha, e que revisões tardias de premissa
básica devem ser esperadas, não tratadas como falha do processo.

**O-20 — A rastreabilidade é o que torna a correção segura, não a documentação em si.** Remover o
rateio exigiu localizar tudo que dependia dele: 3 requisitos, 3 histórias, 1 caso de borda,
2 jornadas, 10 histórias dependentes, 3 premissas, 2 requisitos não-funcionais e 1 entidade do
modelo. A matriz requisito ↔ história e as referências cruzadas permitiram fazer isso
mecanicamente. Sem elas, a remoção deixaria resíduos que só apareceriam na implementação.

**O-21 — O valor da especificação antes da implementação é medido no custo do erro tardio.** A
remoção do rateio custou reescrita de documentos porque nada tinha sido construído. A mesma
mudança na Code Generation teria custado código descartado e migrations; pós-deploy, migração de
dados e uma tabela órfã em produção. O método não evitou o erro — tornou-o reversível.

**O-17 — Planejar exige antecipar o resultado da stage que se está planejando.** A Workflow
Planning precisa da decomposição em unidades para estimar esforço e ordenar o trabalho, mas a
decomposição é produto da Units Generation, que vem depois. A ordem das stages é coerente quanto à
autoridade da decisão, mas cria dependência invertida quanto ao conteúdo. A saída foi declarar a
antecipação como premissa explícita.

**O-18 — Estruturas per-unit pressupõem autonomia entre unidades.** NFR Requirements e
Infrastructure Design são definidas pelo método como stages por unidade — premissa razoável em
microsserviços, insustentável num monolito single-module com um classpath, um deploy e um banco.
O método não oferece mecanismo para declarar que uma stage per-unit tem, num dado projeto, escopo
global; a adaptação teve de ser justificada fora do vocabulário do método.

**O-14 — Convenções de domínio podem ser projetadas sobre requisitos que não as contêm.** A
hierarquia administrador/membro é tão comum em sistemas com grupos que foi assumida durante a
análise de personas, apesar de RF-16 e RF-08 dizerem explicitamente o contrário. Só a verificação
requisito a requisito desfez a projeção.

**O-15 — Regras que proíbem operações sobre estado terminal exigem uma operação inversa.** Bloquear
alterações em fatura paga (RF-95) criava um beco sem saída para quem errou; RF-94 (desmarcar o
pagamento) nasceu dessa constatação, sem ter sido pedido. A proibição isolada parece completa quando
escrita, e só se revela incompleta ao imaginar o usuário que precisa corrigir algo.

**O-16 — Questões de modelagem vivem nas costuras entre áreas funcionais.** Três jornadas
transversais levantaram três questões ausentes de todos os 60 requisitos e de todas as 60 histórias
de épico — incluindo uma (rateio por parcela, não por compra) que altera a estrutura do modelo de
dados. A decomposição funcional, por construção, não olha para as fronteiras entre suas próprias
partições.

**O-11 — Fases declaradas como placeholder criam expectativa de cobertura que não existe.** A fase
de Operations aparece no diagrama de três fases do `CLAUDE.md` como etapa do processo; só ao abrir
`operations/operations.md` se descobre que é um placeholder vazio e que o fluxo termina em Build and
Test. O usuário havia decidido "IaC entra neste ciclo" — o que razoavelmente sugere infraestrutura
no ar ao final, quando entra apenas o código dela.

**O-12 — Infraestrutura como código nunca é 100% código.** A dependência circular do bootstrap
(o CI precisa de state remoto e role IAM, que são infraestrutura que só o Terraform criaria) é
estrutural, não uma limitação de ferramenta. Existe sempre um degrau inicial manual, e a qualidade
da documentação desse degrau determina se o projeto é reproduzível por outra pessoa.

**O-13 — Uma pergunta informativa do usuário pode revelar uma lacuna do método.** *"O provisionamento
vai acontecer em qual momento?"* não era um pedido de mudança — era uma dúvida. Respondê-la exigiu
ler as regras do método e constatar que a resposta era "não acontece", o que gerou 13 requisitos
novos (RF-81 a RF-93) e uma decisão de arquitetura de entrega. Sugere que perguntas de
esclarecimento do usuário sobre o *processo* merecem o mesmo rigor de investigação que perguntas
sobre o produto.

**O-10 — Decisões de negócio definem semântica de indicador.** "Aporte conta como gasto" transforma
o balanço em medida de fluxo de caixa e não de variação patrimonial. Registrar isso explicitamente
evita reinterpretação em stages posteriores.

---

## 6. Estado atual

**Fase**: INCEPTION
**Stage**: CONSTRUCTION — **U5 Infraestrutura**. Infrastructure Design aprovada; Code Generation com
o código gerado e o gate de aprovação pendente; **bootstrap manual em andamento, bloqueado no OIDC**.
**Próxima unidade**: U1 — Fundação (`common`, `usuario`, `grupo`).

**Fase de Inception encerrada**: 7 stages concluídas e aprovadas — Workspace Detection, Reverse
Engineering, Requirements Analysis (9 revisões), User Stories, Workflow Planning, Application
Design, Units Generation. Nenhuma pulada.

**Bloqueio ativo**: o GitHub Actions falha em `sts:AssumeRoleWithWebIdentity` —
*"The web identity token provided could not be validated"*. Duas correções aguardando um único
`terraform apply` do bootstrap: thumbprint lido via `data.tls_certificate` (3.26) e permissões de
`rds` na policy do CI (3.27). Hipótese ainda não descartada: capitalização do owner no claim `sub`.

**Decisões ainda em aberto** (adiadas para stages posteriores): mecanismo de autenticação (D-02),
fronteira do fechamento em dia 29–31 (D-04, parcial), mecanismo de recorrência (D-19), mecanismo de
fechamento de fatura (D-20), base de cálculo do "realizado" do orçamento (J-02), `Fatura.status`
persistido ou derivado (D-33). Fechadas na Construction: D-11, D-12, D-34 a D-39.

**Insumo pendente do usuário**: `domain_name` — sem ele não há TLS e a API responde por HTTP no IP
elástico.

**Questões abertas pelas jornadas transversais**: J-02 (base de cálculo do "realizado" do orçamento)
segue aberta, destino Functional Design de U4. J-01 (rateio por parcela) foi **extinta** na revisão 8
com a remoção do rateio; J-03 (distinção "total do grupo" vs. "total pessoal") foi **resolvida** por
RF-97 e D-28.
