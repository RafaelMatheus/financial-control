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

# ------------------------------------------------- Volume EBS (idempotente)
# CRITICO: so formata se NAO houver filesystem. Sem esta verificacao o volume
# seria reformatado a cada boot, apagando o banco.
DEVICE="${device_name}"
MOUNT_POINT="/mnt/data"

for i in {1..30}; do
  [ -b "$${DEVICE}" ] && break
  # Instancias nitro renomeiam o device para /dev/nvme*
  if [ -b /dev/nvme1n1 ]; then DEVICE=/dev/nvme1n1; break; fi
  echo "aguardando volume ($${i}/30)..."; sleep 2
done

if ! blkid "$${DEVICE}" > /dev/null 2>&1; then
  echo "volume sem filesystem — formatando (primeiro boot)"
  mkfs -t xfs "$${DEVICE}"
else
  echo "volume ja possui filesystem — apenas montando (dados preservados)"
fi

mkdir -p "$${MOUNT_POINT}"
UUID=$(blkid -s UUID -o value "$${DEVICE}")
grep -q "$${UUID}" /etc/fstab || \
  echo "UUID=$${UUID} $${MOUNT_POINT} xfs defaults,nofail 0 2" >> /etc/fstab
mount -a
mkdir -p "$${MOUNT_POINT}/postgres"

# ----------------------------------------------------- Segredos e aplicacao
APP_DIR="/opt/${project_name}"
mkdir -p "$${APP_DIR}"

DB_USER=$(aws ssm get-parameter --region ${aws_region} \
  --name "/${project_name}/db/user" --with-decryption --query Parameter.Value --output text)
DB_PASSWORD=$(aws ssm get-parameter --region ${aws_region} \
  --name "/${project_name}/db/password" --with-decryption --query Parameter.Value --output text)

cat > "$${APP_DIR}/.env" <<ENVFILE
AWS_REGION=${aws_region}
ECR_REPOSITORY=${ecr_repository}
IMAGE_TAG=latest
DB_USER=$${DB_USER}
DB_PASSWORD=$${DB_PASSWORD}
DOMAIN_NAME=${domain_name}
ENABLE_TLS=${enable_tls}
ENVFILE
chmod 600 "$${APP_DIR}/.env"

echo "=== user-data concluido: $(date -Is) ==="
echo "A aplicacao sobe no primeiro deploy, disparado pelo workflow deploy-app.yml."
