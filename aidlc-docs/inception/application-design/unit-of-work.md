# Unidades de Trabalho

**Stage**: INCEPTION - Units Generation - Part 2
**Timestamp**: 2026-07-30T16:11:59Z

> **Terminologia**: este projeto é um **monolito single-module**. As unidades são **módulos
> lógicos** de desenvolvimento, não serviços implantáveis separadamente. Cada uma passa pelo loop
> completo da fase de Construction.

---

## 1. Decomposição

**5 unidades**, com fronteira definida por **capacidade de negócio** — cada uma termina com algo
demonstrável ao usuário.

```
U5 Infraestrutura   (paralelizavel — nao depende de nenhuma unidade de dominio)
     |
     |  recomendado executar cedo, para que as demais nascam com CI verde
     |
U1 Fundacao  -->  U2 Lancamentos  -->  U3 Credito  -->  U4 Planejamento
   common            categoria          cartao            receita
   usuario           gasto (a vista)    fatura            orcamento
   grupo                                conta             investimento
                                        compra
                                        gasto (cartao)
```

---

## 2. U1 — Fundação

| | |
|---|---|
| **Propósito** | Estabelecer identidade e o modelo de visibilidade sobre o qual todo o resto se apoia |
| **Componentes** | `common`, `usuario`, `grupo` |
| **Entidades** | `Usuario`, `Grupo`, `MembroGrupo` |
| **Requisitos** | RF-01 a RF-10, RNF-01, RNF-05, RNF-09, RNF-10 |
| **Histórias** | H-01 a H-08 (8) |
| **Depende de** | — |
| **Bloqueia** | U2, U3, U4 |

**Responsabilidades**
- Cadastro, autenticação e perfil de usuário
- Criação de grupos e gestão de membros, sem hierarquia
- **`Visibilidade`** — o predicado que isola dados, aplicado a toda consulta do sistema
- **`Dinheiro`** — value object com aritmética decimal exata
- **`ErroHandler`** — formato consistente de erro

**Critério de conclusão**
- [ ] Usuário se cadastra e autentica
- [ ] Usuário cria grupo, adiciona e remove membros
- [ ] Um usuário não acessa dados de outro fora das regras de visibilidade (H-03)
- [ ] `Dinheiro` passa nos testes de propriedade de soma e subtração
- [ ] Schema criado por migrations Flyway, com `ddl-auto: validate` passando

> **Unidade mais crítica do projeto.** `Visibilidade` e `Dinheiro` são usados por todas as demais —
> um erro aqui se propaga para o sistema inteiro. Também é onde o débito bloqueante da engenharia
> reversa se resolve: a primeira migration Flyway nasce nesta unidade.

---

## 3. U2 — Lançamentos

| | |
|---|---|
| **Propósito** | Registrar e consultar gastos à vista, pessoais e de grupo |
| **Componentes** | `categoria`, `gasto` (**à vista apenas**) |
| **Entidades** | `Categoria`, `Gasto` |
| **Requisitos** | RF-11, RF-16 a RF-22, RF-36 a RF-38, RF-97 |
| **Histórias** | H-09, H-13, H-14, H-15 (parcial), H-16, H-17, H-33 a H-35 (9) |
| **Depende de** | U1 |
| **Bloqueia** | U3, U4 |

**Responsabilidades**
- CRUD de categorias, com proteção de categoria em uso
- CRUD de gastos à vista, com escopo PESSOAL ou GRUPO e **dono**
- Consulta por período com filtros
- **Os dois totais** — pessoal e de grupo, nunca somados (RF-97)

**Critério de conclusão**
- [ ] Usuário registra gasto pessoal e gasto de grupo
- [ ] Membro do grupo enxerga o gasto do outro, com o dono identificado
- [ ] Consulta retorna `totalPessoal` e `totalGrupo` corretos e distintos (H-17)
- [ ] Categoria com gastos vinculados não é excluída sem realocação (H-34)

> **Escopo do componente `gasto` nesta unidade**: apenas gastos **à vista**. A associação com
> cartão (`cartaoId`, `competencia`) é implementada em U3 — ver §7.

---

## 4. U3 — Crédito

| | |
|---|---|
| **Propósito** | Cartões, ciclo de fatura, parcelamento e a visão unificada de vencimentos |
| **Componentes** | `cartao`, `fatura`, `conta`, `compra`, `gasto` (integração com cartão) |
| **Entidades** | `Cartao`, `Fatura`, `Compra`, `Parcela`, `ContaAPagar`, `ContaRecorrente` |
| **Requisitos** | RF-23 a RF-35, RF-55 a RF-67, RF-94 a RF-96 |
| **Histórias** | H-18 a H-32, H-42 a H-51, J-01, J-03 (25 + 2 jornadas) |
| **Depende de** | U1, U2 |
| **Bloqueia** | U4 (parcialmente — apenas J-02) |

**Responsabilidades**
- CRUD de cartões, com ciclo de fechamento e vencimento
- **Cálculo de competência de fatura**, com corte exclusivo no dia do fechamento
- Compras parceladas, com distribuição do resíduo de centavos
- Consolidação, fechamento, reabertura e bloqueio de fatura
- Contas a pagar de todos os tipos, com recorrência
- **Integração de `gasto` com cartão** — a segunda metade do componente iniciado em U2

**Critério de conclusão**
- [ ] Compra em 30/07 num cartão que fecha dia 28 cai na fatura de setembro (H-20)
- [ ] Compra parcelada gera N parcelas cuja soma é exatamente o total (H-28, H-29) 🔬 **PBT**
- [ ] Fatura fechada gera conta a pagar automaticamente (H-45)
- [ ] Fatura paga bloqueia alterações; desmarcar o pagamento as libera (H-23, H-24)
- [ ] Visão de vencimentos reúne fatura, PIX, boleto e fatura de serviço, ordenados (H-43)
- [ ] Conta recorrente gera ocorrências com valor ajustável no pagamento (H-47, H-48)

> **A unidade mais complexa do sistema** — 25 histórias, 6 entidades e as duas invariantes
> monetárias restantes. Concentra 4 das 6 decisões ainda em aberto (D-04, D-19, D-20, D-33).

---

## 5. U4 — Planejamento

| | |
|---|---|
| **Propósito** | Receitas, orçamento e objetivos de investimento |
| **Componentes** | `receita`, `orcamento`, `investimento` |
| **Entidades** | `Receita`, `Orcamento`, `ObjetivoInvestimento`, `Aporte` |
| **Requisitos** | RF-39 a RF-44, RF-68 a RF-77 |
| **Histórias** | H-36 a H-41, H-52 a H-60, J-02 (15 + 1 jornada) |
| **Depende de** | U1, U2, U3 |
| **Bloqueia** | — |

**Responsabilidades**
- CRUD de receitas e balanço do período
- Teto mensal por categoria e acompanhamento do realizado
- Objetivos de investimento com meta, prazo, aportes e saldo manual

**Critério de conclusão**
- [ ] Balanço apresenta receitas − gastos, **com aportes contando como gasto** (H-38, H-59)
- [ ] Orçamento compara orçado × realizado e sinaliza estouro (H-40, H-41)
- [ ] Objetivo com meta e prazo calcula o aporte mensal necessário (H-57)
- [ ] Rendimento negativo é exibido, não rejeitado (H-55)
- [ ] J-02 (fechar o mês) funciona ponta a ponta, cruzando U2, U3 e U4

> **Depende de U3 apenas por causa de J-02** — a jornada de fechar o mês precisa de vencimentos,
> faturas e orçamento juntos. As demais histórias de U4 dependem só de U1 e U2.

---

## 6. U5 — Infraestrutura

| | |
|---|---|
| **Propósito** | Infraestrutura como código e pipeline de entrega |
| **Componentes** | Terraform, `Dockerfile`, GitHub Actions |
| **Requisitos** | RF-45 a RF-54, RF-81 a RF-93, RNF-13 a RNF-17 |
| **Histórias** | Nenhuma — são requisitos técnicos, sem interação de usuário final |
| **Depende de** | — **paralelizável** |
| **Bloqueia** | — |

**Responsabilidades**
- `infra/terraform/bootstrap/` — bucket S3 do state, lock, OIDC provider, role e ECR
- `infra/terraform/` — VPC, security group, EC2, volume EBS, IAM role da instância
- `Dockerfile` da aplicação
- Workflows: CI da aplicação, `terraform plan` em PR, `terraform apply` no merge, build e push da
  imagem, deploy via SSM
- Runbook de bootstrap em versão executável

**Critério de conclusão**
- [ ] `terraform plan` executa sem erro nos módulos gerados
- [ ] Nenhuma credencial de longa duração versionada ou em GitHub Secrets (RF-82)
- [ ] Porta 22 fechada no security group (RF-90)
- [ ] Volume EBS do PostgreSQL separado do volume raiz (RF-50)
- [ ] Filtro de path no CI: mudança em Kotlin não dispara `terraform plan` (RNF-14)
- [ ] Runbook suficiente para partir de uma conta AWS vazia

> **Recomendação de sequenciamento**: embora paralelizável, executá-la **cedo** faz as unidades
> seguintes nascerem com CI rodando os testes, e tira do caminho a incerteza do bootstrap manual.
>
> ⚠️ **R-05** — o `terraform apply` é automático no merge, sem gate. As mitigações
> (`prevent_destroy` nos recursos com estado) precisam ser detalhadas na Infrastructure Design
> **antes** de qualquer apply com dados reais.

---

## 7. O componente dividido: `gasto`

Único componente implementado em duas unidades. Consequência da decisão de fronteira por capacidade
de negócio.

| Unidade | O que entra | Por quê |
|---|---|---|
| **U2** | Entidade `Gasto`, CRUD, escopo, dono, consulta, os dois totais — **gastos à vista** | Não depende de cartão; entrega capacidade utilizável |
| **U3** | Campos `cartaoId` e `competencia`, integração com `CartaoService` e `FaturaService`, bloqueio de fatura paga | Depende de `cartao` e `fatura`, que só existem em U3 |

**Coordenação necessária**
- A entidade `Gasto` nasce em U2 **já com os campos** `cartaoId` e `competencia` **nuláveis**, para
  evitar migration de alteração de schema em U3
- O endpoint `POST /gastos` aceita `cartaoId` opcional desde U2, mas **rejeita** o campo até que U3
  esteja implementada
- Os testes de U2 cobrem apenas o caminho à vista; U3 acrescenta os de cartão

> **Alternativa descartada**: criar a coluna só em U3. Exigiria uma migration de `ALTER TABLE` e
> mudaria o contrato da API entre unidades — o front teria de lidar com duas versões do mesmo
> recurso.

---

## 8. Estratégia de organização de código

Obrigatório para greenfield, conforme o Step 2 da regra.

### Modelo de implantação
**Monolito single-module**, empacotado como imagem Docker única e implantado numa instância EC2.
As unidades são módulos lógicos — **não** geram artefatos de build separados.

### Estrutura de diretórios

```
financial-control/
+-- src/main/kotlin/com/rafaelmatheus/financialcontrol/
|     +-- common/          U1
|     +-- usuario/         U1
|     +-- grupo/           U1
|     +-- categoria/       U2
|     +-- gasto/           U2 (a vista) + U3 (integracao cartao)
|     +-- cartao/          U3
|     +-- compra/          U3
|     +-- fatura/          U3
|     +-- conta/           U3
|     +-- receita/         U4
|     +-- orcamento/       U4
|     +-- investimento/    U4
|
+-- src/main/resources/
|     +-- application.yml
|     +-- db/migration/    uma migration por unidade, versionada
|           V1__fundacao.sql        U1
|           V2__lancamentos.sql     U2
|           V3__credito.sql         U3
|           V4__planejamento.sql    U4
|
+-- src/test/kotlin/...    espelha a estrutura de main
|
+-- infra/terraform/       U5
|     +-- bootstrap/       aplicado manualmente, uma vez
|     +-- modules/
|     +-- envs/{dev,prod}/
|
+-- .github/workflows/     U5
+-- Dockerfile             U5
+-- build.gradle.kts
```

### Convenções

| Item | Convenção |
|---|---|
| Pacotes | Um por feature, em minúsculas, singular (`gasto`, não `gastos`) |
| Migrations | Uma por unidade — `V{n}__{unidade}.sql`. Nunca alterar migration já aplicada |
| Testes | Espelham a estrutura de `main`; PBT em arquivo separado, com nome distinto dos testes de exemplo (regra PBT-10) |
| DTOs | No pacote da feature, sufixo `DTO` para saída e verbo no comando de entrada (`LancarGasto`) |
| Identificadores | `UUID` gerado pela aplicação (D-32) |
| Valores monetários | `Dinheiro` — nunca `Double`, nunca `Float` (RNF-01) |

---

## 9. Resumo

| Unidade | Componentes | Entidades | Histórias | Requisitos |
|---|---|---|---|---|
| U1 — Fundação | 3 | 3 | 8 | RF-01 a RF-10 |
| U2 — Lançamentos | 2 | 2 | 9 | RF-11, RF-16 a RF-22, RF-36 a RF-38, RF-97 |
| U3 — Crédito | 5 | 6 | 25 + 2 jornadas | RF-23 a RF-35, RF-55 a RF-67, RF-94 a RF-96 |
| U4 — Planejamento | 3 | 4 | 15 + 1 jornada | RF-39 a RF-44, RF-68 a RF-77 |
| U5 — Infraestrutura | — | — | 0 | RF-45 a RF-54, RF-81 a RF-93 |
| **Total** | **12** | **15** | **57 + 3** | **93 RF ativos** |

### Distribuição das decisões em aberto

| Unidade | Decisões a fechar |
|---|---|
| U1 | D-02 (autenticação — na NFR Requirements da primeira unidade) |
| U3 | D-04, D-19, D-20, D-33, J-02 |
| U5 | D-11, D-12, mitigações de R-05 |
