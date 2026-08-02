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

# ---------------------------------------------------- acesso direto ao banco
# Desliga o isolamento de rede do RDS para permitir conexao de fora da VPC
# (DBeaver, psql local). O default e FECHADO: so o ambiente que declarar
# explicitamente nos tfvars fica exposto.
#
# Ligar isto contraria RNF-16 e o desenho de rede de U5, em que o banco so e
# alcancavel a partir do security group da aplicacao. Aceitavel em dev, que nao
# tem dado real; em prod exige decisao registrada.
#
# O caminho sem esta exposicao e o tunel SSM:
#   aws ssm start-session --target <id> \
#     --document-name AWS-StartPortForwardingSessionToRemoteHost \
#     --parameters '{"host":["<endpoint>"],"portNumber":["5432"],"localPortNumber":["5433"]}'
variable "db_publicly_accessible" {
  description = "Da IP publico ao RDS e roteia as subnets do banco pelo IGW"
  type        = bool
  default     = false
}

variable "db_allowed_cidrs" {
  description = "CIDRs que podem abrir 5432 no banco. Vazio mantem o banco fechado."
  type        = list(string)
  default     = []

  validation {
    # /0 aqui seria o banco inteiro aberto para a internet. O erro de digitacao
    # que produz isso e barato demais para nao ter guarda.
    condition     = !contains(var.db_allowed_cidrs, "0.0.0.0/0")
    error_message = "0.0.0.0/0 abriria o banco para a internet inteira. Use um CIDR especifico."
  }
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
