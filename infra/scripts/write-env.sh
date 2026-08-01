#!/bin/bash
# Escreve o .env da instancia a partir do Parameter Store.
#
# O user-data ja faz isto no primeiro boot, mas nao da para depender disso: se a
# instancia subir antes de os parametros existirem, ele aborta em `set -e` e o
# diretorio fica sem .env — foi o que aconteceu no primeiro provisionamento.
#
# Rodar isto a cada deploy torna o estado da instancia convergente, em vez de
# depender de um boot que deu certo. Idempotente.
#
# Uso: write-env.sh <ambiente> <ecr_repository>
#      write-env.sh dev 594116288641.dkr.ecr.us-east-1.amazonaws.com/financial-control

set -euo pipefail

ENVIRONMENT="${1:?informe o ambiente, ex: dev}"
ECR="${2:?informe o repositorio ECR}"

NAME="financial-control-$ENVIRONMENT"
APP_DIR="/opt/$NAME"
PREFIX="/$NAME"

TOKEN="$(curl -sX PUT --max-time 3 http://169.254.169.254/latest/api/token \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' || true)"
REGION="$(curl -s --max-time 3 -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/placement/region || echo us-east-1)"

get() {
  aws ssm get-parameter --region "$REGION" --name "$1" \
    --with-decryption --query Parameter.Value --output text
}

mkdir -p "$APP_DIR"

# Valores que nao vem do Parameter Store sao preservados do .env anterior,
# quando existe. IMAGE_TAG em especial: quem manda nele e o passo de deploy,
# que o reescreve logo depois.
preservar() {
  local chave="$1" padrao="$2"
  if [ -f "$APP_DIR/.env" ] && grep -qE "^$chave=" "$APP_DIR/.env"; then
    grep -E "^$chave=" "$APP_DIR/.env" | head -1 | cut -d= -f2-
  else
    echo "$padrao"
  fi
}

TAG_ATUAL="$(preservar IMAGE_TAG latest)"
DOMINIO="$(preservar DOMAIN_NAME '')"
TLS="$(preservar ENABLE_TLS false)"

umask 077
cat > "$APP_DIR/.env" <<ENVFILE
AWS_REGION=$REGION
ECR_REPOSITORY=$ECR
IMAGE_TAG=$TAG_ATUAL
DB_URL=$(get "$PREFIX/db/url")
DB_USER=$(get "$PREFIX/db/user")
DB_PASSWORD=$(get "$PREFIX/db/password")
JWT_SECRET=$(get "$PREFIX/auth/jwt-secret")
DOMAIN_NAME=$DOMINIO
ENABLE_TLS=$TLS
# Perfil do Spring: ativa application-dev.yml em dev (Swagger UI ligado).
APP_PROFILE=$ENVIRONMENT
ENVFILE
chmod 600 "$APP_DIR/.env"

echo ".env escrito em $APP_DIR"
echo "  IMAGE_TAG=$TAG_ATUAL  ENABLE_TLS=$TLS  DOMAIN_NAME=${DOMINIO:-<vazio>}"
