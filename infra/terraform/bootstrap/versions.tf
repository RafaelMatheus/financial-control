terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Sem backend remoto: este modulo CRIA o backend usado pelos demais.
  # O terraform.tfstate fica local e deve ser guardado — ver runbook.
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "financial-control"
      ManagedBy = "terraform"
      Module    = "bootstrap"
    }
  }
}

variable "aws_region" {
  description = "Regiao AWS (D-11)"
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "Conta AWS alvo. Usado no id do import block do OIDC provider."
  type        = string
  default     = "594116288641"
}

variable "ci_role_name" {
  description = <<-EOT
    Nome da role assumida pelo GitHub Actions. Vale `github-actions` porque a
    role foi criada manualmente no console para destravar o pipeline, e o
    Terraform a adota por import em vez de criar uma segunda.
  EOT
  type        = string
  default     = "github-actions"
}

variable "extra_trusted_subs" {
  description = <<-EOT
    Valores extras aceitos no claim `sub`, somados ao padrao repo:owner/repo:ref.

    O default preserva o `sub` que a role criada no console ja aceitava. O formato
    com IDs numericos (owner@25590639, repo@1316467420) nao e o padrao do GitHub e
    sua origem nao foi identificada — mas o pipeline autentica com ele, entao
    remove-lo no apply arriscaria trancar o CI para fora da propria role.

    Depois de confirmar qual `sub` o token realmente carrega, esta lista deve
    encolher para so o que for necessario (RF-93).
  EOT
  type        = list(string)
  default     = ["repo:RafaelMatheus@25590639/financial-control@1316467420:*"]
}

variable "github_repository" {
  description = "Repositorio GitHub no formato owner/repo"
  type        = string
  default     = "RafaelMatheus/financial-control"
}

variable "github_branch" {
  description = "Branch autorizada a assumir a role do CI (RF-93)"
  type        = string
  default     = "main"
}

variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
  default     = "financial-control"
}

variable "github_repositories_front" {
  description = <<-DESC
    Repositorios adicionais que usam a mesma role OIDC — hoje, o do front.

    Eles publicam no ECR e disparam o deploy por SSM na mesma instancia, entao
    precisam exatamente das mesmas permissoes. Uma role por repositorio seria
    mais granular sem ser mais segura, e duplicaria a manutencao da policy.

    ## O formato do `sub`, e a divida que isto resolve

    Esta conta do GitHub NAO emite o `sub` no formato documentado
    `repo:OWNER/REPO:ref:...`. Ela emite com IDs numericos embutidos:

        repo:OWNER@<owner_id>/REPO@<repo_id>:<contexto>

    A trust policy original ja trazia `repo:RafaelMatheus@25590639/financial-control@1316467420:*`,
    herdado da role criada a mao no console. O ciclo AI-DLC registrou aquele
    valor como "formato cuja origem nao foi identificada" e manteve a UNIAO com
    o padrao documentado, por assimetria de custo.

    **A origem esta identificada agora**: 25590639 e o `id` do usuario e
    1316467420 e o `id` do repositorio, conferidos pela API do GitHub. O primeiro
    deploy do front falhou com `Not authorized to perform sts:AssumeRoleWithWebIdentity`
    exatamente porque a entrada dele usava o formato documentado, que esta conta
    nao emite.

    Cada entrada aqui gera as DUAS formas. A do padrao documentado nao custa
    nada e cobre o caso de a conta voltar ao formato normal; a numerica e a que
    de fato autentica hoje.
  DESC
  type = list(object({
    repositorio = string
    owner_id    = number
    repo_id     = number
  }))
  default = [{
    repositorio = "RafaelMatheus/financial-control-web"
    owner_id    = 25590639
    repo_id     = 1322237708
  }]
}
