# Decisões de Stack — U1 Fundação

O que entra no `build.gradle.kts` nesta unidade, e por quê. A base — Kotlin 2.1.21, JVM 21, Spring
Boot 3.5.4, Gradle 8.14.2 — já existia e não é revisitada aqui.

---

## 1. O que passa a entrar

| Dependência | Para quê | Decisão |
|---|---|---|
| `spring-boot-starter-security` | Filtro de autenticação, `PasswordEncoder`, contexto de segurança | D-02, D-48 |
| `io.jsonwebtoken:jjwt-api` / `-impl` / `-jackson` | Emitir e validar o JWT | D-02 |
| `org.flywaydb:flyway-core` | Migrations (RNF-04) | D-01 |
| `org.flywaydb:flyway-database-postgresql` | Suporte a PostgreSQL 16 no Flyway 10+ | D-01 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Gerar a especificação a partir do código | D-06 |
| `io.kotest:kotest-property` (teste) | Property-based testing | D-05 |
| `io.kotest:kotest-runner-junit5` (teste) | Executar as propriedades no JUnit 5 já configurado | D-05 |
| `org.springframework.security:spring-security-test` (teste) | Autenticar requisições em teste de integração | — |

> `flyway-database-postgresql` é fácil de esquecer: a partir do Flyway 10 o suporte a cada banco
> saiu do core para artefato próprio, e sem ele a aplicação sobe e falha na primeira migration com
> "Unsupported Database". Não é opcional.

## 2. O que **não** entra, e por quê

| Descartado | Motivo |
|---|---|
| Argon2 / Bouncy Castle | BCrypt basta no perfil de RNF-12, e Argon2 exige calibrar memória numa `t3.small` que já hospeda o Spring |
| Refresh token | D-50 escolheu 24 h sem refresh; refresh traria de volta o estado no servidor que o JWT stateless evita |
| Redis, ou qualquer cache | Nada em U1 pede cache. O contador de bloqueio cabe em memória com instância única |
| OAuth2 / OIDC externo | Contradiz D-42 (senhaHash próprio) e adiciona serviço a configurar e pagar |
| Biblioteca de rate limiting | O bloqueio de NFR-U1-03 é por conta, com contador simples — biblioteca seria desproporcional |
| jqwik | Kotest integra melhor com Kotlin e com o restante do ferramental (PBT-09) |

> A coluna da direita importa tanto quanto a da esquerda. Sem ela, cada uma dessas escolhas vira
> pergunta de novo daqui a três meses, e a resposta será reconstruída do zero — provavelmente
> diferente.

---

## 3. Configuração decorrente

### 3.1 `application.yml`

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false   # banco novo; ligar isto esconderia migration faltando

app:
  auth:
    # Vem do Parameter Store, como as credenciais do banco. Sem default no arquivo:
    # um default aqui viraria o segredo de producao no dia em que a variavel faltasse.
    jwt-secret: ${JWT_SECRET}
    jwt-validade-horas: 24
    bcrypt-forca: 12
    max-tentativas: 5
    bloqueio-minutos: 15

springdoc:
  swagger-ui:
    # Desabilitado por padrao: a especificacao completa e um mapa da superficie
    # de ataque. Habilitar por perfil, em dev.
    enabled: false
```

### 3.2 Novo parâmetro no Parameter Store

`/{project_name}/auth/jwt-secret`, tipo `SecureString`, gerado pelo Terraform como as senhas do
banco já são. Implica:

- `parameters.tf` passa de 5 para **6** parâmetros
- `user-data.sh` e `write-env.sh` passam a exportar `JWT_SECRET`
- a policy do instance profile já cobre o prefixo inteiro — sem mudança de permissão

> **Atenção operacional**: trocar esse segredo invalida todos os tokens em circulação, o que é o
> comportamento correto e a única forma de revogação disponível, dado que D-50 dispensou refresh e
> lista de bloqueio. Vale como procedimento de emergência: se um token vazar, gire o segredo.

### 3.3 Rota pública

O filtro de segurança precisa liberar sem autenticação: `POST /usuarios` (cadastro),
`POST /auth/login`, e `/health` mais `/actuator/health` — este último porque o healthcheck do
container e o do nginx batem nele sem credencial, e exigir autenticação ali derrubaria o deploy.

Todo o resto exige token.

---

## 4. Versões

Deixadas a cargo do `io.spring.dependency-management`, que já governa o projeto, exceto onde ele não
alcança:

| Artefato | Versão | Nota |
|---|---|---|
| `jjwt` | `0.12.5` | Fora do BOM do Spring Boot |
| `kotest` | `5.9.1` | Fora do BOM |
| `springdoc` | `2.8.6` | Compatível com Spring Boot 3.5 |
| Flyway, Spring Security | do BOM | Alinhadas ao Spring Boot 3.5.4 |

> Versão menor fixada é dívida com prazo — foi o que derrubou o `apply` do RDS em
> `engine_version = "16.6"` (research-log 3.35). Aqui elas são inevitáveis, porque estão fora do
> BOM; ficam explícitas em vez de espalhadas, para que a atualização seja um lugar só.
