environment   = "dev"
aws_region    = "us-east-1"
instance_type = "t3.small"
ebs_size_gb   = 20

# Sem dominio em dev: a API responde por HTTP no IP elastico.
domain_name = ""
enable_tls  = false

# Substituir pela saida ecr_repository_url do bootstrap.
ecr_repository = "REPLACE_ME"
