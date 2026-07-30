environment   = "dev"
aws_region    = "us-east-1"
instance_type = "t3.small"
db_instance_class        = "db.t4g.micro"
db_allocated_storage     = 20
db_multi_az              = false
db_backup_retention_days = 7

# Sem dominio em dev: a API responde por HTTP no IP elastico.
domain_name = ""
enable_tls  = false

# Conta 594116288641 — saida ecr_repository_url do bootstrap.
ecr_repository = "594116288641.dkr.ecr.us-east-1.amazonaws.com/financial-control"
