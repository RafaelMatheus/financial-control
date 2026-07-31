# OIDC provider do GitHub Actions (RF-82).
# Elimina credenciais AWS de longa duracao no repositorio: o Actions assume esta
# role apresentando um token OIDC de curta duracao.

# Le o thumbprint do certificado atual do GitHub em vez de fixar um valor.
# Thumbprints copiados de tutoriais ficam desatualizados quando o GitHub rotaciona
# o certificado, e o sintoma e "The web identity token provided could not be validated".
data "tls_certificate" "github" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

# ---------------------------------------------------------------------------
# Adocao dos recursos criados manualmente no console.
#
# O OIDC provider e a role ja existem: foram criados a mao para destravar o
# pipeline. Um `import` block adota o recurso existente durante o apply, em vez
# de tentar criar um segundo ao lado — provider OIDC e unico por conta, e o
# apply falharia com EntityAlreadyExists.
#
# Depois do primeiro apply bem-sucedido estes dois blocos podem ser removidos:
# cumprida a adocao, eles nao fazem mais nada.
# ---------------------------------------------------------------------------

import {
  to = aws_iam_openid_connect_provider.github
  id = "arn:aws:iam::${var.aws_account_id}:oidc-provider/token.actions.githubusercontent.com"
}

import {
  to = aws_iam_role.github_actions
  id = var.ci_role_name
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  thumbprint_list = distinct(concat(
    [for c in data.tls_certificate.github.certificates : c.sha1_fingerprint],
    # Thumbprints historicos do GitHub, mantidos por seguranca durante rotacoes.
    [
      "6938fd4d98bab03faadb97b34396831e3780aea1",
      "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
    ]
  ))
}

# Trust policy restrita a REPOSITORIO e BRANCH especificos (RF-93).
# Sem a condicao de "sub", qualquer repositorio do GitHub poderia assumir a role.
data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    effect = "Allow"
    # sts:TagSession estava na trust policy criada no console e e mantido: se a
    # action marcar a sessao com tags, remover isto quebra a assuncao da role —
    # e quebraria justamente a role que o CI usa para consertar o proprio erro.
    actions = ["sts:AssumeRoleWithWebIdentity", "sts:TagSession"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Multiplos valores numa condicao IAM sao um OU. A lista e a UNIAO do padrao
    # desenhado com o que a role ja tinha e que comprovadamente funciona, para
    # que o apply nao possa derrubar a autenticacao do proprio CI.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = concat(
        [
          "repo:${var.github_repository}:ref:refs/heads/${var.github_branch}",
          "repo:${var.github_repository}:pull_request",
        ],
        var.extra_trusted_subs,
      )
    }
  }
}

resource "aws_iam_role" "github_actions" {
  # Nome vem de variavel porque a role ja existe com o nome `github-actions`,
  # criado manualmente. O import block acima a adota com este nome.
  name               = var.ci_role_name
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
  description        = "Role assumida pelo GitHub Actions via OIDC"
}

# Permissoes do CI: gerenciar a infraestrutura, publicar no ECR e disparar deploy por SSM.
data "aws_iam_policy_document" "github_actions" {
  statement {
    sid    = "TerraformState"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*",
    ]
  }

  statement {
    sid    = "EcrPushPull"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:DescribeRepositories",
      "ecr:DescribeImages",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "SsmDeploy"
    effect = "Allow"
    actions = [
      "ssm:SendCommand",
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
      "ssm:DescribeInstanceInformation",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "InfrastructureManagement"
    effect = "Allow"
    actions = [
      "ec2:*",
      # RDS gerenciado (D-37): sem estas acoes o apply falha ao criar
      # aws_db_instance, aws_db_subnet_group e aws_db_parameter_group.
      "rds:*",
      # RDS cria a service-linked role na primeira instancia da conta.
      "iam:CreateServiceLinkedRole",
      # storage_encrypted = true com a chave padrao aws/rds.
      "kms:DescribeKey",
      "kms:CreateGrant",
      "iam:GetRole",
      "iam:PassRole",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:TagRole",
      "iam:TagInstanceProfile",
      "ssm:PutParameter",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:DeleteParameter",
      "ssm:DescribeParameters",
      "ssm:AddTagsToResource",
      "ssm:ListTagsForResource",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "${var.project_name}-github-actions"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions.json
}
