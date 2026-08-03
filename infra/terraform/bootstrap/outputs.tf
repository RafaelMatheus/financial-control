output "role_arn" {
  description = "ARN da role do CI. Registrar como variable AWS_ROLE_ARN no GitHub."
  value       = aws_iam_role.github_actions.arn
}

output "state_bucket" {
  description = "Bucket do state remoto. Usar em envs/*/backend.hcl."
  value       = aws_s3_bucket.terraform_state.id
}

output "ecr_repository_url" {
  description = "URI do repositorio ECR. Registrar como variable ECR_REPOSITORY no GitHub."
  value       = aws_ecr_repository.app.repository_url
}

output "aws_region" {
  description = "Regiao. Registrar como variable AWS_REGION no GitHub."
  value       = var.aws_region
}

output "ecr_web_repository_url" {
  description = "URI do repositorio ECR do front (variavel ECR_WEB_REPOSITORY nos dois repos)"
  value       = aws_ecr_repository.web.repository_url
}
