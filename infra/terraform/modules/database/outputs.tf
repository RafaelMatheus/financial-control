output "endpoint" {
  description = "Host do banco, sem a porta"
  value       = aws_db_instance.main.address
}

output "port" {
  value = aws_db_instance.main.port
}

output "database_name" {
  value = aws_db_instance.main.db_name
}

output "jdbc_url" {
  description = "URL JDBC com TLS obrigatorio (rds.force_ssl = 1)"
  value       = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}?sslmode=require"
}
