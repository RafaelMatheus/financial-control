# Runbook de Bootstrap — provisionamento inicial

**Status**: 🟡 **Esboço preliminar** — a versão executável, com os valores reais (região, IDs de
recurso, ARNs), será produzida na stage **Infrastructure Design**, quando D-11 (dimensionamento da
EC2, AMI, região e rede) estiver decidido.

**Propósito**: documentar os passos **manuais e únicos** que precedem o funcionamento do pipeline
(RF-92). Depois deste bootstrap, todo o resto acontece no GitHub Actions.

---

## 1. Por que existe um bootstrap manual

Há um ovo-e-galinha: o `terraform apply` no CI precisa de um bucket S3 para guardar o state (RF-51)
e de uma IAM role para o Actions assumir por OIDC (RF-82). Mas esses recursos **são**
infraestrutura — só o Terraform os criaria. O CI não pode criar aquilo de que depende para rodar.

```
   +--------------------------------------------------+
   |  BOOTSTRAP (manual, uma vez, na sua maquina)     |
   |  state local, sem backend remoto                 |
   |                                                  |
   |  cria: bucket S3 do state                        |
   |        mecanismo de lock                         |
   |        OIDC provider do GitHub                   |
   |        IAM role assumida pelo Actions            |
   |        repositorio ECR                           |
   +--------------------------------------------------+
                          |
                          | a partir daqui o CI tem
                          | onde guardar state e
                          | como se autenticar
                          v
   +--------------------------------------------------+
   |  PIPELINE (GitHub Actions, toda vez)             |
   |  state remoto no S3, auth por OIDC               |
   |                                                  |
   |  cria: VPC, subnet, security group                |
   |        EC2 + volume EBS                          |
   |        IAM role da instancia                     |
   +--------------------------------------------------+
```

---

## 2. Pré-requisitos

Antes de começar, tenha:

- [ ] Conta AWS com permissão administrativa (o bootstrap cria IAM roles e OIDC provider)
- [ ] AWS CLI instalada e autenticada — `aws sts get-caller-identity` deve responder com a conta certa
- [ ] Terraform instalado (versão a fixar na Infrastructure Design)
- [ ] Permissão de administrador no repositório GitHub (para configurar variables/secrets)
- [ ] Região AWS definida — **pendente de D-11**

> ⚠️ Confira a conta antes de rodar qualquer `apply`. `aws sts get-caller-identity` é o comando que
> evita provisionar na conta errada.

---

## 3. Sequência de execução

### Passo 1 — Aplicar o bootstrap
```bash
cd infra/terraform/bootstrap
terraform init          # state local, sem backend remoto
terraform plan          # CONFIRA a lista de recursos antes de seguir
terraform apply
```

**O que será criado**: bucket S3 do state, mecanismo de lock, OIDC provider do GitHub, IAM role para
o Actions (com trust policy restrita ao repositório e branch — RF-93) e repositório ECR.

**Saídas a anotar**: ARN da role, nome do bucket, URI do repositório ECR.

> 🔒 O `terraform.tfstate` deste módulo fica **local**. Guarde-o — sem ele você perde a referência
> dos recursos de bootstrap. Ele **não** deve ser versionado (RNF-15).

### Passo 2 — Configurar o repositório GitHub
Registrar as saídas do Passo 1 como *variables* do repositório (não são segredos — a role só pode
ser assumida por este repositório e branch):

- [ ] `AWS_ROLE_ARN` — ARN da role criada
- [ ] `AWS_REGION` — região escolhida
- [ ] `ECR_REPOSITORY` — URI do repositório ECR

### Passo 3 — Validar a autenticação OIDC
Abrir um PR qualquer que toque `infra/**` e confirmar que o workflow de `terraform plan` (RF-84)
autentica e roda. **Se o OIDC estiver mal configurado, falha aqui** — antes de qualquer recurso
real ser criado.

### Passo 4 — Primeiro apply da infraestrutura
Fazer merge em `main`. O workflow de apply (RF-85) provisiona VPC, security group, EC2, volume EBS e
a IAM role da instância.

> ⚠️ **Não há gate de aprovação** — o merge aplica direto (D-25, risco R-05). Leia o `plan` do PR
> antes de mergear, especialmente se houver `destroy` ou `replace` na lista.

### Passo 5 — Primeiro deploy da aplicação
O workflow de build (RF-86) constrói a imagem, publica no ECR com tag do commit SHA (RF-87), e o
SSM Run Command (RF-88) atualiza a instância.

### Passo 6 — Verificação
- [ ] `GET /actuator/health` responde `{"status":"UP"}`
- [ ] Porta 22 **fechada** no security group (RF-90)
- [ ] Porta 5432 **não** acessível pela internet (RNF-16)
- [ ] Volume EBS do PostgreSQL montado e separado do volume raiz (RF-50)
- [ ] Migrations Flyway aplicadas — a aplicação sobe com `ddl-auto: validate` passando (RNF-04)

---

## 4. Reversão

```bash
# derrubar a infraestrutura provisionada pelo pipeline
cd infra/terraform
terraform destroy

# derrubar o bootstrap (por ultimo, e so se for abandonar o projeto)
cd bootstrap
terraform destroy
```

> ⚠️ `terraform destroy` na infraestrutura principal **remove o volume EBS com os dados do
> PostgreSQL**. Antes de rodar, execute a rotina de backup (RF-54). Sem RDS não há snapshot
> automático para voltar (risco R-01).

---

## 5. Pontos que a Infrastructure Design precisa fechar

| Item | Decisão |
|---|---|
| Região AWS e AMI | D-11 |
| Tipo e tamanho da instância EC2 | D-11 |
| Topologia de rede (VPC própria ou default, subnet pública ou privada) | D-11 |
| Versão do Terraform e dos providers | D-11 |
| Mecanismo de lock do state (DynamoDB ou lockfile S3 nativo) | D-11 |
| `prevent_destroy` nos recursos com estado — mitigação de R-05 | Infrastructure Design |
| Como a aplicação recebe as credenciais do banco na instância | RF-53 |
| Rotina concreta de backup e restauração | RF-54 |
