# financial-control

Aplicação Spring Boot + Kotlin para controle financeiro, construída com a metodologia
[AI-DLC](https://github.com/awslabs/aidlc-workflows) (AI-Driven Development Life Cycle) da AWS.

O domínio é gerado pelo próprio fluxo AI-DLC. A unidade **U1 — Fundação** já está implementada:
identidade, autenticação e grupos, sobre os quais todo o resto se apoia.

## Stack

| Item | Versão |
|---|---|
| Kotlin | 2.1.21 |
| Spring Boot | 3.5.4 |
| JDK | 21 |
| Gradle | 8.14.2 (Kotlin DSL) |
| PostgreSQL | 16 (docker-compose) |
| Testes | JUnit 5 + Testcontainers |

## Pré-requisitos

- JDK 21
- Docker e Docker Compose

## Como rodar

```bash
docker compose up -d
JWT_SECRET=segredo-local-qualquer-com-tamanho-suficiente ./gradlew bootRun
```

A aplicação sobe em `http://localhost:8080`. Health check em `/actuator/health`.

`JWT_SECRET` **não tem default**, de propósito: um default aqui viraria o segredo de produção no dia
em que a variável faltasse. Em produção ele vem do Parameter Store.

O schema é criado pelo **Flyway** na subida (`src/main/resources/db/migration/`). `ddl-auto` fica em
`validate` para denunciar divergência entre as entidades e o schema — nunca troque por `update`.

## Testes

Os testes usam Testcontainers e sobem um PostgreSQL efêmero — basta ter o Docker rodando:

```bash
./gradlew test
```

## Configuração

As credenciais têm default de desenvolvimento em `application.yml` e podem ser sobrescritas por
variável de ambiente. Veja `.env.example`.

| Variável | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/financial_control` |
| `DB_USER` | `financial` |
| `DB_PASSWORD` | `financial` |
| `SERVER_PORT` | `8080` |
| `JWT_SECRET` | **sem default** — obrigatória |

## API

Autenticação por **JWT stateless**, validade de 24 horas, sem refresh token.

```bash
# 1. cadastrar
curl -X POST localhost:8080/usuarios -H 'Content-Type: application/json' \
  -d '{"email":"ana@exemplo.com","senha":"senha-de-teste","nome":"Ana"}'

# 2. autenticar
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@exemplo.com","senha":"senha-de-teste"}' | jq -r .token)

# 3. usar
curl localhost:8080/usuarios/eu -H "Authorization: Bearer $TOKEN"
```

| Método | Rota | Autenticação |
|---|---|---|
| `POST` | `/usuarios` | pública |
| `POST` | `/auth/login` | pública |
| `GET` `PUT` | `/usuarios/eu` | token |
| `GET` `POST` | `/grupos` | token |
| `GET` `PUT` | `/grupos/{id}` | token |
| `POST` | `/grupos/{id}/membros` | token |
| `DELETE` | `/grupos/{id}/membros/{usuarioId}` | token |
| `DELETE` | `/grupos/{id}/membros/eu` | token |

Não existe rota que aceite identificador de usuário para consultar perfil: o perfil é sempre o do
token. A regra fica na assinatura, não numa validação que alguém pode esquecer.

Especificação OpenAPI gerada em `/v3/api-docs`. O Swagger UI vem **desabilitado** — a especificação
completa é um mapa da superfície de ataque.

## Infraestrutura e deploy

A aplicação roda numa instância **AWS EC2** (`t3.small`, `us-east-1`) e o banco é **RDS PostgreSQL
gerenciado** em subnet privada. Toda a infraestrutura é descrita em **Terraform**, em
`infra/terraform/`, e provisionada pelo **GitHub Actions**.

Custo estimado: **~US$ 35/mês**.

### Pipeline

| Workflow | Gatilho | O que faz |
|---|---|---|
| `ci-app.yml` | PR em `src/**` | Build Gradle e testes com Testcontainers |
| `bootstrap.yml` | manual | Bucket de state, ECR, OIDC provider e role do CI |
| `db-bootstrap.yml` | manual | Cria o usuário da aplicação no RDS |
| `terraform-plan.yml` | PR em `infra/**` | `plan` e publica o diff como comentário no PR |
| `terraform-apply.yml` | merge em `main`, `infra/**` | `apply` automático |
| `deploy-app.yml` | merge em `main`, `src/**` | Build da imagem, push para ECR e deploy via SSM |

Autenticação na AWS por **OIDC** — nenhuma credencial de longa duração no repositório.

### Variáveis do repositório

Em *Settings → Secrets and variables → Actions → **Variables*** (não Secrets):

| Name | Value |
|---|---|
| `AWS_REGION` | `us-east-1` |
| `AWS_ROLE_ARN` | `arn:aws:iam::594116288641:role/github-actions` |
| `ECR_REPOSITORY` | `594116288641.dkr.ecr.us-east-1.amazonaws.com/financial-control` |

**Nenhum secret.** O OIDC dispensa access key.

### Antes do primeiro uso

Existe um **bootstrap manual e único** que cria o que o próprio CI precisa para funcionar: bucket do
state, OIDC provider, role do CI e repositório ECR.

Ele roda pelo próprio Actions, em *Terraform — Bootstrap (uma vez)*, com `mode: plan` primeiro e
`mode: apply` depois de você ler o diff.

Depois dele, uma vez por ambiente, *Banco — Criar usuario da aplicacao* cria o usuário
`financial_app` no RDS. O Terraform não consegue: o banco fica em subnet privada, inalcançável de
onde ele roda.

Passo a passo completo, com verificações e reversão:
`aidlc-docs/construction/u5-infraestrutura/infrastructure-design/deployment-architecture.md` §6.

### Acesso à instância

```bash
aws ssm start-session --target <instance-id>
```

Sem SSH — a porta 22 fica fechada.

### Banco de dados

**RDS PostgreSQL 16** (`db.t4g.micro`), em subnet privada, sem acesso público. Backup automático
com retenção configurável, point-in-time recovery e patching gerenciado. Em `dev` a retenção é de
**1 dia** — a conta está no plano Free Tier, que recusa mais que isso. TLS obrigatório
(`rds.force_ssl = 1`).

Acesso alcançável apenas de dentro da VPC. Para inspecionar dados da sua máquina, use túnel SSM
pela EC2.

## AI-DLC

A metodologia está instalada na raiz do projeto:

```
CLAUDE.md               # workflow core — carregado automaticamente pelo Claude Code
.aidlc-rule-details/    # regras detalhadas, referenciadas sob demanda
├── inception/          # requisitos, user stories, unidades de trabalho, design
├── construction/       # design funcional, NFRs, geração de código, build e testes
├── operations/         # infraestrutura e operação
├── extensions/         # security, testing, resiliency
└── common/             # formato de perguntas, níveis de profundidade, continuidade de sessão
```

Versão das regras: **1.0.1** ([awslabs/aidlc-workflows](https://github.com/awslabs/aidlc-workflows)).

### Como usar

Abra o Claude Code na raiz do projeto e descreva a intenção de negócio em vez de tarefas. Por
exemplo: *"quero controlar despesas recorrentes e parceladas por categoria"*. O agente conduz a
fase de Inception, faz perguntas de esclarecimento e só executa depois da validação humana.

O trabalho roda em **bolts** — ciclos de horas ou dias, não semanas. Os artefatos gerados em cada
fase ficam em `aidlc-docs/`, e vale versionar essa pasta: ela é o contexto acumulado que alimenta
as fases seguintes.

## Licença

As regras AI-DLC em `CLAUDE.md` e `.aidlc-rule-details/` são de autoria da AWS, distribuídas sob
a licença do projeto original.
