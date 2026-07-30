# API Documentation

> **Resumo**: **Nenhuma API de negócio existe.** Não há classes anotadas com `@RestController`,
> `@Controller`, `@RequestMapping` ou equivalentes no código-fonte. Os únicos endpoints HTTP
> disponíveis são os fornecidos automaticamente pelo Spring Boot Actuator.

## REST APIs

### Endpoints de negócio
Nenhum. Zero controllers no código-fonte.

### Endpoints de infraestrutura (Spring Boot Actuator)

Expostos por configuração em `application.yml`
(`management.endpoints.web.exposure.include: health,info`). Não são definidos por código do
projeto — vêm de `spring-boot-starter-actuator`.

#### Health
- **Method**: `GET`
- **Path**: `/actuator/health`
- **Purpose**: Verificar a saúde da aplicação. Inclui o `DataSourceHealthIndicator`, que valida a
  conectividade com o PostgreSQL.
- **Request**: Sem parâmetros, sem corpo.
- **Response**: `200 OK` com `{"status":"UP"}` (ou `503` com `{"status":"DOWN"}`).
  Detalhes por componente estão configurados como `show-details: when-authorized`; como o projeto
  **não tem Spring Security no classpath**, não existe usuário autenticado e os detalhes nunca são
  exibidos — a resposta é sempre o status agregado.

#### Info
- **Method**: `GET`
- **Path**: `/actuator/info`
- **Purpose**: Expor metadados da aplicação.
- **Request**: Sem parâmetros, sem corpo.
- **Response**: `200 OK` com `{}` — nenhum contribuidor de `info` está configurado (sem
  `management.info.env.enabled`, sem plugin de build-info do Gradle habilitado).

**Observação de segurança**: ambos os endpoints são **públicos e não autenticados**, já que não há
Spring Security. Deve ser tratado na stage de NFR Requirements caso o Security Baseline seja
habilitado.

## Internal APIs

### `FinancialControlApplication`
- **Methods**:
  - `fun main(args: Array<String>): Unit` (função de nível de pacote, não método da classe)
- **Parameters**: `args` — argumentos de linha de comando repassados ao Spring Boot.
- **Return Types**: `Unit`.
- **Notes**: A classe em si é vazia; serve apenas como âncora para `@SpringBootApplication`
  (component scan + auto-configuração a partir de `com.rafaelmatheus.financialcontrol`).

### `TestcontainersConfiguration` (escopo de teste)
- **Methods**:
  - `fun postgresContainer(): PostgreSQLContainer<*>` — anotado com `@Bean` e `@ServiceConnection`.
- **Parameters**: Nenhum.
- **Return Types**: `PostgreSQLContainer<*>`, instanciado a partir de
  `DockerImageName.parse("postgres:16-alpine")`.
- **Notes**: `@ServiceConnection` faz o Spring Boot derivar automaticamente `spring.datasource.url`,
  `username` e `password` do container em execução.

### Interfaces / abstrações de negócio
Nenhuma. Não há interfaces de serviço, repositórios Spring Data, portas, use cases ou clientes.

## Data Models

**Nenhum modelo de dados existe.**

- **Entidades JPA**: nenhuma classe anotada com `@Entity`, `@MappedSuperclass` ou `@Embeddable`.
- **DTOs / requests / responses**: nenhum.
- **Enums de domínio**: nenhum.
- **Schema de banco**: o database `financial_control` é criado vazio pelo `docker-compose.yml`.
  Não há migrations (Flyway/Liquibase ausentes do classpath) nem scripts `schema.sql` / `data.sql`.
- **Regras de validação**: `spring-boot-starter-validation` está no classpath, mas **nenhuma
  anotação de validação** (`@NotNull`, `@Positive`, `@Valid` etc.) é usada — não há alvo para
  validar.

O modelo de dados para gastos, categorias, cartões de crédito e parcelas será definido a partir da
Requirements Analysis e detalhado na Functional Design.

## Contrato de API publicado

Não existe. Sem OpenAPI/Swagger (springdoc não está no classpath), sem especificação Smithy, sem
arquivos `.proto`, sem coleção Postman versionada.
