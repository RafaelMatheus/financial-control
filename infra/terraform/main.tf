locals {
  name = "${var.project_name}-${var.environment}"
}

module "network" {
  source = "./modules/network"

  project_name = local.name
  # A rota acompanha a exposicao do banco: ligar uma sem a outra produz um RDS
  # com IP publico e sem rota de resposta, exposto e inalcancavel ao mesmo tempo.
  enable_database_internet_route = var.db_publicly_accessible
}

module "security" {
  source = "./modules/security"

  project_name           = local.name
  vpc_id                 = module.network.vpc_id
  aws_region             = var.aws_region
  database_allowed_cidrs = var.db_allowed_cidrs
}

module "database" {
  source = "./modules/database"

  project_name          = local.name
  private_subnet_ids    = module.network.private_subnet_ids
  security_group_id     = module.security.database_security_group_id
  instance_class        = var.db_instance_class
  allocated_storage     = var.db_allocated_storage
  master_password       = random_password.db_master.result
  multi_az              = var.db_multi_az
  backup_retention_days = var.db_backup_retention_days
  publicly_accessible   = var.db_publicly_accessible
}

module "compute" {
  source = "./modules/compute"

  project_name          = local.name
  aws_region            = var.aws_region
  instance_type         = var.instance_type
  subnet_id             = module.network.public_subnet_id
  security_group_id     = module.security.app_security_group_id
  instance_profile_name = module.security.instance_profile_name
  ecr_repository        = var.ecr_repository
  domain_name           = var.domain_name
  enable_tls            = var.enable_tls
  environment           = var.environment

  # O user-data le o Parameter Store logo no boot e roda com `set -e`. Sem esta
  # dependencia explicita, a instancia pode subir antes de os parametros
  # existirem, o script aborta e o .env nunca e escrito — foi o que aconteceu no
  # primeiro provisionamento de dev. Nao ha referencia entre os recursos que
  # crie a ordem implicitamente, entao ela precisa ser declarada.
  depends_on = [
    aws_ssm_parameter.db_url,
    aws_ssm_parameter.db_app_username,
    aws_ssm_parameter.db_app_password,
  ]
}
