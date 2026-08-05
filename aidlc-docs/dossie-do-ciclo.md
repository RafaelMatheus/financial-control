# Dossiê do ciclo AI-DLC — financial-control

> Registro consolidado do que foi construído, do método, e das 84 decisões que moldaram o
> sistema — cada uma com a alternativa recusada ao lado.
>
> **Ciclo encerrado em 2026-08-03.** Front-end acrescentado em 2026-08-05, fora do ciclo.
>
> Versão navegável, com filtro por unidade:
> https://claude.ai/code/artifact/16b5bfe9-e6e7-4592-8005-54f35fa16de0

| | |
|---|---|
| Decisões registradas | **84** (D-01 a D-84) |
| Regras de negócio | **107** |
| Testes | **199**, 0 falhas |
| Unidades de trabalho | **5** |
| Decisões revertidas | **1** |

---

## 1. Dois repositórios, uma origem

O front-end foi excluído do repositório do backend logo na Requirements Analysis — seria
construído separadamente, consumindo a API. Foi o que aconteceu, dois dias depois de o ciclo
encerrar.

| Repositório | Papel | Stack |
|---|---|---|
| [`financial-control`](https://github.com/RafaelMatheus/financial-control) | Backend, infraestrutura e a documentação do ciclo | Kotlin 2.1 · Spring Boot 3.5 · JVM 21 · PostgreSQL 16 (RDS) · Terraform · GitHub Actions |
| [`financial-control-web`](https://github.com/RafaelMatheus/financial-control-web) | Front-end, servido pela mesma origem da API | React 19 · Vite · TypeScript · tanstack-query · zod |

**Ambiente `dev`**: http://52.73.89.203/ — front na raiz, API sob `/api/`.

### Por que mesma origem

O nginx da instância serve `/` e faz proxy de `/api/`. **Não é preferência de arquitetura** —
resolve dois bloqueios concretos:

| Problema | Por que existiria |
|---|---|
| **CORS** | O backend não declara `.cors(...)`. Um front em outra origem seria bloqueado em toda requisição |
| **Mixed content** | `dev` responde por HTTP. Um front em HTTPS não pode chamar API em HTTP — restrição do navegador, **não contornável por código** |

---

## 2. O método, e a lacuna que ele não declara ter

O AI-DLC organiza o trabalho em fases com portões de aprovação explícitos. Cada stage produz
artefatos versionados, e nenhuma avança sem aprovação.

| Fase | Situação |
|---|---|
| **Inception** | 7 stages, nenhuma pulada. Engenharia reversa, 97 requisitos, 57 histórias, 3 jornadas, decomposição em 5 unidades |
| **Construction** | Por unidade: Functional Design, NFR Design, Code Generation — cada uma com plano numerado e portão. Depois, Build and Test |
| **Operations** | ⬜ **Placeholder vazio.** As regras são explícitas: o fluxo termina após a Build and Test |

### A lacuna

O método entrega o Terraform **escrito** e não o aplica. A stage vazia é honesta; o que não
estava declarado era a **consequência** — que o artefato de infraestrutura ficaria parado e
ninguém seria responsável por aplicá-lo.

A lacuna apareceu **na Requirements Analysis**, e não numa revisão do método: veio de uma
pergunta do usuário sobre *processo*, cuja resposta exigiu ler as regras e constatar que a coisa
não acontecia.

**Gerou 13 requisitos novos** (RF-81 a RF-93), **seis decisões** (D-21 a D-26) e **uma unidade
inteira** que não existia no plano original.

> Vindo no começo, custou o mesmo que qualquer outro esclarecimento — e permitiu que a
> infraestrutura fosse a **primeira** unidade entregue, com o CI verde desde o início. Vindo na
> Build and Test, custaria o mesmo trabalho com quatro unidades já dependendo de um ambiente
> inexistente.

---

## 3. As decisões

Seleção das que mudaram o sistema. A lista canônica vive nos artefatos de cada stage.

**Marcações**: ✅ fecha uma decisão adiada · ⚠️ reverte comportamento já entregue.

### Inception

| ID | Decisão |
|---|---|
| D-01 | **Flyway** como migration, mantendo `ddl-auto: validate`. Resolve o débito bloqueante da engenharia reversa: a aplicação deixaria de subir na primeira `@Entity` |
| D-08 | Terraform no mesmo repositório. App e IaC mudam no mesmo PR |
| D-14 | Fatura de cartão **unificada com conta a pagar** — uma visão de vencimentos reunindo fatura, PIX, boleto e conta de serviço |
| D-15 | O **fechamento**, não o vencimento, determina a fatura de destino. Mantém os valores compatíveis com o extrato do banco |
| D-17 | Investimento por aportes + saldo manual, sem controle de ativos nem cotação. O rendimento é derivado |
| D-18 | O aporte conta como gasto no balanço. **Consequência declarada**: o balanço mede fluxo de caixa, não variação patrimonial |
| D-27 | Compartilhamento é **apenas visibilidade** — não há rateio. Removeu 3 requisitos, 3 histórias e uma entidade |
| D-28 | Total pessoal e total de grupo são grandezas distintas que **nunca se somam** |
| D-31 | Fatura é entidade persistida, não visão calculada |

### U5 — Infraestrutura

| ID | Decisão |
|---|---|
| D-21 | GitHub Actions como CI/CD, fechando a lacuna de provisionamento do método |
| D-22 | **OIDC** para autenticar na AWS, sem credencial de longa duração |
| D-24 | Deploy por **SSM Run Command**. Porta 22 fechada, nenhuma chave privada nos secrets |
| D-26 | Bootstrap manual e único, resolvendo o ovo-e-galinha do state remoto e da role |
| D-37 | **RDS gerenciado** em vez de container na EC2. Fechou o risco R-01 — que o Free Tier reabriu depois, recusando 7 dias de retenção |
| D-40 | Provisionar `dev` primeiro, como ensaio da stack completa |

### U1 — Fundação

| ID | Decisão |
|---|---|
| D-02 ✅ | JWT stateless, 24h, **sem refresh**. Girar o segredo passa a ser a única revogação possível |
| D-43 | Aritmética monetária com escala 2 e HALF_UP — o arredondamento de extrato bancário brasileiro |
| D-44 | Quem sai do grupo sofre **corte total** de visibilidade |
| D-49 | Bloqueio de 5 tentativas por 15 min, contador **em memória**. Único componente com estado do sistema |
| D-51 | Arquitetura hexagonal. Escolhida **contra a recomendação**, com a ressalva registrada |
| D-52 | **A porta de repositório não expõe método cru.** Não existe `findAll` nem `findById`: quem escrever consulta sem filtro não produz bug, produz **erro de compilação** |

### U2 — Lançamentos

| ID | Decisão |
|---|---|
| D-54 | Categoria tem escopo, simétrico ao do gasto. Sem isso, duas pessoas criariam duas "Mercado" no mesmo grupo |
| D-56 | As categorias iniciais nascem na primeira listagem. **Consequência aceita**: apagar todas as faz ressurgir |
| D-57 | Listagem paginada e **totais em operação própria** — para que o total não dependa da página aberta |
| D-62 | Lançamentos de ex-membros permanecem no total do grupo |
| D-63 | A porta cresce **por feature**, com período obrigatório no tipo. Não existe forma de construir a pergunta "todos os gastos" |
| D-64 | Totais somados no banco. Passam a existir duas aritméticas monetárias sem teste de comparação — **risco aceito por decisão**, com a alternativa registrada |
| D-66 | **Teste de arquitetura** reprova o build quando uma entidade com dono nasce fora do padrão. É D-52 uma camada acima: do compilador para o CI |

### U3 — Crédito

| ID | Decisão |
|---|---|
| D-67 | A entrada do parcelamento é o **valor total**. Resolveu uma contradição entre três documentos já aprovados |
| D-68 ⚠️ | A última parcela absorve todo o resíduo. **Reverte comportamento entregue e testado em U1** |
| D-69 ✅ | Dia inexistente cai para o último dia do mês. Uma regra serve fechamento, vencimento e recorrência |
| D-70 ✅ | O status PAGA é **derivado** da conta a pagar. Equivalência entre dois campos alguém precisa manter; derivação não |
| D-71 ✅ | **Job diário** fecha as faturas, idempotente e recuperável. Primeiro componente agendado do sistema |
| D-72 ✅ | A ocorrência recorrente **materializa ao ser tocada**. A consulta projeta; a linha só nasce com estado próprio |
| D-73 | O bloqueio de fatura paga **desce para o adaptador**. No serviço é para dar mensagem; embaixo é para impedir |
| D-74 | **Advisory lock** no job. Única vez no ciclo em que a lista do que quebra com escala horizontal **encolheu** |
| D-75 | O total da fatura passa a ser **calculado na leitura**. A invariante dependia de oito caminhos de escrita lembrarem de recalcular |

### U4 — Planejamento

| ID | Decisão |
|---|---|
| D-77 ✅ | Cada orçamento declara a **base do realizado**. Uma compra de R$ 1.200 em 12× aparece inteira em julho por uma base, e como R$ 100/mês pela outra |
| D-79 | O aporte não entra no realizado do orçamento. Ele não tem categoria — tem objetivo |
| D-81 | **Quem sabe somar um dado é quem é dono dele.** A porta de leitura entre unidades vive no domínio de quem possui a tabela |
| D-83 | Excluir aporte **subtrai do saldo**. Sem a simetria, excluir R$ 500 faria o rendimento *subir* R$ 500 — sem erro, sem log |

---

## 4. O que a verificação encontrou

**Nenhum defeito estava numa regra de negócio.** Todos moravam na cola entre o código e a
infraestrutura — o momento do *flush*, a ordem entre dois validadores, o escopo de vida de um
singleton.

| Un. | Sintoma | Causa |
|---|---|---|
| U1 | Cadastros simultâneos davam 500 em vez de 409 | `save()` só envia o INSERT no commit, depois de o `try/catch` sair de cena |
| U1 | E-mail com espaços dava 400 em vez de 409 | `@Email` roda antes da normalização e rejeita o que a regra manda remover |
| U1 | NPE nos testes seguintes ao de bloqueio | Contador em memória; o `TRUNCATE` não o alcança |
| U2 | Listagens simultâneas de conta nova davam 500 | Releitura dentro de transação **já abortada** pelo PostgreSQL |
| U2 | Um teste afirmava o oposto da regra | Defeito no teste — a regra fora escrita minutos antes |
| U3 | **40 testes vermelhos de uma vez** | Chave YAML duplicada. Nenhuma mensagem mencionava YAML |
| U3 | Teste de arquitetura reprovou código correto | Ele casava por **prefixo de nome**, achando que verificava relação de tipo |

> A última linha é a mais instrutiva. A regra existia justamente para **não depender de
> disciplina humana** — e passou a depender de uma convenção não escrita, que valeu por acaso em
> duas unidades e quebrou na primeira que não a seguiu.

### A regra que nasceu do primeiro erro

Em U1 os testes de integração foram **escritos e não executados** — não havia Docker na máquina.
O plano registrou o desvio e disse que a aprovação deveria esperar o CI. O CI reprovou **3 de 69**.

> *Um teste escrito e não executado é documentação, não verificação.*

Virou passo explícito nos planos seguintes:

| Unidade | CI na 1ª execução |
|---|---|
| U1 | ❌ 3 falhas em 69 |
| U2 | ❌ 2 falhas em 82 |
| U3 | ❌ 2 causas independentes |
| U4 | ✅ **verde** |

---

## 5. O que fica em aberto

**Nenhuma decisão do ciclo permanece adiada** — a última, J-02, fechou na Functional Design de U4.

| Item | Natureza |
|---|---|
| **TLS ausente em `dev`** — o tráfego é HTTP, inclusive a senha no login | Insumo pendente do usuário. **Bloqueante antes de dado real** |
| Retenção de backup em `prod` — o Free Tier recusou 7 dias | Decisão a tomar antes do primeiro dado real |
| `AdministratorAccess` na role do CI, com apply automático no merge | Risco registrado (R-05) |
| RF-29 e H-27 desatualizados por D-67 | Correção de texto; não afeta código |
| Contador de tentativas quebra com duas instâncias | Mantido deliberadamente — resolvê-lo exigiria armazenamento compartilhado |
| Duas aritméticas monetárias sem teste de comparação | Risco aceito em D-64, com a alternativa registrada |
| Formulários de cartão, orçamento, conta e objetivo no front | Escopo não implementado |

---

## 6. Onde está o resto

| Documento | Conteúdo |
|---|---|
| `research-log.md` | Registro cronológico e analítico — 51 seções, 50 observações metodológicas. Base para o artigo |
| `aidlc-state.md` | Estado do ciclo, stage a stage |
| `audit.md` | Toda entrada do usuário e toda resposta, com timestamp |
| `construction/*/` | Por unidade: functional-design, nfr-design, code |
| `construction/build-and-test/` | Instruções de build e teste, com os números reais do CI |
| `operations/operations.md` | O estado operacional e as pendências, em detalhe |
