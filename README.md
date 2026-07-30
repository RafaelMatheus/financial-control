# financial-control

Aplicação Spring Boot + Kotlin para controle financeiro, construída com a metodologia
[AI-DLC](https://github.com/awslabs/aidlc-workflows) (AI-Driven Development Life Cycle) da AWS.

O repositório nasce **sem domínio de negócio por decisão de projeto**: o modelo de domínio será
gerado pelo próprio fluxo AI-DLC, a partir da fase de Inception. O que existe aqui é o esqueleto
executável e as regras do método.

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

Gere o Gradle wrapper na primeira vez (o `.jar` do wrapper não é versionado neste commit inicial):

```bash
gradle wrapper --gradle-version 8.14.2
```

> O CI e o build da imagem **não dependem do wrapper** — instalam o Gradle 8.14.2 explicitamente.
> O wrapper é conveniência para desenvolvimento local. Versioná-lo (`gradle/wrapper/gradle-wrapper.jar`
> e `gradlew`) simplificaria ambos e é recomendado.

Suba o banco e a aplicação:

```bash
docker compose up -d
./gradlew bootRun
```

A aplicação sobe em `http://localhost:8080`. Health check em `/actuator/health`.

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

## Infraestrutura e deploy

A aplicação roda numa instância **AWS EC2** (`t3.small`, `us-east-1`), com PostgreSQL em container
na mesma máquina e volume EBS separado para os dados. Toda a infraestrutura é descrita em
**Terraform**, em `infra/terraform/`, e provisionada pelo **GitHub Actions**.

### Pipeline

| Workflow | Gatilho | O que faz |
|---|---|---|
| `ci-app.yml` | PR em `src/**` | Build Gradle e testes com Testcontainers |
| `terraform-plan.yml` | PR em `infra/**` | `plan` e publica o diff como comentário no PR |
| `terraform-apply.yml` | merge em `main`, `infra/**` | `apply` automático |
| `deploy-app.yml` | merge em `main`, `src/**` | Build da imagem, push para ECR e deploy via SSM |

Autenticação na AWS por **OIDC** — nenhuma credencial de longa duração no repositório.

### Antes do primeiro uso

Existe um **bootstrap manual e único** que cria o que o próprio CI precisa para funcionar: bucket do
state, OIDC provider, role do CI e repositório ECR.

```bash
cd infra/terraform/bootstrap
terraform init && terraform apply
```

Passo a passo completo, com verificações e reversão:
`aidlc-docs/construction/u5-infraestrutura/infrastructure-design/deployment-architecture.md` §6.

### Acesso à instância

```bash
aws ssm start-session --target <instance-id>
```

Sem SSH — a porta 22 fica fechada.

### ⚠️ Sem backup

Não há rotina de backup do PostgreSQL (decisão registrada em D-36). A única proteção é o volume EBS
separado da instância, que sobrevive à recriação da EC2 — **mas não a perda ou corrupção do próprio
volume**. Antes de guardar dados que você não queira perder, implemente RF-54.

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
