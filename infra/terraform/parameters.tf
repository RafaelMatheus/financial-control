# Credenciais no Parameter Store (RF-53).
# Senhas geradas pelo Terraform, nunca hardcoded nem versionadas. Ficam apenas
# no state (criptografado) e no Parameter Store, lido pela IAM role da instancia.
#
# Dois usuarios:
#   financial_admin -> master, administracao apenas
#   financial_app   -> aplicacao, permissao so no database financial_control
#
# O usuario da aplicacao e criado por SQL, uma unica vez — ver runbook. O
# Terraform nao o cria porque o banco esta em subnet privada, inalcancavel de
# onde o Terraform roda.

resource "random_password" "db_master" {
  length  = 32
  special = false # evita caracteres que quebrariam a URL JDBC
}

resource "random_password" "db_app" {
  length  = 32
  special = false
}

resource "aws_ssm_parameter" "db_master_username" {
  name  = "/${local.name}/db/master-username"
  type  = "String"
  value = "financial_admin"
}

resource "aws_ssm_parameter" "db_master_password" {
  name  = "/${local.name}/db/master-password"
  type  = "SecureString"
  value = random_password.db_master.result

  lifecycle {
    # Regenerar a senha em applies futuros trocaria a credencial de um banco
    # ja em uso. A rotacao, quando necessaria, e deliberada.
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "db_app_username" {
  name  = "/${local.name}/db/user"
  type  = "String"
  value = "financial_app"
}

resource "aws_ssm_parameter" "db_app_password" {
  name  = "/${local.name}/db/password"
  type  = "SecureString"
  value = random_password.db_app.result

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "db_url" {
  name        = "/${local.name}/db/url"
  type        = "String"
  value       = module.database.jdbc_url
  description = "URL JDBC com sslmode=require"
}

# Segredo de assinatura do JWT (D-02, NFR-U1-02).
#
# Girar este valor invalida TODOS os tokens em circulacao. E o comportamento
# correto, e a unica revogacao disponivel: D-50 dispensou refresh token e lista
# de bloqueio, entao nao ha como invalidar um token isolado antes das 24h.
# Vale como procedimento de emergencia — se um token vazar, gire o segredo.
resource "random_password" "jwt_secret" {
  length  = 64
  special = false # base64url-safe: o segredo atravessa .env e variavel de ambiente
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${local.name}/auth/jwt-secret"
  type  = "SecureString"
  value = random_password.jwt_secret.result

  lifecycle {
    # Mesma razao das senhas do banco: regenerar em apply futuro deslogaria todo
    # mundo sem que ninguem tivesse pedido.
    ignore_changes = [value]
  }
}
