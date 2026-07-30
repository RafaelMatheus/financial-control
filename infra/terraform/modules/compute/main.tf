# EC2 t3.small (D-11), apenas com a aplicacao e o nginx.
# O PostgreSQL saiu daqui — agora e RDS gerenciado, em subnet privada.

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = var.instance_profile_name

  user_data                   = local.user_data
  user_data_replace_on_change = false # trocar user-data NAO recria a instancia

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  tags = { Name = var.project_name }

  lifecycle {
    # A AMI mais recente muda quando a Amazon publica uma imagem nova; sem isto
    # todo apply recriaria a instancia.
    ignore_changes = [ami]
  }
}

locals {
  user_data = templatefile("${path.module}/user-data.sh", {
    project_name   = var.project_name
    aws_region     = var.aws_region
    ecr_repository = var.ecr_repository
    domain_name    = var.domain_name
    enable_tls     = var.enable_tls
  })
}

# IP fixo: o DNS do dominio aponta para ele.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = { Name = "${var.project_name}-eip" }

  lifecycle {
    prevent_destroy = true
  }
}
