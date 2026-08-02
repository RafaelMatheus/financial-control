environment   = "prod"
aws_region    = "us-east-1"
instance_type = "t3.small"
db_instance_class        = "db.t4g.micro"
db_allocated_storage     = 20
db_multi_az              = false
db_backup_retention_days = 7

# Banco fechado: alcancavel apenas pelo SG da aplicacao. dev abre para o IP do
# desenvolvedor; prod nao, e a ausencia destas duas linhas aqui e deliberada —
# o default de db_publicly_accessible e db_allowed_cidrs ja e o estado seguro.
# Acesso administrativo em prod e por tunel SSM.

# INSUMO PENDENTE: informe o dominio e mude enable_tls para true.
# Sem isso a API responde por HTTP, sem certificado.
domain_name = ""
enable_tls  = false

# Conta 594116288641 — saida ecr_repository_url do bootstrap.
ecr_repository = "594116288641.dkr.ecr.us-east-1.amazonaws.com/financial-control"
