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

module "compute" {
  source = "./modules/compute"

  project_name          = local.name
  aws_region            = var.aws_region
  instance_type         = var.instance_type
  subnet_id             = module.network.subnet_id
  security_group_id     = module.security.security_group_id
  instance_profile_name = module.security.instance_profile_name
  ecr_repository        = var.ecr_repository
  domain_name           = var.domain_name
  enable_tls            = var.enable_tls
}

module "storage" {
  source = "./modules/storage"

  project_name      = local.name
  availability_zone = module.compute.availability_zone
  instance_id       = module.compute.instance_id
  size_gb           = var.ebs_size_gb
}
