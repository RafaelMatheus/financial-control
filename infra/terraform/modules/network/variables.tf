variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "CIDR da subnet publica"
  type        = string
  default     = "10.0.1.0/24"
}
