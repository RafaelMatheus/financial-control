# Resumo da Code Generation — U1 Fundação

28 passos executados. Todo o código na raiz do workspace; nada em `aidlc-docs/`.

---

## 1. O que foi criado

### `common` — herdado por U2, U3 e U4

| Arquivo | Papel |
|---|---|
| `dominio/Dinheiro.kt` | Escala 2, HALF_UP, `dividirEm` em centavos inteiros |
| `dominio/Competencia.kt` | Ano-mês, com virada de ano correta |
| `dominio/Escopo.kt` | `PESSOAL` / `GRUPO` |
| `web/Erros.kt` | 8 códigos, `ErroDeNegocio`, `RespostaErro` |
| `web/ErroHandler.kt` | `@RestControllerAdvice` único |
| `web/FiltroCorrelacao.kt` | Id por requisição no MDC |
| `persistencia/RepositorioComVisibilidade.kt` | **A porta sem método cru** |
| `seguranca/ContextoUsuario.kt` | Fonte única; porta `ConsultaDeGrupos` |
| `seguranca/EmissorDeToken.kt` | JWT HMAC-SHA256, 24h |
| `seguranca/RegistroDeTentativas.kt` | Bloqueio 5 falhas / 15 min |
| `seguranca/ConfiguracaoSeguranca.kt` | Cadeia de filtros, BCrypt 12 |
| `ConfiguracaoComum.kt` | `Clock` injetável |

### `usuario` e `grupo`

Cada um com `dominio/` (puro, sem JPA), `aplicacao/` (serviço e transação) e
`adaptador/{web,persistencia}/`. 3 entidades, 8 endpoints.

### Persistência

`V1__fundacao.sql` — 3 tabelas, e o **índice único parcial**
`uk_membro_grupo_ativo ON membro_grupo (grupo_id, usuario_id) WHERE saiu_em IS NULL`,
que é o que permite o histórico de participações de D-45 e é a única coisa que barra duas
requisições simultâneas de adicionar membro.

### Testes

| Arquivo | Cobre |
|---|---|
| `DinheiroPropriedadesTest` | 14 propriedades + bordas 🔬 |
| `CompetenciaPropriedadesTest` | 10 propriedades 🔬 |
| `RegistroDeTentativasTest` | Bloqueio, expiração, isolamento por conta |
| `EmissorDeTokenTest` | Assinatura, expiração, adulteração |
| `IsolamentoDeDadosTest` | **H-03** — 8 cenários |
| `UsuarioIntegracaoTest` | H-01, H-02, H-04 — 11 cenários |
| `GrupoIntegracaoTest` | H-05 a H-08 — 9 cenários |

---

## 2. O defeito que o PBT encontrou

`dividirEm` estava errada, e o erro tinha atravessado a Functional Design, a revisão do design e a
escrita do código. A especificação dizia "resíduo na última parte", com o exemplo
`100,00 em 3 → 33,33 / 33,33 / 33,34`.

O exemplo está certo; a regra que ele ilustra, não. O resíduo vale até **n−1 centavos**, e o exemplo
tem resíduo de exatamente um — ilustrava a regra errada com o resultado certo.

Contraexemplo encontrado na primeira execução: `R$ 10.000.000.000,00 em 6`. No domínio:
**R$ 1,19 em 120 parcelas** daria 119 parcelas de zero e uma de R$ 1,19.

Registrado no research-log como 3.36 / O-28.

---

## 3. Decisões de implementação que valem registro

**A porta sem método cru.** `RepositorioComVisibilidade` não tem `buscarTodos` nem `buscarPorId`.
Quem escrever consulta sem filtro não produz bug — produz erro de compilação.

**404 e não 403 para não-membro.** Um 403 confirmaria que o grupo existe, permitindo descobrir
identificadores válidos por tentativa.

**`consultarPerfil()` não aceita parâmetro.** A regra fica na assinatura, não numa validação
esquecível.

**Tempo constante no login.** Quando o e-mail não existe, o serviço calcula um hash descartável.
Sem isso, a latência revelaria quais e-mails estão cadastrados.

**Bloqueio responde como senha errada.** Mesmo código, mesma mensagem. Dizer "conta bloqueada"
confirmaria a conta justamente para quem tenta adivinhar a senha dela.

**`Grupo` sem `criadorId`.** Guardar o criador criaria um campo que alguém usaria como autoridade,
reintroduzindo a hierarquia que RF-06 nega.

**Verificar para dar mensagem, restringir no banco para dar garantia.** Aplicado a e-mail duplicado
e a membro duplicado. Só a verificação é uma corrida; só a restrição dá mensagem ruim.

**Relógio injetado.** Não é purismo: sem ele, testar expiração de bloqueio exigiria dormir 15
minutos — teste lento e depois intermitente.

---

## 4. Achados corrigidos de passagem

| Achado | Correção |
|---|---|
| `gradlew` e `gradle-wrapper.jar` não existiam — débito da engenharia reversa que U5 contornou instalando Gradle no CI | Wrapper 8.14.2 gerado e versionado |
| `application-test.yml` com `ddl-auto: create-drop` — o schema dos testes viria do JPA, a migration nunca seria exercitada, e o índice único parcial não existiria nos testes | Trocado por `validate` + Flyway |

---

## 5. Infraestrutura decorrente

- `parameters.tf` passou de 5 para **6** parâmetros: `/{nome}/auth/jwt-secret`, `SecureString`, com
  `ignore_changes` para que um apply futuro não deslogue todo mundo
- `user-data.sh`, `write-env.sh` e `docker-compose.prod.yml` passam `JWT_SECRET` ao container
- Sem default no `application.yml`: a aplicação **não sobe** sem o segredo, que é o comportamento
  correto

---

## 6. Limitações desta execução

**Os testes de integração não rodaram localmente**: não há Docker nesta máquina, e Testcontainers
precisa dele. Compilam, e rodam no CI — o `ci-app.yml` executa em runner com Docker.

Rodaram localmente e passaram: os **24 testes de propriedade** e os **13 de unidade** de
`common/seguranca`.

---

## 7. O que U1 não faz

Nenhum endpoint de U2, U3 ou U4. Nenhuma troca de e-mail ou de senha — não há requisito. Nenhum
front-end. Recuperação de grupo abandonado continua sendo requisito novo, se o produto pedir.
