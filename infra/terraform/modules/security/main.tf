# Security group: superficie minima (RNF-16).
#
# Portas 22 (SSH) e 5432 (PostgreSQL) NAO tem regra de ingresso — e proposital:
#   22   -> acesso administrativo por SSM Session Manager (RF-90)
#   5432 -> PostgreSQL so na rede interna do docker compose
#
# Nao adicione regra para nenhuma das duas.

resource "aws_security_group" "app" {
  name        = "${var.project_name}-sg"
  description = "HTTPS e HTTP publicos; SSH e PostgreSQL fechados"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.project_name}-sg" }
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

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.app.id
  description       = "Saida liberada: pull do ECR, Let's Encrypt, agente SSM"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
