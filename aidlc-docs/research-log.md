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

**Resultado consolidado (revisão 4)**: 76 requisitos funcionais ativos (RF-01 a RF-77, com RF-12
removido), 17 requisitos não-funcionais, 10 cenários de usuário, 16 casos de borda, 11 premissas,
20 decisões técnicas e 4 riscos.

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
| Requisitos funcionais ativos | 76 (RF-01 a RF-77, RF-12 removido) |
| Requisitos não-funcionais | 17 |
| Cenários de usuário | 10 |
| Casos de borda e erro | 16 |
| Premissas registradas | 11 |
| Decisões técnicas | 20 (13 fechadas, 7 adiadas) |
| Riscos registrados | 4 |
| Revisões do documento de requisitos | 4 |

### 4.3 Evolução do escopo

| Revisão | RF ativos | Evento |
|---|---|---|
| 1 | 44 | Esclarecimento inicial (17 perguntas) |
| 2 | 54 | Infraestrutura (AWS EC2 + Terraform) |
| 3 | 53 | Generalização Casa→Grupo; **RF-12 removido** |
| 4 | 76 | Contas a pagar + Investimentos |

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

**O-10 — Decisões de negócio definem semântica de indicador.** "Aporte conta como gasto" transforma
o balanço em medida de fluxo de caixa e não de variação patrimonial. Registrar isso explicitamente
evita reinterpretação em stages posteriores.

---

## 6. Estado atual

**Fase**: INCEPTION
**Stage**: Requirements Analysis — aguardando aprovação (revisão 4)
**Próxima stage prevista**: User Stories

**Stages concluídas**: Workspace Detection, Reverse Engineering (aprovada), Requirements Analysis
(4 revisões, gate pendente).

**Decisões ainda em aberto** (adiadas para stages posteriores): mecanismo de autenticação (D-02),
estrutura de pacotes (D-03), regra de fronteira do fechamento (D-04), springdoc-openapi (D-06),
visibilidade de histórico em grupo (D-13), mecanismo de recorrência (D-19), mecanismo de fechamento
de fatura (D-20), dimensionamento da EC2 (D-11), mecanismo de deploy (D-12).
