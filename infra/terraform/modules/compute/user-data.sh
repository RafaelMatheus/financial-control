#!/bin/bash
set -euo pipefail

exec > >(tee /var/log/user-data.log | logger -t user-data) 2>&1
echo "=== user-data iniciado: $(date -Is) ==="

# ---------------------------------------------------------------- Docker
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

DOCKER_COMPOSE_VERSION="v2.29.7"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL \
  "https://github.com/docker/compose/releases/download/$${DOCKER_COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Cliente psql: usado uma vez para criar o usuario da aplicacao (ver runbook)
dnf install -y postgresql16

# --------------------------------------------------- Segredos e configuracao
# O banco e RDS gerenciado: nao ha volume a montar nem formatar.
APP_DIR="/opt/${project_name}"
mkdir -p "$${APP_DIR}"

get_param() {
  aws ssm get-parameter --region ${aws_region} --name "$1" \
    --with-decryption --query Parameter.Value --output text
}

DB_URL=$(get_param "/${project_name}/db/url")
DB_USER=$(get_param "/${project_name}/db/user")
DB_PASSWORD=$(get_param "/${project_name}/db/password")
JWT_SECRET=$(get_param "/${project_name}/auth/jwt-secret")

cat > "$${APP_DIR}/.env" <<ENVFILE
AWS_REGION=${aws_region}
ECR_REPOSITORY=${ecr_repository}
IMAGE_TAG=latest
DB_URL=$${DB_URL}
DB_USER=$${DB_USER}
DB_PASSWORD=$${DB_PASSWORD}
JWT_SECRET=$${JWT_SECRET}
DOMAIN_NAME=${domain_name}
ENABLE_TLS=${enable_tls}
ENVFILE
chmod 600 "$${APP_DIR}/.env"

echo "=== user-data concluido: $(date -Is) ==="
echo "A aplicacao sobe no primeiro deploy, disparado pelo workflow deploy-app.yml."
echo "Antes disso, crie o usuario da aplicacao no banco — ver runbook, Passo 5."
