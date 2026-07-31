environment   = "dev"
aws_region    = "us-east-1"
instance_type = "t3.small"
db_instance_class        = "db.t4g.micro"
db_allocated_storage     = 20
db_multi_az              = false
# 1 dia, nao 7: a conta esta no plano Free Tier, que recusa retencoes maiores
# com FreeTierRestrictionError. Aceitavel em dev, que nao tem dado real.
# Em prod isto reabre o risco R-01 — ver a decisao pendente no aidlc-state.
db_backup_retention_days = 1

# Sem dominio em dev: a API responde por HTTP no IP elastico.
domain_name = ""
enable_tls  = false

# Conta 594116288641 — saida ecr_repository_url do bootstrap.
ecr_repository = "594116288641.dkr.ecr.us-east-1.amazonaws.com/financial-control"
