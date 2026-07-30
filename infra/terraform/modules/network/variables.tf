variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR da subnet publica, onde fica a EC2"
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_subnet_cidrs" {
  description = "CIDRs das subnets privadas do banco — duas AZs, exigencia do db_subnet_group"
  type        = list(string)
  default     = ["10.0.2.0/24", "10.0.3.0/24"]
}
