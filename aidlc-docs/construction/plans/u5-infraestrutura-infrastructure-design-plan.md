# Infrastructure Design Plan — U5 Infraestrutura

**Stage**: CONSTRUCTION - Infrastructure Design
**Unidade**: U5 — Infraestrutura
**Timestamp**: 2026-07-30T16:11:59Z
**Status**: ✅ Respondido — executando a Seção 3

---

## 1. Contexto

**Fontes**: `requirements.md` rev. 8 (RF-45 a RF-54, RF-81 a RF-93, RNF-13 a RNF-17) ·
`unit-of-work.md` (U5) · `bootstrap-runbook.md` (esboço) · `execution-plan.md`.

### Decisões já tomadas em stages anteriores

| ID | Decisão |
|---|---|
| D-08 | Terraform no mesmo repositório, em `infra/terraform/` |
| D-09 | IaC dentro deste ciclo |
| D-10 | PostgreSQL na própria instância EC2, sem RDS |
| D-21 | GitHub Actions como plataforma de CI/CD |
| D-22 | Autenticação AWS por OIDC, sem credenciais de longa duração |
| D-23 | Amazon ECR como registry da imagem |
| D-24 | Deploy via SSM Run Command; porta 22 fechada |
| D-25 | `terraform apply` automático no merge, sem gate |
| D-26 | Bootstrap manual e único |

---

## 2. Questões de infraestrutura

Respostas coletadas via tool e transcritas aqui.

## Question 1 — Região e dimensionamento (D-11)
Qual região AWS e tipo de instância?

A) `us-east-1` · `t3.small` — mais barata (~US$ 20/mês), latência ~120ms do Brasil

B) `sa-east-1` · `t3.small` — São Paulo (~US$ 31/mês), latência ~15ms

C) `us-east-1` · `t3.micro` — mais barato (~US$ 12/mês), 1 GB de RAM

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **`us-east-1` · `t3.small`**. Economia de ~US$ 130/ano em relação a São Paulo.
A latência de ~120ms é aceitável para uso pessoal. 2 GB de RAM comportam JVM e PostgreSQL com folga
— `t3.micro` foi descartado pelo risco real de OOM com os dois processos na mesma máquina.

## Question 2 — Topologia de rede
Como estruturar a rede?

A) VPC própria, subnet pública com IP elástico

B) VPC própria, subnet privada + NAT Gateway

C) VPC default da conta

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **VPC própria, subnet pública**. A proteção vem do security group (22 e 5432
fechadas), não do isolamento de rede. Subnet privada exigiria NAT Gateway a ~US$ 32/mês — mais caro
que a própria instância, incoerente com a escolha de economizar na Question 1. VPC default foi
descartada por contrariar RNF-13 (infra reproduzível do zero).

## Question 3 — Exposição na internet
Como a aplicação fica acessível?

A) Nginx + Let's Encrypt na instância

B) Application Load Balancer + certificado ACM

C) HTTP direto, sem TLS

X) Other (please describe after [Answer]: tag below)

[Answer]: A — **Nginx + Let's Encrypt**. Container nginx faz terminação TLS e proxy para a
aplicação. Certificado gratuito e auto-renovável, sem custo adicional. ALB foi descartado por custar
~US$ 18/mês, quase dobrando a conta. HTTP sem TLS foi descartado — trafegaria credenciais e dados
financeiros em texto claro.

> **Insumo pendente**: exige um **domínio** apontando para o IP elástico. O Terraform parametriza o
> domínio como variável; enquanto não for informado, o provisionamento funciona mas o certificado
> não é emitido.

## Question 4 — Backup do PostgreSQL (RF-54, risco R-01)
Qual rotina de backup?

A) `pg_dump` diário para S3

B) Snapshot automático do volume EBS

C) Ambos

X) Other (please describe after [Answer]: tag below)

[Answer]: X — **Fora deste ciclo.** Resposta do usuário: *"nao precisamos nos preocupar neste
momnento com backup"*.

**Consequências registradas**:
- **RF-54** sai do escopo desta unidade
- **Risco R-01** (severidade Alta) permanece **aberto e sem mitigação**. PostgreSQL na instância,
  sem backup gerenciado nem rotina própria — perda da instância ou corrupção do volume implica
  perda total dos dados
- **RF-50** (volume EBS separado do volume raiz) **permanece no escopo** e é implementado. É a
  única proteção ativa: os dados sobrevivem à substituição da instância, mas não à perda do volume
- **Gatilho para retomar**: primeiro deploy com dados reais que não se queira perder

---

## 3. Checklist de execução

### 3.1 Preparação
- [x] Consolidar as respostas da Seção 2
- [x] Confirmar as decisões herdadas (D-08 a D-26)

### 3.2 Artefatos
- [x] Gerar `infrastructure-design.md` — mapeamento de componentes para serviços AWS
- [x] Gerar `deployment-architecture.md` — topologia, fluxo de deploy e runbook executável

### 3.3 Cobertura
- [x] RF-45 a RF-53 (infraestrutura) — RF-54 excluído por decisão do usuário
- [x] RF-81 a RF-93 (CI/CD)
- [x] RNF-13 a RNF-17
- [x] Mitigações de R-05 detalhadas (`prevent_destroy`)
- [x] D-11 e D-12 fechados

---

## 4. Fora do escopo desta stage

- Código Terraform propriamente dito → **Code Generation**
- `Dockerfile` e workflows → **Code Generation**
- Execução do `terraform apply` → fora do fluxo AI-DLC; roda no GitHub Actions
- Backup do PostgreSQL → excluído por decisão do usuário (Question 4)
