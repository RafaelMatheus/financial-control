# Bootstrap

Resolve uma **dependência circular**: o Terraform do CI precisa de um bucket S3 para o state e de
uma role IAM para autenticar — mas ambos são infraestrutura que só o Terraform criaria.

Este módulo tem **state local** e é aplicado **uma única vez, manualmente**.

## O que cria

| Recurso | Para quê |
|---|---|
| Bucket S3 versionado e criptografado | State remoto dos demais módulos (RF-51) |
| OIDC provider do GitHub | Autenticação do CI sem credencial de longa duração (RF-82) |
| IAM role do CI | Assumida pelo Actions; trust restrita a repositório e branch (RF-93) |
| Repositório ECR | Imagens da aplicação, com tag imutável (RF-87) |

## Uso

```bash
aws sts get-caller-identity    # CONFIRME a conta antes de continuar

terraform init
terraform plan                 # leia a lista de recursos
terraform apply
```

Anote as saídas e registre como **variables** (não secrets) do repositório GitHub, em
*Settings → Secrets and variables → Actions → Variables*:

| Output | Variable |
|---|---|
| `role_arn` | `AWS_ROLE_ARN` |
| `aws_region` | `AWS_REGION` |
| `ecr_repository_url` | `ECR_REPOSITORY` |

E o `state_bucket` em `../envs/{dev,prod}/backend.hcl`.

## ⚠️ O state local

O `terraform.tfstate` deste módulo **fica na sua máquina** e não é versionado. Guarde-o — sem ele
você perde a referência dos recursos de bootstrap e precisaria importá-los manualmente.

## Passo a passo completo

Ver `aidlc-docs/construction/u5-infraestrutura/infrastructure-design/deployment-architecture.md` §6.
