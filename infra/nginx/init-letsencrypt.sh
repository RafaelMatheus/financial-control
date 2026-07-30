#!/bin/bash
# Emissao inicial do certificado. Rodar UMA VEZ na instancia, apos o DNS do
# dominio ja apontar para o IP elastico. A renovacao e automatica depois disso.
set -euo pipefail

DOMAIN="${1:?uso: ./init-letsencrypt.sh <dominio> <email>}"
EMAIL="${2:?uso: ./init-letsencrypt.sh <dominio> <email>}"
APP_DIR="/opt/financial-control"

cd "$APP_DIR"

# Substitui o placeholder do nginx.conf pelo dominio real.
sed -i "s|/etc/letsencrypt/live/DOMAIN/|/etc/letsencrypt/live/${DOMAIN}/|g" nginx/nginx.conf

# Sobe o nginx sem TLS para responder ao desafio HTTP-01.
docker compose -f docker-compose.prod.yml up -d nginx

docker compose -f docker-compose.prod.yml run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" --email "$EMAIL" \
  --agree-tos --no-eff-email --non-interactive

docker compose -f docker-compose.prod.yml restart nginx
echo "Certificado emitido para ${DOMAIN}. Renovacao automatica a cada 12h."
