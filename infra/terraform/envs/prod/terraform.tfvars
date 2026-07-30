environment   = "prod"
aws_region    = "us-east-1"
instance_type = "t3.small"
ebs_size_gb   = 20

# INSUMO PENDENTE: informe o dominio e mude enable_tls para true.
# Sem isso a API responde por HTTP, sem certificado.
domain_name = ""
enable_tls  = false

# Substituir pela saida ecr_repository_url do bootstrap.
ecr_repository = "REPLACE_ME"
