# Deployment Architecture — U5 Infraestrutura

**Stage**: CONSTRUCTION - Infrastructure Design
**Unidade**: U5 — Infraestrutura
**Timestamp**: 2026-07-30T16:11:59Z

---

## 1. Pipeline

```
PR toca src/** ou build.gradle.kts
   |
   +--> ci-app.yml
          gradle build + test (Testcontainers)
          |
        merge em main
          |
          +--> deploy-app.yml
                 build imagem Docker
                 push para ECR (tag = commit SHA)
                 ssm send-command -> EC2
                   docker compose pull && up -d


PR toca infra/**
   |
   +--> terraform-plan.yml
          terraform init + plan
          comenta o diff no PR
          |
        merge em main
          |
          +--> terraform-apply.yml
                 terraform apply -auto-approve
                 (sem gate — D-25, mitigado por prevent_destroy)
```

**Filtro de path (RNF-14)**: mudança só em Kotlin não dispara `terraform plan`, e vice-versa.

```yaml
on:
  pull_request:
    paths: ['src/**', 'build.gradle.kts', 'settings.gradle.kts', 'Dockerfile']
```

---

## 2. Workflows

| Arquivo | Gatilho | O que faz | Requisito |
|---|---|---|---|
| `ci-app.yml` | PR em `src/**` | `./gradlew build` com Testcontainers | RF-83 |
| `terraform-plan.yml` | PR em `infra/**` | `plan` + comentário no PR | RF-84 |
| `terraform-apply.yml` | push em `main`, `infra/**` | `apply -auto-approve` | RF-85 |
| `deploy-app.yml` | push em `main`, `src/**` | Build, push para ECR, deploy por SSM | RF-86 a RF-88 |

Todos autenticam por **OIDC** (RF-82):

```yaml
permissions:
  id-token: write
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: ${{ vars.AWS_ROLE_ARN }}
      aws-region: ${{ vars.AWS_REGION }}
```

---

## 3. Deploy na instância

Via **SSM Run Command** — sem SSH, sem chave privada, porta 22 fechada.

```
GitHub Actions
  | aws ssm send-command
  |   --document-name AWS-RunShellScript
  |   --targets Key=tag:Name,Values=financial-control
  v
Systems Manager
  | (agente ja presente na AMI Amazon Linux 2023)
  v
EC2:
  aws ecr get-login-password | docker login
  export IMAGE_TAG=<commit-sha>
  docker compose pull
  docker compose up -d
  docker image prune -f
```

**Rollback**: reexecutar o comando com `IMAGE_TAG` de um commit anterior. A tag é imutável por
commit (RF-87), então qualquer versão publicada é recuperável.

---

## 4. Composição na instância

```yaml
# docker-compose.prod.yml — na instancia
services:
  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
    depends_on: [app]

  certbot:
    image: certbot/certbot
    # renovacao automatica do certificado

  app:
    image: ${ECR_REPOSITORY}:${IMAGE_TAG}
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/financial_control
      DB_USER: ${DB_USER}          # do Parameter Store
      DB_PASSWORD: ${DB_PASSWORD}  # do Parameter Store
    depends_on: [postgres]

  postgres:
    image: postgres:16-alpine
    volumes:
      - /mnt/data/postgres:/var/lib/postgresql/data   # volume EBS separado
```

**Portas publicadas**: apenas 80 e 443 pelo nginx. `app` e `postgres` só na rede interna.

---

## 5. Boot da instância

`user-data` executado na primeira inicialização:

```
1. instala docker e docker compose
2. formata o volume EBS se ainda nao formatado
   (verificacao idempotente — nunca reformata volume com dados)
3. monta em /mnt/data, com entrada no /etc/fstab
4. le credenciais do Parameter Store
5. escreve o .env
6. docker compose up -d
```

> O passo 2 é o mais delicado: precisa distinguir volume novo de volume já com dados. A verificação
> é por `blkid` — se houver filesystem, apenas monta.

---

## 6. Runbook de bootstrap — versão executável

Substitui o esboço de `aidlc-docs/inception/requirements/bootstrap-runbook.md`.

### Pré-requisitos

- [ ] Conta AWS com permissão administrativa
- [ ] AWS CLI autenticada — **confirme a conta**: `aws sts get-caller-identity`
- [ ] Terraform instalado
- [ ] Permissão de admin no repositório GitHub
- [ ] *(opcional)* Um domínio, para TLS

### Passo 1 — Bootstrap

```bash
cd infra/terraform/bootstrap
terraform init                    # state LOCAL
terraform plan                    # confira a lista de recursos
terraform apply
```

Cria: bucket S3 do state, mecanismo de lock, OIDC provider do GitHub, IAM role do CI (trust
restrita a repo e branch) e repositório ECR.

Anote as saídas: `role_arn`, `state_bucket`, `ecr_repository_url`.

> 🔒 O `terraform.tfstate` deste módulo fica **local**. Guarde-o — sem ele você perde a referência
> dos recursos de bootstrap. Não é versionado (RNF-15).

### Passo 2 — Configurar o repositório

Em **Settings → Secrets and variables → Actions → Variables**:

- [ ] `AWS_ROLE_ARN` = saída `role_arn`
- [ ] `AWS_REGION` = `us-east-1`
- [ ] `ECR_REPOSITORY` = saída `ecr_repository_url`

> São *variables*, não *secrets* — a role só pode ser assumida por este repositório e branch.

### Passo 3 — Validar o OIDC

Abra um PR que toque `infra/**` e confirme que o `terraform plan` autentica e roda.
**Se o OIDC estiver mal configurado, falha aqui** — antes de criar qualquer recurso cobrado.

### Passo 4 — Primeiro apply

Merge em `main`. Provisiona VPC, subnet, IGW, security group, EC2, Elastic IP, volume EBS e IAM
role da instância.

> ⚠️ **Sem gate de aprovação** (D-25). Leia o `plan` do PR antes de mergear, especialmente se
> houver `destroy` ou `replace`.

### Passo 5 — DNS e TLS *(se houver domínio)*

- [ ] Aponte um registro A do domínio para o Elastic IP
- [ ] Defina `domain_name` em `envs/prod/terraform.tfvars`
- [ ] Merge — o certbot emite o certificado na próxima execução

Sem domínio, a API responde por HTTP no IP elástico. Aceitável em `dev`, não em `prod`.

### Passo 6 — Primeiro deploy

Merge em `main` tocando `src/**`. O workflow constrói a imagem, publica no ECR e atualiza a
instância via SSM.

### Passo 7 — Verificação

- [ ] `GET https://<dominio>/actuator/health` responde `{"status":"UP"}`
- [ ] Porta 22 **fechada**: `nmap -p 22 <ip>` retorna filtered/closed
- [ ] Porta 5432 **não acessível** de fora
- [ ] `docker compose ps` na instância (via SSM Session Manager) mostra os 3 containers de pé
- [ ] Volume montado: `df -h /mnt/data`
- [ ] Migrations Flyway aplicadas — app sobe com `ddl-auto: validate` passando

### Acesso administrativo

```bash
aws ssm start-session --target <instance-id>
```

Sem SSH, sem chave privada.

---

## 7. Reversão

```bash
# derruba a infraestrutura provisionada pelo pipeline
cd infra/terraform
terraform destroy
```

> ⚠️ Recursos com `prevent_destroy` — volume EBS, Elastic IP, ECR — **bloqueiam o destroy**. Para
> removê-los é preciso retirar o `prevent_destroy` num commit separado. É intencional.
>
> ⚠️ **O volume EBS carrega os dados do PostgreSQL e não há backup** (risco R-01, decisão do
> usuário). Destruí-lo é irreversível.

```bash
# derruba o bootstrap — por ultimo, e so ao abandonar o projeto
cd infra/terraform/bootstrap
terraform destroy
```

---

## 8. Custo estimado

| Item | Mensal |
|---|---|
| EC2 `t3.small` on-demand | ~US$ 15 |
| EBS gp3 20 GB | ~US$ 1,60 |
| IPv4 público | ~US$ 3,60 |
| ECR (poucas imagens) | < US$ 1 |
| S3 (state) + Parameter Store | < US$ 1 |
| **Total** | **~US$ 22/mês** |

Sem NAT Gateway (~US$ 32 economizados), sem ALB (~US$ 18), sem RDS (~US$ 15+).
Valores aproximados — confirme na calculadora da AWS.

---

## 9. Observabilidade

Fora do escopo — a extensão de resiliência está desligada e não houve requisito.

**O que existe**: `/actuator/health` e `/actuator/info`; logs dos containers via `docker logs` e
SSM Session Manager.

**O que não existe**: métricas exportadas, alertas, logs agregados, tracing distribuído.

---

## 10. Insumo pendente

| Item | Impacto | Quando é necessário |
|---|---|---|
| `domain_name` | Sem ele, não há certificado TLS — a API responde por HTTP no IP elástico | Antes do deploy em `prod` com dados reais |

Nenhum outro insumo externo é necessário. O provisionamento funciona sem o domínio.
