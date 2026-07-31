#!/bin/bash
# Cria o usuario da aplicacao no banco — Passo 5b do runbook.
#
# O Terraform cria o database e o usuario master, mas nao o usuario da
# aplicacao: o RDS fica em subnet privada, inalcancavel de onde o Terraform
# roda. Este script roda NA INSTANCIA, que esta na mesma VPC.
#
# Idempotente de proposito: pode ser reexecutado sem erro, e reexecutar
# sincroniza a senha com o que esta no Parameter Store.
#
# Uso: db-bootstrap.sh <prefixo-do-parameter-store>
#      db-bootstrap.sh /financial-control-dev

set -euo pipefail

PREFIX="${1:?informe o prefixo do Parameter Store, ex: /financial-control-dev}"

TOKEN="$(curl -sX PUT --max-time 3 http://169.254.169.254/latest/api/token \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' || true)"
REGION="$(curl -s --max-time 3 -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/placement/region || echo us-east-1)"

get() {
  aws ssm get-parameter --region "$REGION" --name "$1" \
    --with-decryption --query Parameter.Value --output text
}

DB_URL="$(get "$PREFIX/db/url")"
DB_HOST="$(echo "$DB_URL" | sed -E 's|jdbc:postgresql://([^:/]+).*|\1|')"
DB_NAME="$(echo "$DB_URL" | sed -E 's|.*/([^?]+).*|\1|')"

ADMIN_USER="$(get "$PREFIX/db/master-username")"
ADMIN_PASS="$(get "$PREFIX/db/master-password")"
APP_USER="$(get "$PREFIX/db/user")"
APP_PASS="$(get "$PREFIX/db/password")"

echo "Host....: $DB_HOST"
echo "Database: $DB_NAME"
echo "Usuario.: $APP_USER"

# A senha vai como variavel do psql e e interpolada com %L pelo format(), que
# escapa literais corretamente. Nao ha concatenacao de string crua em SQL.
#
# \gexec executa o texto produzido pelo SELECT anterior — e como se monta DDL
# dinamico no psql, ja que CREATE ROLE nao aceita parametro nem cabe em DO com
# variavel de cliente.
PGPASSWORD="$ADMIN_PASS" psql \
  "host=$DB_HOST user=$ADMIN_USER dbname=$DB_NAME sslmode=require" \
  --set=app_user="$APP_USER" \
  --set=app_pass="$APP_PASS" \
  --set ON_ERROR_STOP=1 <<'SQL'
\echo 'Criando ou atualizando o papel da aplicacao...'

SELECT format(
  CASE WHEN EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
       THEN 'ALTER ROLE %I WITH LOGIN PASSWORD %L'
       ELSE 'CREATE ROLE %I WITH LOGIN PASSWORD %L'
  END, :'app_user', :'app_pass')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'app_user')
\gexec

-- CREATE no schema public: o Flyway precisa criar tabelas (D-01, RNF-04).
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user')
\gexec

\echo 'Pronto.'
SQL

echo "Usuario da aplicacao pronto."
