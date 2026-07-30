locals {
  name = "${var.project_name}-${var.environment}"
}

module "network" {
  source = "./modules/network"

  project_name = local.name
}

module "security" {
  source = "./modules/security"

  project_name = local.name
  vpc_id       = module.network.vpc_id
  aws_region   = var.aws_region
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
}
