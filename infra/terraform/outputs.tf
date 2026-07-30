output "public_ip" {
  description = "IP elastico da aplicacao. Apontar o registro A do dominio para ele."
  value       = module.compute.public_ip
}

output "instance_id" {
  description = "Use com: aws ssm start-session --target <id>"
  value       = module.compute.instance_id
}

output "api_url" {
  value = var.enable_tls && var.domain_name != "" ? "https://${var.domain_name}" : "http://${module.compute.public_ip}"
}

output "db_endpoint" {
  description = "Host do banco. Privado — alcancavel apenas de dentro da VPC."
  value       = module.database.endpoint
}

output "db_jdbc_url" {
  description = "URL JDBC com TLS obrigatorio"
  value       = module.database.jdbc_url
}
