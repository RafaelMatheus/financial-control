# Code Generation Plan — U5 Infraestrutura

**Stage**: CONSTRUCTION - Code Generation - Part 1 (Planejamento)
**Unidade**: U5 — Infraestrutura
**Timestamp**: 2026-07-30T16:11:59Z
**Status**: ✅ Concluído — 14 passos executados

> **Este plano é a fonte única de verdade da Code Generation desta unidade.** A Parte 2 executa
> exatamente o que está aqui, na ordem descrita, marcando cada passo `[x]` ao concluir.

---

## 1. Contexto da unidade

**Fontes**: `infrastructure-design.md` · `deployment-architecture.md` · `unit-of-work.md` (U5) ·
`requirements.md` rev. 8.

### Histórias implementadas
**Nenhuma.** U5 é coberta por requisitos técnicos, sem interação de usuário final — decisão
registrada em `user-stories-assessment.md`.

### Requisitos cobertos

| Grupo | Requisitos |
|---|---|
| Infraestrutura | RF-45 a RF-53 (**RF-54 fora do escopo** — decisão D-36) |
| CI/CD | RF-81 a RF-93 |
| Não-funcionais | RNF-13 a RNF-17 |

### Dependências
**Nenhuma.** U5 não depende de nenhuma unidade de domínio — é totalmente paralelizável.

### Decisões que orientam a geração

| ID | Decisão |
|---|---|
| D-08 | Terraform em `infra/terraform/`, mesmo repositório |
| D-11 | `us-east-1` · `t3.small` |
| D-12 | Deploy por SSM Run Command sobre `docker compose` |
| D-21 | GitHub Actions |
| D-22 | OIDC — nenhuma credencial de longa duração |
| D-23 | Amazon ECR |
| D-25 | `apply` automático no merge, mitigado por `prevent_destroy` |
| D-26 | Bootstrap manual e único |
| D-34 | VPC própria, subnet pública, sem NAT |
| D-35 | nginx + Let's Encrypt |
| D-36 | Backup fora do escopo |

---

## 2. Localização do código

**Workspace root**: `/Users/rafaelmatheuspereiradecastro/IdeaProjects/financial-control`

Projeto **brownfield** — estrutura existente preservada. Arquivos existentes são **modificados
in-place**, nunca duplicados.

| Caminho | Ação |
|---|---|
| `infra/terraform/**` | **Criar** |
| `Dockerfile` | **Criar** |
| `docker-compose.prod.yml` | **Criar** |
| `.github/workflows/**` | **Criar** |
| `.gitignore` | **Modificar** — estender com `*.tfstate`, `*.tfvars` sensíveis (RNF-15) |
| `.dockerignore` | **Criar** |
| `README.md` | **Modificar** — seção de infraestrutura e deploy |

> ⚠️ **Nenhum código de aplicação nesta unidade.** `src/` não é tocado — o domínio começa em U1.

---

## 3. Passos de geração

### Step 1 — Estrutura de diretórios
- [x] Criar `infra/terraform/bootstrap/`
- [x] Criar `infra/terraform/modules/{network,security,compute,storage}/`
- [x] Criar `infra/terraform/envs/{dev,prod}/`
- [x] Criar `.github/workflows/`

### Step 2 — Bootstrap Terraform (RF-91, RF-92, D-26)
Aplicado **manualmente uma vez**, com state local. Resolve a dependência circular do CI.

- [x] `infra/terraform/bootstrap/versions.tf` — providers e versões fixadas
- [x] `infra/terraform/bootstrap/main.tf` — bucket S3 do state, versionado e criptografado, com lock
- [x] `infra/terraform/bootstrap/oidc.tf` — OIDC provider do GitHub + IAM role do CI, com trust
      restrita a `repo:RafaelMatheus/financial-control:ref:refs/heads/main` (RF-93)
- [x] `infra/terraform/bootstrap/ecr.tf` — repositório ECR, com `prevent_destroy`
- [x] `infra/terraform/bootstrap/outputs.tf` — `role_arn`, `state_bucket`, `ecr_repository_url`
- [x] `infra/terraform/bootstrap/README.md` — instrução de uso, apontando para o runbook

### Step 3 — Módulo `network` (RF-49, D-34)
- [x] `modules/network/main.tf` — VPC `10.0.0.0/16`, subnet pública `10.0.1.0/24`, Internet
      Gateway, route table e associação
- [x] `modules/network/variables.tf` · `outputs.tf`

### Step 4 — Módulo `security` (RF-49, RF-89, RF-90, RNF-16)
- [x] `modules/security/main.tf` — security group: **443 e 80 abertas; 22 e 5432 ausentes**
- [x] `modules/security/iam.tf` — IAM role da instância: `AmazonSSMManagedInstanceCore` + pull do
      ECR + leitura do Parameter Store
- [x] `modules/security/variables.tf` · `outputs.tf`

### Step 5 — Módulo `storage` (RF-50, mitigação de R-05)
- [x] `modules/storage/main.tf` — volume EBS gp3 **declarado separadamente** da instância, com
      `aws_volume_attachment` e `prevent_destroy`
- [x] `modules/storage/variables.tf` · `outputs.tf`

> **Ponto crítico da mitigação de R-05**: o volume separado faz com que recriar a EC2 desanexe e
> reanexe o volume, em vez de destruí-lo com os dados dentro.

### Step 6 — Módulo `compute` (RF-46, RF-47, RF-48)
- [x] `modules/compute/main.tf` — EC2 `t3.small`, AMI Amazon Linux 2023, Elastic IP com
      `prevent_destroy`
- [x] `modules/compute/user-data.sh` — instala Docker, monta o volume EBS de forma **idempotente**
      (só formata se não houver filesystem), lê o Parameter Store, escreve o `.env` e sobe o compose
- [x] `modules/compute/variables.tf` · `outputs.tf`

### Step 7 — Composição raiz (RF-45, RF-51, RF-52)
- [x] `infra/terraform/versions.tf` — backend S3 e providers
- [x] `infra/terraform/main.tf` — composição dos 4 módulos
- [x] `infra/terraform/variables.tf` · `outputs.tf`
- [x] `envs/dev/terraform.tfvars` · `envs/dev/backend.hcl`
- [x] `envs/prod/terraform.tfvars` · `envs/prod/backend.hcl`

### Step 8 — Segredos no Parameter Store (RF-53)
- [x] `infra/terraform/parameters.tf` — parâmetros `SecureString` para credenciais do banco,
      **com valor gerado, nunca hardcoded**

### Step 9 — Empacotamento da aplicação (RF-48)
- [x] `Dockerfile` — multi-stage: build Gradle, runtime JRE 21 slim, usuário não-root
- [x] `.dockerignore`
- [x] `docker-compose.prod.yml` — nginx, certbot, app e postgres, com o volume EBS montado

### Step 10 — Nginx e TLS (D-35)
- [x] `infra/nginx/nginx.conf` — proxy para a aplicação, redirect 80→443
- [x] `infra/nginx/init-letsencrypt.sh` — emissão inicial do certificado

### Step 11 — Workflows (RF-81 a RF-88, RNF-14)
- [x] `.github/workflows/ci-app.yml` — build e testes em PR que toca `src/**` (RF-83)
- [x] `.github/workflows/terraform-plan.yml` — `plan` em PR que toca `infra/**`, com diff no PR (RF-84)
- [x] `.github/workflows/terraform-apply.yml` — `apply` no merge (RF-85)
- [x] `.github/workflows/deploy-app.yml` — build, push para ECR com tag de commit, deploy por SSM
      (RF-86 a RF-88)

Todos com **OIDC** (RF-82) e **filtro de path** (RNF-14).

### Step 12 — Higiene do repositório (RNF-15)
- [x] Modificar `.gitignore` — `*.tfstate*`, `.terraform/`, `*.tfvars` com valores sensíveis,
      `.env`

### Step 13 — Documentação
- [x] Modificar `README.md` — seção de infraestrutura, deploy e link para o runbook
- [x] Criar `aidlc-docs/construction/u5-infraestrutura/code/code-summary.md` — resumo dos
      artefatos gerados (markdown apenas)

### Step 14 — Verificação
- [~] `terraform fmt -check -recursive` — Terraform não instalado localmente; roda no CI
- [~] `terraform validate` — idem, roda no CI
- [x] Nenhum segredo hardcoded em nenhum arquivo
- [x] Nenhuma porta 22 ou 5432 em security group
- [x] `prevent_destroy` presente em volume EBS, Elastic IP, ECR e bucket de state
- [x] Filtro de path presente nos 4 workflows
- [x] Nenhum arquivo duplicado criado

---

## 4. Escopo e contagem

| Categoria | Arquivos |
|---|---|
| Terraform — bootstrap | 6 |
| Terraform — módulos | 12 |
| Terraform — raiz e ambientes | 8 |
| Docker | 3 |
| Nginx | 2 |
| Workflows | 4 |
| Modificados | 2 (`.gitignore`, `README.md`) |
| Documentação | 1 |
| **Total** | **~38 arquivos** |

**14 passos**, sem geração de código de aplicação — `src/` não é tocado.

---

## 5. O que este plano **não** faz

| Item | Onde acontece |
|---|---|
| Executar `terraform apply` | GitHub Actions, no merge — nunca desta sessão |
| Executar o bootstrap | Você, manualmente, seguindo o runbook |
| Provisionar recursos AWS | Consequência do merge, não da geração |
| Código de domínio | U1 em diante |
| Rotina de backup | Fora do escopo — D-36 |

> **Nenhum recurso AWS é criado por esta stage.** O que se gera é o código que, quando você fizer
> merge, provisionará a infraestrutura.
