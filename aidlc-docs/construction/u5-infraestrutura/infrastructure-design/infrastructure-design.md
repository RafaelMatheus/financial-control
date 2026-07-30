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
| **D-36** | **Backup fora deste ciclo** | Decisão do usuário. Ver §7 |

---

## 2. Mapeamento componente → serviço AWS

| Componente lógico | Serviço AWS | Configuração | Requisito |
|---|---|---|---|
| Aplicação Spring Boot | **EC2** `t3.small` | Container Docker, 2 vCPU / 2 GB | RF-46, RF-48 |
| PostgreSQL 16 | **EC2** (mesma instância) | Container Docker, volume em EBS separado | RF-47, RF-50 |
| Terminação TLS | **EC2** (mesma instância) | Container nginx + certbot | D-35 |
| Persistência de dados | **EBS gp3** 20 GB | Volume dedicado, montado em `/var/lib/postgresql` | RF-50 |
| Imagem da aplicação | **ECR** | Repositório privado, tag por commit SHA | RF-86, RF-87 |
| Rede | **VPC** própria + subnet pública | `10.0.0.0/16` · `10.0.1.0/24` | RF-49, D-34 |
| Controle de acesso de rede | **Security Group** | 443 e 80 abertas; 22 e 5432 fechadas | RF-49, RF-90, RNF-16 |
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
        +-------------------------------------------+
        |  VPC 10.0.0.0/16  (us-east-1)             |
        |                                           |
        |  +-------------------------------------+  |
        |  | Subnet publica 10.0.1.0/24          |  |
        |  |                                     |  |
        |  |  +-------------------------------+  |  |
        |  |  | EC2 t3.small + Elastic IP     |  |  |
        |  |  |                               |  |  |
        |  |  |  docker compose:              |  |  |
        |  |  |   +-----------------------+   |  |  |
        |  |  |   | nginx    :443 :80     |   |  |  |
        |  |  |   +----------+------------+   |  |  |
        |  |  |              | 8080           |  |  |
        |  |  |   +----------v------------+   |  |  |
        |  |  |   | app (Spring Boot)     |   |  |  |
        |  |  |   +----------+------------+   |  |  |
        |  |  |              | 5432           |  |  |
        |  |  |   +----------v------------+   |  |  |
        |  |  |   | postgres:16-alpine    |   |  |  |
        |  |  |   +----------+------------+   |  |  |
        |  |  |              |                |  |  |
        |  |  +--------------|----------------+  |  |
        |  |                 | mount              |  |
        |  |        +--------v---------+          |  |
        |  |        | EBS gp3 20GB     |          |  |
        |  |        | volume separado  |          |  |
        |  |        +------------------+          |  |
        |  +-------------------------------------+  |
        |                                           |
        |  Internet Gateway  |  Route Table         |
        +-------------------------------------------+

        Security Group:
          443  0.0.0.0/0    ABERTA
          80   0.0.0.0/0    ABERTA (redirect para 443)
          22   ---          FECHADA  -> acesso por SSM
          5432 ---          FECHADA  -> apenas rede interna do compose
```

**Sem NAT Gateway.** A instância tem IP público e sai pelo Internet Gateway.

---

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
| 5432 | **Fechada** | PostgreSQL só na rede interna do compose (RNF-16) |

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
|     storage/              volume EBS + attachment
+-- envs/
      dev/                  terraform.tfvars + backend.hcl
      prod/                 terraform.tfvars + backend.hcl
```

### Variáveis parametrizáveis (RF-52)

| Variável | `dev` | `prod` |
|---|---|---|
| `instance_type` | `t3.small` | `t3.small` |
| `ebs_size_gb` | 20 | 20 |
| `domain_name` | — | *a informar* |
| `enable_tls` | `false` | `true` |

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

Aplicado a: **volume EBS** (dados do PostgreSQL), **Elastic IP** (mudança quebraria o DNS),
**repositório ECR** (perderia todas as imagens) e **bucket S3 do state**.

Com isso, um PR que remova esses recursos **falha no `apply`** em vez de destruí-los. A remoção
deliberada exige retirar o `prevent_destroy` num commit separado — dois passos conscientes.

### 6.2 `plan` visível no PR

O workflow de `terraform plan` (RF-84) publica o diff como comentário no PR, com destaque para
linhas de `destroy` e `replace`.

### 6.3 Proteção do volume contra `replace`

O volume EBS é declarado **separadamente** da instância, com `aws_volume_attachment`. Assim,
substituir a instância (mudança de AMI, de tipo, de user-data) **não toca no volume** — ele é
desanexado e reanexado.

> Esta é a proteção mais relevante do conjunto: sem ela, qualquer alteração que force recriação da
> EC2 levaria os dados junto.

---

## 7. Risco R-01 — aberto e sem mitigação

**Decisão do usuário**: backup fora deste ciclo.

| Item | Situação |
|---|---|
| RF-54 (rotina de backup) | ❌ Fora do escopo desta unidade |
| RF-50 (volume EBS separado) | ✅ Implementado — **única proteção ativa** |
| Snapshot automático | ❌ Não configurado |
| Restauração documentada | ❌ Não existe |

**O que RF-50 protege**: os dados sobrevivem à substituição da instância — troca de AMI, de tipo,
recriação por mudança de user-data.

**O que RF-50 não protege**: perda ou corrupção do próprio volume, exclusão acidental, ou erro de
aplicação que corrompa os dados. Nesses casos **não há ponto de recuperação**.

**Gatilho para retomar**: primeiro deploy com dados reais que não se queira perder.

---

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
