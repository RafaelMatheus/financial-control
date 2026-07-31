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
