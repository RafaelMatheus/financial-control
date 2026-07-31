# Requisitos Não-Funcionais — U1 Fundação

Herda os RNF do `requirements.md` e os torna verificáveis para esta unidade. Onde um RNF global não
tem consequência em U1, isso está dito — vale mais que o silêncio.

> **A restrição que governa esta stage é RNF-12**: uso pessoal e doméstico, dezenas de usuários,
> milhares de lançamentos, sem requisito de alta disponibilidade nem latência agressiva. Boa parte
> das decisões abaixo é "a opção simples", e isso é consequência do requisito, não descuido.

---

## 1. Segurança

### NFR-U1-01 — Credencial em BCrypt (D-48)
Senha armazenada como hash BCrypt, via `PasswordEncoder` do Spring Security. Fator de custo
**12** — cerca de 250 ms por verificação numa `t3.small`, o que encarece ataque offline sem
inviabilizar o login.

**Verificação**: nenhuma coluna do schema guarda senha reversível; o teste de cadastro confirma que
`senhaHash` não é igual à senha enviada.

### NFR-U1-02 — Sessão por JWT stateless (D-02)
Token assinado (HMAC-SHA256), enviado em `Authorization: Bearer`. O servidor **não** guarda estado
de sessão.

**Validade: 24 horas, sem refresh token** (D-50).

**Consequência aceita e registrada**: não há revogação antes do vencimento. Logout apaga o token no
cliente; um token vazado vale até 24 h. A alternativa — lista de bloqueio no servidor — reintroduz
exatamente o estado que o JWT stateless existe para evitar, e não se justifica nesta escala.

**Segredo de assinatura**: vem do Parameter Store, como as credenciais do banco. Nunca no
repositório, nunca no `application.yml`.

### NFR-U1-03 — Bloqueio temporário por conta (D-49)
Após **5** falhas consecutivas, a conta recusa tentativas por **15 minutos**. O contador zera no
login bem-sucedido.

Contador **em memória** — é suficiente com instância única, e é a escolha coerente com RNF-12.
Fica registrado que uma segunda instância tornaria a contagem parcial: cada uma contaria as suas.

**Interação com RN-U04**: a resposta do bloqueio precisa ser indistinguível da resposta de senha
errada. Dizer "conta bloqueada" confirmaria que a conta existe — desfazendo, no caminho de erro, a
proteção que RN-U04 monta no caminho normal.

### NFR-U1-04 — Isolamento de dados (RNF-05)
Toda consulta passa pelo predicado de `Visibilidade`. Não existe repositório que retorne dados sem
ele.

**Verificação**: teste de integração com dois usuários e um grupo, cobrindo os três casos de H-03 —
pessoal alheio, grupo alheio e grupo compartilhado.

### NFR-U1-05 — O que nunca é registrado em log
Senha em qualquer forma, token JWT, hash de senha, e o corpo de requisições de autenticação.

O nível de log de `com.rafaelmatheus.financialcontrol` é `INFO`, e nenhum log de nível `DEBUG` de
Spring Security ou Hibernate é habilitado em produção — ambos registram parâmetros de consulta.

---

## 2. Integridade e correção

### NFR-U1-06 — Aritmética monetária exata (RNF-01)
`BigDecimal` com escala 2 e `HALF_UP` (D-43). `Double` e `Float` **proibidos** em qualquer caminho
que toque valor monetário.

**Verificação**: property-based testing sobre `Dinheiro` — ver NFR-U1-11.

### NFR-U1-07 — Schema versionado (RNF-04)
Flyway, com `ddl-auto: validate` mantido. A primeira migration nasce nesta unidade e resolve o
débito bloqueante apontado na engenharia reversa.

**Ponto de atenção**: o índice único parcial de `membro_grupo` não é expressável em JPA, então
`validate` não o verifica. Ele existe apenas na migration, e a invariante que ele protege
(RN-G05) precisa de teste de integração próprio, com duas inserções concorrentes.

### NFR-U1-08 — Validação de entrada (RNF-10)
`spring-boot-starter-validation`, hoje no classpath sem uso, passa a ser usado em todo DTO de
entrada. Violação vira `400` no formato de NFR-U1-09.

### NFR-U1-09 — Formato consistente de erro (RNF-09)
`@RestControllerAdvice` único, com `codigo`, `mensagem` e `detalhes`. A taxonomia de 8 códigos está
em `business-rules.md` §4.

**Mensagem acionável**: diz o que fazer, não o que quebrou internamente. Nenhuma mensagem expõe
nome de tabela, de constraint ou de classe.

---

## 3. Desempenho e disponibilidade

Dimensionados por RNF-12, e deliberadamente modestos.

| Requisito | Alvo | Observação |
|---|---|---|
| NFR-U1-10 — Latência | p95 abaixo de **500 ms** nas operações de U1 | Exceto login, que carrega os ~250 ms do BCrypt por construção |
| Disponibilidade | **Sem SLA.** Instância única, sem réplica | Reinício da instância derruba o serviço; aceito em RNF-12 |
| Capacidade | Pool Hikari de **10** conexões | Já configurado; folgado para dezenas de usuários |

**Não há requisito de escala horizontal.** Duas decisões desta stage — contador de bloqueio em
memória e ausência de estado de sessão — se apoiam nisso de formas opostas: a primeira quebra com
mais de uma instância, a segunda facilita. Se um dia houver segunda instância, revisitar NFR-U1-03,
não NFR-U1-02.

---

## 4. Testabilidade

### NFR-U1-11 — Property-based testing (RNF-07, D-05)
**Kotest Property Testing**, modo Parcial: PBT-02, PBT-03, PBT-07, PBT-08 e PBT-09 bloqueantes.

Alvos e propriedades estão em `business-rules.md` §6. O gerador de `Dinheiro` (PBT-07) precisa
cobrir zero, negativos, valores com resíduo de divisão e magnitudes que denunciariam ponto flutuante.

Seed registrado no CI para reprodução (PBT-08) — `ci-app.yml` já faz.

### NFR-U1-12 — Teste de integração contra PostgreSQL real (RNF-06)
Testcontainers, padrão já estabelecido no repositório. Vale especialmente para o índice único
parcial e para as migrations Flyway, que só se verificam contra o banco de verdade.

### NFR-U1-13 — Cobertura mínima da unidade
Sem meta numérica. O critério é por comportamento: cada uma das 21 regras de negócio de
`business-rules.md` tem ao menos um teste que falha se a regra for removida.

> Meta percentual de cobertura mede linhas executadas, não comportamento verificado. Numa unidade
> cuja razão de existir é o isolamento de dados, "80% de cobertura" pode conviver com o predicado de
> visibilidade nunca testado.

---

## 5. Contrato de API

### NFR-U1-14 — OpenAPI gerado do código (D-06, RNF-08)
**springdoc-openapi** gera a especificação a partir dos controllers. O gerado é o contrato
publicado; o `openapi.yaml` da Application Design passa a ser **referência de design**, não fonte de
verdade.

**Consequência registrada**: nada garante que o gerado continue coerente com o desenhado. A
divergência deixa de ser detectável automaticamente. Se isso incomodar, a saída é um teste de
contrato comparando os dois — foi oferecido e não escolhido, e fica anotado como opção futura.

Swagger UI vem junto. Deve ficar **desabilitado em produção** ou atrás de autenticação: a
especificação completa é um mapa da superfície de ataque.

---

## 6. Rastreabilidade

| NFR desta unidade | Origem |
|---|---|
| NFR-U1-01, 02, 03 | D-48, D-02, D-50, D-49 — decididos nesta stage |
| NFR-U1-04 | RNF-05, RF-03, RF-04 |
| NFR-U1-05 | Decorrência de RN-U03 |
| NFR-U1-06 | RNF-01, D-43 |
| NFR-U1-07 | RNF-04, D-01 |
| NFR-U1-08 | RNF-10 |
| NFR-U1-09 | RNF-09 |
| NFR-U1-10 | RNF-12 |
| NFR-U1-11, 12, 13 | RNF-06, RNF-07, D-05 |
| NFR-U1-14 | RNF-08, D-06 |
