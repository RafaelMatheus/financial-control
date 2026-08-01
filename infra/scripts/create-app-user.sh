#!/bin/bash
# Passo 5b do runbook: cria o usuario da aplicacao no PostgreSQL.
#
# O Terraform cria o database e o usuario master, mas nao este: o banco esta em
# subnet privada, inalcancavel de onde o Terraform roda. Este script roda NA
# INSTANCIA, por SSM Run Command, que e o unico lugar com rota ate o RDS.
#
# Uso: create-app-user.sh <environment>
#
# Idempotente: rodar de novo sincroniza a senha com o Parameter Store em vez de
# falhar com "role already exists".

set -euo pipefail

ENVIRONMENT="${1:?informe o ambiente: dev ou prod}"
REGION="${AWS_REGION:-us-east-1}"
PREFIX="/financial-control-${ENVIRONMENT}"

get() {
  aws ssm get-parameter --region "$REGION" --name "$1" \
    --with-decryption --query Parameter.Value --output text
}

DB_HOST=$(get "$PREFIX/db/url" | sed -E 's|jdbc:postgresql://([^:]+):.*|\1|')
DB_NAME=$(get "$PREFIX/db/url" | sed -E 's|.*/([^?]+)\?.*|\1|')
ADMIN_USER=$(get "$PREFIX/db/master-username")
ADMIN_PASS=$(get "$PREFIX/db/master-password")
APP_USER=$(get "$PREFIX/db/user")
APP_PASS=$(get "$PREFIX/db/password")

# sslmode=require e obrigatorio: o parameter group tem rds.force_ssl = 1.
ADMIN_CONN="host=$DB_HOST user=$ADMIN_USER dbname=$DB_NAME sslmode=require"

export PGPASSWORD="$ADMIN_PASS"

if [ "$(psql "$ADMIN_CONN" -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'")" = "1" ]; then
  psql "$ADMIN_CONN" -c "ALTER ROLE $APP_USER WITH LOGIN PASSWORD '$APP_PASS'"
  echo "role $APP_USER ja existia — senha sincronizada com o Parameter Store"
else
  psql "$ADMIN_CONN" -c "CREATE ROLE $APP_USER WITH LOGIN PASSWORD '$APP_PASS'"
  echo "role $APP_USER criada"
fi

# CREATE no schema e necessario porque o Flyway cria as tabelas na primeira
# execucao da aplicacao.
psql "$ADMIN_CONN" -c "GRANT CONNECT ON DATABASE $DB_NAME TO $APP_USER"
psql "$ADMIN_CONN" -c "GRANT USAGE, CREATE ON SCHEMA public TO $APP_USER"

# Verificacao: um GRANT que retorna sucesso nao prova que o usuario conecta.
unset PGPASSWORD
PGPASSWORD="$APP_PASS" psql "host=$DB_HOST user=$APP_USER dbname=$DB_NAME sslmode=require" \
  -tAc "SELECT 'conectado como ' || current_user || ' em ' || current_database()"
