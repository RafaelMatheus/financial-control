variable "project_name" {
  description = "Prefixo dos recursos"
  type        = string
}

variable "availability_zone" {
  description = "AZ do volume — precisa ser a mesma da instancia"
  type        = string
}

variable "instance_id" {
  description = "Instancia a que o volume e anexado"
  type        = string
}

variable "size_gb" {
  description = "Tamanho do volume em GB"
  type        = number
  default     = 20
}

variable "device_name" {
  description = "Device de anexacao"
  type        = string
  default     = "/dev/xvdf"
}
