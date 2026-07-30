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
