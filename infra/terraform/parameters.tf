# Credenciais do banco no Parameter Store (RF-53).
# Nunca hardcoded no Terraform nem versionadas: a senha e gerada aleatoriamente
# e fica apenas no state (criptografado) e no Parameter Store.

resource "random_password" "db" {
  length  = 32
  special = false # evita caracteres que quebrariam a URL JDBC
}

resource "aws_ssm_parameter" "db_user" {
  name  = "/${local.name}/db/user"
  type  = "String"
  value = "financial"
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${local.name}/db/password"
  type  = "SecureString"
  value = random_password.db.result

  lifecycle {
    # Nao regenerar a senha em applies futuros: mudaria a credencial de um
    # banco que ja tem dados, e a aplicacao deixaria de conectar.
    ignore_changes = [value]
  }
}
