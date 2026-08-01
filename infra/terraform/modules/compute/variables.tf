variable "project_name" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "instance_type" {
  description = "Tipo da instancia (D-11: t3.small)"
  type        = string
  default     = "t3.small"
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "instance_profile_name" {
  type = string
}

variable "ecr_repository" {
  type = string
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

variable "environment" {
  description = <<-EOT
    Ambiente (dev, prod). Vira SPRING_PROFILES_ACTIVE no container.

    Em `dev` isso ativa application-dev.yml, que liga o Swagger UI. Em `prod` o
    arquivo nao e carregado e a UI continua desligada — a especificacao completa
    e um mapa da superficie de ataque.
  EOT
  type        = string
}
