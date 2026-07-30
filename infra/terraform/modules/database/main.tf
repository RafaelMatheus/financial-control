# RDS PostgreSQL 16 gerenciado.
#
# Substitui a decisao original de rodar PostgreSQL em container na EC2 (D-10).
# Com isso o RISCO R-01 — banco sem backup gerenciado — deixa de existir:
# a AWS faz backup automatico, point-in-time recovery e patching.
#
# Escolhido RDS comum em vez de Aurora: mesmos beneficios de gerenciamento por
# cerca de um quarto do preco. A arquitetura de instancia unica nao usa replicas
# nem failover rapido, que sao o diferencial do Aurora.

resource "aws_db_subnet_group" "main" {
  name        = "${var.project_name}-db"
  subnet_ids  = var.private_subnet_ids
  description = "Subnets privadas do banco — duas AZs, exigencia do RDS"

  tags = { Name = "${var.project_name}-db" }
}

resource "aws_db_parameter_group" "main" {
  name        = "${var.project_name}-pg16"
  family      = "postgres16"
  description = "Parametros do PostgreSQL 16"

  # Exige TLS em toda conexao. O cliente usa sslmode=require.
  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage # autoscaling de disco
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.database_name
  username = var.master_username
  password = var.master_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.security_group_id]
  parameter_group_name   = aws_db_parameter_group.main.name

  # Sem acesso publico: alcancavel apenas de dentro da VPC.
  publicly_accessible = false
  multi_az            = var.multi_az

  # Backup gerenciado — o que resolve o risco R-01.
  backup_retention_period   = var.backup_retention_days
  backup_window             = "06:00-07:00" # UTC, madrugada no Brasil
  maintenance_window        = "sun:07:00-sun:08:00"
  copy_tags_to_snapshot     = true
  delete_automated_backups  = false
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final"

  auto_minor_version_upgrade = true
  apply_immediately          = false

  performance_insights_enabled = false # custo extra desnecessario neste porte
  enabled_cloudwatch_logs_exports = ["postgresql"]

  tags = { Name = "${var.project_name}-db" }

  lifecycle {
    prevent_destroy = true
  }
}
