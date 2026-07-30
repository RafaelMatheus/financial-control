# Dois security groups.
#
# app: superficie publica minima (RNF-16).
#   Portas 22 (SSH) e 5432 NAO tem regra de ingresso — e proposital:
#     22   -> acesso administrativo por SSM Session Manager (RF-90)
#     5432 -> a aplicacao e CLIENTE do banco, nao servidor
#   Nao adicione regra para nenhuma das duas.
#
# database: 5432 apenas a partir do security group da aplicacao.
#   Nenhum CIDR — a origem e o proprio SG, entao mudar o IP da EC2
#   nao quebra o acesso, e nenhum outro recurso alcanca o banco.

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app"
  description = "HTTPS e HTTP publicos; SSH fechado"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.project_name}-app" }
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS da API"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.app.id
  description       = "HTTP: redirect para 443 e validacao HTTP-01 do Let's Encrypt"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "Saida liberada: ECR, Let's Encrypt, agente SSM, banco"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ------------------------------------------------------------- banco
resource "aws_security_group" "database" {
  name        = "${var.project_name}-database"
  description = "PostgreSQL acessivel apenas a partir da aplicacao"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.project_name}-database" }
}

resource "aws_vpc_security_group_ingress_rule" "postgres_from_app" {
  security_group_id            = aws_security_group.database.id
  description                  = "PostgreSQL apenas a partir do SG da aplicacao"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
