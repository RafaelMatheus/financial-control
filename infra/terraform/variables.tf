variable "aws_region" {
  description = "Regiao AWS (D-11)"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Ambiente: dev ou prod"
  type        = string
}

variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
  default     = "financial-control"
}

variable "instance_type" {
  description = "Tipo da instancia EC2"
  type        = string
  default     = "t3.small"
}

variable "ebs_size_gb" {
  description = "Tamanho do volume de dados"
  type        = number
  default     = 20
}

variable "domain_name" {
  description = "Dominio da API. Vazio desabilita o TLS."
  type        = string
  default     = ""
}

variable "enable_tls" {
  description = "Emite certificado Let's Encrypt. Exige domain_name."
  type        = bool
  default     = false
}

variable "ecr_repository" {
  description = "URI do repositorio ECR, saida do bootstrap"
  type        = string
}
