variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
}

variable "vpc_id" {
  description = "VPC onde o security group e criado"
  type        = string
}

variable "aws_region" {
  description = "Regiao, usada na condicao de KMS"
  type        = string
}

variable "database_allowed_cidrs" {
  description = "CIDRs que podem abrir 5432 direto no banco, alem do SG da aplicacao"
  type        = list(string)
  default     = []
}
