variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnets privadas — o RDS exige pelo menos duas AZs"
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group do banco: 5432 apenas a partir da aplicacao"
  type        = string
}

variable "engine_version" {
  description = <<-EOT
    Versao do PostgreSQL, so a maior.

    Fixar a menor (era "16.6") quebra quando a AWS a retira do catalogo:
    "Cannot find version 16.6 for postgres". Com so a maior, o RDS escolhe a
    menor mais recente disponivel — e e o unico valor coerente com
    auto_minor_version_upgrade = true, que ja estava ligado no modulo.
  EOT
  type        = string
  default     = "16"
}

variable "instance_class" {
  description = "Classe da instancia"
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Disco inicial em GB"
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Teto do autoscaling de disco em GB"
  type        = number
  default     = 100
}

variable "database_name" {
  description = "Database criado na inicializacao"
  type        = string
  default     = "financial_control"
}

variable "master_username" {
  description = "Usuario master — administracao apenas, nao usado pela aplicacao"
  type        = string
  default     = "financial_admin"
}

variable "master_password" {
  description = "Senha do master, gerada pelo Terraform"
  type        = string
  sensitive   = true
}

variable "multi_az" {
  description = "Alta disponibilidade — dobra o custo"
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Retencao dos backups automaticos. Zero desabilita — nao use zero."
  type        = number
  default     = 7
}

variable "publicly_accessible" {
  description = "Da IP publico ao RDS. Exige rota para o IGW na subnet do banco."
  type        = bool
  default     = false
}
