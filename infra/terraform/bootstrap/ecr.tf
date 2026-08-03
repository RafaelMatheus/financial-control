# Registry da imagem da aplicacao (RF-86, D-23).
# A EC2 puxa a imagem pela IAM role da instancia — nenhum token de registry na maquina.

resource "aws_ecr_repository" "app" {
  name                 = var.project_name
  image_tag_mutability = "IMMUTABLE" # tag por commit SHA nunca e sobrescrita (RF-87)

  image_scanning_configuration {
    scan_on_push = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

# Retencao: mantem as 20 imagens mais recentes.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Mantem as 20 imagens mais recentes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# Registry da imagem do FRONT, publicada pelo repositorio financial-control-web.
#
# Diferente do repositorio da aplicacao, este e MUTABLE: alem da tag por commit
# SHA, o front publica `latest`, e a composicao do backend a usa como default.
# Sem isso, um deploy do backend anterior ao primeiro deploy do front tentaria
# puxar uma tag inexistente.
resource "aws_ecr_repository" "web" {
  name                 = "${var.project_name}-web"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_lifecycle_policy" "web" {
  repository = aws_ecr_repository.web.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Mantem as 20 imagens mais recentes"
      selection = {
        # `tagStatus = tagged` com prefixo vazio nao se aplica a `latest`, que
        # e sempre a mais recente e nunca expira por contagem.
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}
