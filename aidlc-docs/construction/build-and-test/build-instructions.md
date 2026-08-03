# Instruções de Build

Projeto de **módulo único**: as cinco unidades AI-DLC convivem no mesmo artefato Gradle, separadas
por pacote de feature (D-03, D-51). Não há build por unidade — há um build só.

---

## 1. Pré-requisitos

| Item | Versão | Observação |
|---|---|---|
| **JDK** | 21 (Temurin) | `java.toolchain` no `build.gradle.kts` |
| **Gradle** | 8.14.2 | ⚠️ **O `gradle-wrapper.jar` não é versionado** — ver §1.1 |
| **Docker** | qualquer recente | **Só para os testes de integração** (Testcontainers) |
| **PostgreSQL** | 16 | Local para rodar a aplicação; nos testes o Testcontainers sobe |

### 1.1 O wrapper não está no repositório

A engenharia reversa encontrou que `gradlew` e `gradle-wrapper.jar` **não existiam** no repositório —
débito que quebraria o CI e o build da imagem. A correção de U5 foi **remover a dependência do
wrapper**, e não versioná-lo:

- o `Dockerfile` usa a imagem oficial do Gradle;
- o `ci-app.yml` instala o Gradle 8.14.2 explicitamente.

**Consequência para quem builda localmente**: use `gradle`, não `./gradlew`. Se o wrapper existir na
sua cópia, ele funciona — mas não conte com ele.

---

## 2. Passos

### 2.1 Compilar

```bash
gradle build --no-daemon
```

Isto **compila e roda a suíte inteira**, inclusive os testes de integração. Sem Docker, eles falham
na inicialização do Testcontainers.

### 2.2 Compilar sem rodar testes

```bash
gradle assemble --no-daemon
```

Use quando não houver Docker na máquina. É o que a máquina de desenvolvimento deste projeto fez
durante todo o ciclo — e a razão de o CI ser a única verificação real dos testes de integração.

### 2.3 Rodar a aplicação localmente

```bash
export JWT_SECRET='...'                    # obrigatorio; sem ele a aplicacao nao sobe
export SPRING_PROFILES_ACTIVE=dev
gradle bootRun --no-daemon
```

---

## 3. O que a build produz

| Artefato | Local |
|---|---|
| JAR executável | `build/libs/financial-control-0.0.1-SNAPSHOT.jar` |
| Relatórios de teste | `build/reports/tests/test/index.html` |
| Resultados XML | `build/test-results/test/` |

---

## 4. Variáveis de ambiente

| Variável | Obrigatória | Origem em produção |
|---|---|---|
| `JWT_SECRET` | **Sim** | Parameter Store: `/{projeto}/auth/jwt-secret` (SecureString) |
| `SPRING_DATASOURCE_URL` | Sim (fora de teste) | `write-env.sh`, a partir do output do Terraform |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Sim | Parameter Store |
| `SPRING_PROFILES_ACTIVE` | Não | `dev` ou vazio |

> **Sem `JWT_SECRET` a aplicação não sobe, e isso é o comportamento correto.** Um segredo com valor
> padrão seria pior que a ausência dele: subiria assinando tokens com um valor que qualquer um
> conhece.

---

## 5. Problemas conhecidos

### `Unsupported Database: PostgreSQL`

**Causa**: `flyway-database-postgresql` ausente do classpath. O suporte a PostgreSQL saiu do core do
Flyway na versão 10.
**Solução**: já está no `build.gradle.kts` como `runtimeOnly`. Se sumir, a aplicação sobe e falha na
primeira migration.

### `Schema-validation: missing table [...]`

**Causa**: `ddl-auto: validate` encontrou divergência entre as `@Entity` e o schema criado pelas
migrations.
**Solução**: **ajustar a migration** — nunca desligar o `validate`. Ele é o mecanismo que detecta a
divergência, e desligá-lo troca um erro na inicialização por um erro em produção.

### Testes de integração falham com `Could not find a valid Docker environment`

**Causa**: Docker ausente ou parado.
**Solução**: subir o Docker, ou usar `gradle assemble` e deixar os testes para o CI.

### `DuplicateKeyException: while constructing a mapping`

**Causa**: chave repetida em algum `.yml`. Aconteceu de verdade em U3, com dois blocos `app:` no
`application-test.yml`.
**Sintoma enganoso**: **todos** os testes de integração falham de uma vez, e nenhuma mensagem
menciona YAML. A causa aparece dezenas de linhas abaixo, numa `Caused by` idêntica em todos.
