# Code Generation Summary — U5 Infraestrutura

**Stage**: CONSTRUCTION - Code Generation - Part 2
**Timestamp**: 2026-07-30T16:11:59Z

**38 arquivos** — 36 criados, 2 modificados. **Nenhum código de aplicação**: `src/` não foi tocado.

---

## Criados

### Terraform — bootstrap (manual, uma vez)
| Arquivo | Conteúdo |
|---|---|
| `infra/terraform/bootstrap/versions.tf` | Providers, sem backend remoto (é ele que o cria) |
| `infra/terraform/bootstrap/main.tf` | Bucket S3 versionado e criptografado, com `prevent_destroy` |
| `infra/terraform/bootstrap/oidc.tf` | OIDC provider + role do CI, trust restrita a repo e branch |
| `infra/terraform/bootstrap/ecr.tf` | ECR com tag imutável, scan on push e retenção de 20 imagens |
| `infra/terraform/bootstrap/outputs.tf` | `role_arn`, `state_bucket`, `ecr_repository_url` |
| `infra/terraform/bootstrap/README.md` | Explicação da dependência circular e uso |

### Terraform — módulos
| Módulo | Arquivos | Conteúdo |
|---|---|---|
| `network` | 3 | VPC `10.0.0.0/16`, subnet pública, IGW, route table |
| `security` | 4 | Security group (443/80) + IAM role da instância |
| `storage` | 3 | Volume EBS gp3 separado, com `prevent_destroy` |
| `compute` | 4 | EC2 `t3.small`, Elastic IP, `user-data.sh` |

### Terraform — raiz e ambientes
`versions.tf` · `main.tf` · `variables.tf` · `outputs.tf` · `parameters.tf` ·
`envs/{dev,prod}/terraform.tfvars` · `envs/{dev,prod}/backend.hcl`

### Docker e nginx
`Dockerfile` · `.dockerignore` · `docker-compose.prod.yml` ·
`infra/nginx/nginx.conf` · `infra/nginx/init-letsencrypt.sh`

### Workflows
`ci-app.yml` · `terraform-plan.yml` · `terraform-apply.yml` · `deploy-app.yml`

## Modificados
| Arquivo | Mudança |
|---|---|
| `.gitignore` | `*.tfstate*`, `.terraform/`, `*.tfplan`, tfvars sensíveis (RNF-15) |
| `README.md` | Seção de infraestrutura, pipeline, bootstrap e ressalva sobre backup |

---

## Decisões de implementação

**Lock nativo do S3** em vez de tabela DynamoDB — `use_lockfile = true`, disponível desde o
Terraform 1.10. Menos um recurso a manter e a pagar.

**`ignore_changes = [ami]`** na EC2. A AMI mais recente do Amazon Linux muda quando a Amazon publica
uma imagem nova; sem isso, todo `apply` recriaria a instância.

**`user_data_replace_on_change = false`** — alterar o script de boot não destrói a instância.

**Montagem idempotente do volume** no `user-data.sh`: só formata se `blkid` não encontrar
filesystem. Sem essa verificação, o disco seria reformatado a cada boot, apagando o banco. Também
trata a renomeação do device em instâncias Nitro (`/dev/nvme1n1`).

**Senha do banco gerada** por `random_password`, com `ignore_changes = [value]` no parâmetro. Sem
isso, cada `apply` geraria uma senha nova e a aplicação perderia acesso a um banco já com dados.

**`-XX:MaxRAMPercentage=70`** no runtime. Num `t3.small` de 2 GB dividido com o PostgreSQL, a JVM
assumindo a memória do host causaria OOM.

**Sem `ports` em `app` e `postgres`** no compose — só o nginx publica portas. O PostgreSQL nunca
existe fora da rede interna.

---

## 🔴 Achado durante a verificação: `gradlew` inexistente

`gradlew` e `gradle/wrapper/gradle-wrapper.jar` **não existem no repositório** — débito registrado
na engenharia reversa como severidade Média, que se tornou **bloqueante** aqui: tanto o `Dockerfile`
quanto o `ci-app.yml` originalmente chamavam `./gradlew` e falhariam na primeira execução.

**Correção aplicada** — remover a dependência do wrapper:

| Onde | Antes | Depois |
|---|---|---|
| `Dockerfile` | `FROM eclipse-temurin:21-jdk` + `./gradlew bootJar` | `FROM gradle:8.14.2-jdk21-alpine` + `gradle bootJar` |
| `ci-app.yml` | `./gradlew build` | `setup-gradle` com `gradle-version: 8.14.2` + `gradle build` |

**Recomendação registrada**: versionar `gradlew` e `gradle-wrapper.jar` simplificaria ambos e é a
prática usual. Fica como sugestão, não bloqueio — o pipeline funciona sem eles.

---

## Verificação (Step 14)

| Item | Resultado |
|---|---|
| Porta 22 ou 5432 em security group | ✅ Nenhuma |
| `prevent_destroy` nos recursos com estado | ✅ 4 arquivos — EBS, EIP, ECR, bucket |
| Segredos hardcoded | ✅ Nenhum |
| Filtro de path nos workflows | ✅ 4 de 4 |
| YAML dos workflows | ✅ Válido |
| Arquivos duplicados | ✅ Nenhum |
| Referência a `./gradlew` | ✅ Nenhuma (após correção) |
| `terraform fmt` / `validate` | ⏳ Terraform não instalado localmente — roda no `terraform-plan.yml` |

---

## Cobertura

**RF-45 a RF-53** ✅ · **RF-54** ❌ fora do escopo (D-36) · **RF-81 a RF-93** ✅ ·
**RNF-13 a RNF-16** ✅ · **RNF-17** ⚠️ parcial (só RF-50)

---

## Próximos passos — seus

1. Rodar o bootstrap: `cd infra/terraform/bootstrap && terraform init && terraform apply`
2. Registrar `AWS_ROLE_ARN`, `AWS_REGION` e `ECR_REPOSITORY` como **variables** do repositório
3. Substituir `REPLACE_ME` em `envs/*/terraform.tfvars` e `envs/*/backend.hcl`
4. Abrir um PR tocando `infra/**` para validar o OIDC **antes** de criar recursos cobrados

Passo a passo completo em `../infrastructure-design/deployment-architecture.md` §6.
