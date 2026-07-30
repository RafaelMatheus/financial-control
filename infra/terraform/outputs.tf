output "public_ip" {
  description = "IP elastico. Apontar o registro A do dominio para ele."
  value       = module.compute.public_ip
}

output "instance_id" {
  description = "Use com: aws ssm start-session --target <id>"
  value       = module.compute.instance_id
}

output "api_url" {
  value = var.enable_tls && var.domain_name != "" ? "https://${var.domain_name}" : "http://${module.compute.public_ip}"
}
