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
  description = "Tipo da instancia EC2 da aplicacao"
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "Classe da instancia RDS"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Disco inicial do banco em GB"
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Alta disponibilidade do banco — dobra o custo"
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Retencao dos backups automaticos"
  type        = number
  default     = 7
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
