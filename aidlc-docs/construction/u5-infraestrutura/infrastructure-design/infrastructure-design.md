# Infrastructure Design — U5 Infraestrutura

**Stage**: CONSTRUCTION - Infrastructure Design
**Unidade**: U5 — Infraestrutura
**Timestamp**: 2026-07-30T16:11:59Z

---

## 1. Decisões desta stage

| ID | Decisão | Justificativa |
|---|---|---|
| **D-11** | `us-east-1` · `t3.small` | Região mais barata da AWS. Economia de ~US$ 130/ano sobre São Paulo; latência de ~120ms aceitável para uso pessoal. 2 GB de RAM comportam JVM e PostgreSQL — `t3.micro` traria risco real de OOM |
| **D-34** | VPC própria, subnet pública, sem NAT | A proteção vem do security group, não do isolamento de rede. NAT Gateway custaria ~US$ 32/mês — mais que a instância |
| **D-35** | Nginx + Let's Encrypt na instância | TLS gratuito e auto-renovável. ALB custaria ~US$ 18/mês |
| **D-12** | Deploy por SSM Run Command sobre `docker compose` | Fecha a decisão iniciada em D-24 |
| ~~D-36~~ | ~~Backup fora deste ciclo~~ | ❌ **Sem objeto** — o RDS faz backup automático |
| **D-37** | **RDS PostgreSQL gerenciado** em vez de container na EC2 | Reverte D-10. RDS comum e não Aurora: mesmos benefícios por ~1/4 do preço; a arquitetura de instância única não usa réplicas nem failover rápido. **Resolve R-01** |
| **D-38** | Duas subnets privadas, sem NAT | RDS exige subnet group com 2 AZs. O banco não precisa de saída |
| **D-39** | Database e usuário dedicados | Master só para administração; credencial da app com escopo mínimo |

---

## 2. Mapeamento componente → serviço AWS

| Componente lógico | Serviço AWS | Configuração | Requisito |
|---|---|---|---|
| Aplicação Spring Boot | **EC2** `t3.small` | Container Docker, 2 vCPU / 2 GB | RF-46, RF-48 |
| PostgreSQL 16 | **RDS** `db.t4g.micro` | Single-AZ, subnet privada, gp3 20 GB com autoscaling até 100, criptografado, backup 7 dias, `rds.force_ssl = 1` | RF-47, RF-54 |
| Terminação TLS | **EC2** (mesma instância) | Container nginx + certbot | D-35 |
| Isolamento do banco | **Subnets privadas** ×2 | AZs distintas, sem rota para a internet | D-38 |
| Imagem da aplicação | **ECR** | Repositório privado, tag por commit SHA | RF-86, RF-87 |
| Rede | **VPC** própria | `10.0.0.0/16` · pública `10.0.1.0/24` · privadas `10.0.2.0/24` e `10.0.3.0/24` | RF-49, D-34, D-38 |
| Controle de acesso de rede | **2 Security Groups** | app: 443 e 80 abertas, 22 fechada · database: 5432 apenas a partir do SG da app | RF-49, RF-90, RNF-16 |
| Endereço fixo | **Elastic IP** | Associado à instância | D-35 (DNS do domínio) |
| Acesso administrativo | **Systems Manager** | Session Manager + Run Command | RF-88, RF-90 |
| Identidade da instância | **IAM Role** | Pull do ECR + SSM Managed Instance | RF-89 |
| Identidade do CI | **IAM Role via OIDC** | Trust restrita a repo e branch | RF-82, RF-93 |
| Estado do Terraform | **S3** + lock | Versionado, criptografado | RF-51 |
| Segredos | **SSM Parameter Store** | `SecureString` para credenciais do banco | RF-53 |

---

## 3. Topologia

```
                        Internet
                            |
                            | 443 (TLS) / 80 (redirect)
                            v
    +--------------------------------------------------------+
    |  VPC 10.0.0.0/16   (us-east-1)                         |
    |                                                        |
    |  +--------------------------------------------------+  |
    |  | Subnet PUBLICA 10.0.1.0/24            AZ-a       |  |
    |  |                                                  |  |
    |  |   +------------------------------------------+   |  |
    |  |   | EC2 t3.small + Elastic IP                |   |  |
    |  |   |                                          |   |  |
    |  |   |   docker compose:                        |   |  |
    |  |   |     nginx  :443 :80                      |   |  |
    |  |   |       | 8080                             |   |  |
    |  |   |     app (Spring Boot)                    |   |  |
    |  |   +---------------------|--------------------+   |  |
    |  +-------------------------|------------------------+  |
    |                            | 5432 (TLS obrigatorio)    |
    |  +-------------------------|------------------------+  |
    |  | Subnets PRIVADAS        |                        |  |
    |  |   10.0.2.0/24  AZ-a     |                        |  |
    |  |   10.0.3.0/24  AZ-b     v                        |  |
    |  |                +------------------------+        |  |
    |  |                | RDS PostgreSQL 16      |        |  |
    |  |                | db.t4g.micro           |        |  |
    |  |                | gp3 20GB criptografado |        |  |
    |  |                | backup automatico 7d   |        |  |
    |  |                | sem acesso publico     |        |  |
    |  |                +------------------------+        |  |
    |  |                                                  |  |
    |  | Sem rota default: banco nao sai para a internet  |  |
    |  +--------------------------------------------------+  |
    |                                                        |
    |  Internet Gateway   (apenas a subnet publica)          |
    +--------------------------------------------------------+

    SG app                          SG database
      443  0.0.0.0/0  ABERTA          5432  <- SG app  APENAS
      80   0.0.0.0/0  ABERTA          nenhum CIDR
      22   ---        FECHADA
```

**Sem NAT Gateway.** A EC2 sai pelo Internet Gateway; o banco não precisa de saída.

**Duas AZs nas subnets privadas** — exigência do `db_subnet_group` do RDS, mesmo com a instância
single-AZ.

**Origem por security group, não por CIDR**: a regra do banco referencia o SG da aplicação. Trocar
o IP da EC2 não quebra o acesso, e nenhum outro recurso da VPC alcança o banco.

## 4. Segurança de acesso

### 4.1 Nenhuma credencial de longa duração

| Ator | Como autentica | Requisito |
|---|---|---|
| GitHub Actions → AWS | **OIDC** — token de curta duração via `AssumeRoleWithWebIdentity` | RF-82 |
| EC2 → ECR | **IAM Role da instância** — sem token de registry na máquina | RF-89 |
| EC2 → Parameter Store | IAM Role da instância | RF-53 |
| Você → EC2 | **SSM Session Manager** — sem chave SSH | RF-90 |

Nenhum secret de longa duração nos GitHub Secrets. As *variables* do repositório guardam apenas
identificadores não sensíveis: `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`.

### 4.2 Trust policy da role do CI (RF-93)

Restrita a repositório **e** branch — não a qualquer repositório da organização:

```
sub = repo:RafaelMatheus/financial-control:ref:refs/heads/main
aud = sts.amazonaws.com
```

### 4.3 Portas

| Porta | Estado | Motivo |
|---|---|---|
| 443 | Aberta | HTTPS da API |
| 80 | Aberta | Redirect para 443 e validação HTTP-01 do Let's Encrypt |
| 22 | **Fechada** | Acesso administrativo por SSM (RF-90) |
| 5432 | **Não existe no SG da app** | A aplicação é *cliente* do banco. O SG do banco aceita 5432 apenas a partir do SG da app (RNF-16) |

---

## 5. Estrutura Terraform

```
infra/terraform/
|
+-- bootstrap/              MANUAL, uma vez, state LOCAL
|     main.tf               bucket S3 do state + lock
|     oidc.tf               GitHub OIDC provider + IAM role do CI
|     ecr.tf                repositorio ECR
|     outputs.tf            ARN da role, nome do bucket, URI do ECR
|
+-- main.tf                 backend S3 + composicao dos modulos
+-- variables.tf
+-- outputs.tf
+-- modules/
|     network/              VPC, subnet, IGW, route table
|     security/             security group, IAM role da instancia
|     compute/              EC2, Elastic IP, user-data
|     database/             RDS, subnet group, parameter group
+-- envs/
      dev/                  terraform.tfvars + backend.hcl
      prod/                 terraform.tfvars + backend.hcl
```

### Variáveis parametrizáveis (RF-52)

| Variável | `dev` | `prod` |
|---|---|---|
| `instance_type` | `t3.small` | `t3.small` |
| `db_instance_class` | `db.t4g.micro` | `db.t4g.micro` |
| `db_allocated_storage` | 20 | 20 |
| `db_multi_az` | `false` | `false` |
| `db_backup_retention_days` | 7 | 7 |
| `domain_name` | — | *a informar* |
| `enable_tls` | `false` | *a definir* |

> **`domain_name` é o único insumo pendente.** Sem ele o provisionamento funciona, mas o
> certificado não é emitido e a API responde só por HTTP no IP elástico. Em `dev` isso é aceitável;
> em `prod`, não.

---

## 6. Mitigações do risco R-05

`terraform apply` roda automaticamente no merge, sem gate de aprovação (D-25). As proteções:

### 6.1 `prevent_destroy` nos recursos com estado

```hcl
lifecycle {
  prevent_destroy = true
}
```

Aplicado a: **instância RDS** (perderia o banco), **Elastic IP** (mudança quebraria o DNS),
**repositório ECR** (perderia todas as imagens) e **bucket S3 do state**.

Com isso, um PR que remova esses recursos **falha no `apply`** em vez de destruí-los. A remoção
deliberada exige retirar o `prevent_destroy` num commit separado — dois passos conscientes.

### 6.2 `plan` visível no PR

O workflow de `terraform plan` (RF-84) publica o diff como comentário no PR, com destaque para
linhas de `destroy` e `replace`.

### 6.3 Banco fora do ciclo de vida da instância

Com o RDS, o banco é um recurso independente da EC2. Recriar a instância — mudança de AMI, de tipo,
de user-data — **não afeta os dados**, porque eles nunca estiveram nela.

Além disso, `skip_final_snapshot = false`: mesmo um `destroy` deliberado tira um snapshot final
antes de remover a instância.

---

## 7. Risco R-01 — RESOLVIDO

A migração para RDS gerenciado (D-37) **elimina o risco** que estava aberto.

| Item | Antes (Postgres na EC2) | Agora (RDS) |
|---|---|---|
| Backup | ❌ Fora do escopo (D-36) | ✅ Automático, 7 dias de retenção |
| Point-in-time recovery | ❌ Inexistente | ✅ Nativo |
| Patching | ❌ Manual | ✅ Gerenciado, janela definida |
| Snapshot final ao destruir | ❌ Não havia | ✅ `skip_final_snapshot = false` |
| Criptografia em repouso | Volume EBS | ✅ `storage_encrypted = true` |
| TLS em trânsito | Rede interna do compose | ✅ `rds.force_ssl = 1` — obrigatório |

**RF-54 passa de "fora do escopo" para atendido nativamente.** Nenhuma rotina própria é necessária.

> A troca custa ~US$ 13/mês a mais. Em contrapartida, remove o único risco de severidade Alta que
> permanecia aberto no projeto.

## 8. Cobertura de requisitos

| Requisito | Situação |
|---|---|
| RF-45 Terraform em `infra/terraform/` | ✅ §5 |
| RF-46 EC2 | ✅ `t3.small`, `us-east-1` |
| RF-47 PostgreSQL na instância | ✅ Container no compose |
| RF-48 Imagem Docker | ✅ Build no CI, push para ECR |
| RF-49 VPC, subnet, SG, role | ✅ §3, §4 |
| RF-50 Volume EBS separado | ✅ §6.3 |
| RF-51 State remoto com lock | ✅ Bootstrap |
| RF-52 Parametrizado por ambiente | ✅ `envs/dev`, `envs/prod` |
| RF-53 Segredos por Parameter Store | ✅ §2 |
| RF-54 Backup | ❌ **Fora do escopo** — decisão do usuário |
| RF-81 a RF-93 CI/CD | ✅ `deployment-architecture.md` |
| RNF-13 Reprodutibilidade | ✅ VPC própria, sem passo manual no console |
| RNF-14 Filtro de path no CI | ✅ `deployment-architecture.md` |
| RNF-15 Segredos fora do versionamento | ✅ `.gitignore` estendido |
| RNF-16 Superfície mínima | ✅ §4.3 |
| RNF-17 Durabilidade | ⚠️ **Parcial** — apenas RF-50; ver §7 |

**Decisões fechadas**: D-11, D-12, D-34, D-35, D-36.
**Nenhuma decisão de infraestrutura permanece em aberto**, exceto o insumo `domain_name`.
