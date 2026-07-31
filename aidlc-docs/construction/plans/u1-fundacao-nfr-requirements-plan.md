# Plano de NFR Requirements — U1 Fundação

**Unidade**: U1 — Fundação
**RNF herdados**: RNF-01 (integridade monetária), RNF-05 (isolamento), RNF-09 (erros),
RNF-10 (validação), e por consequência RNF-04 (Flyway), RNF-06 (Testcontainers), RNF-07 (PBT)
**Decisões que fecham aqui**: D-02, D-05, D-06

> RNF-12 fixa a escala: uso pessoal e doméstico, dezenas de usuários, milhares de lançamentos.
> **Não há requisito de alta escala, alta disponibilidade nem latência agressiva.** Isso deve
> podar boa parte do espaço de decisão desta stage, em vez de convidar a superdimensionar.

---

## 1. Passos

- [x] 1.1 Fechar D-02 — mecanismo de autenticação e sessão
- [x] 1.2 Escolher o algoritmo de hash de senha
- [x] 1.3 Definir proteção contra força bruta no login
- [x] 1.4 Fechar D-06 — origem da especificação OpenAPI
- [x] 1.5 Confirmar D-05 — framework de property-based testing
- [x] 1.6 Quantificar desempenho e disponibilidade coerentes com RNF-12
- [x] 1.7 Definir observabilidade mínima — o que logar e o que nunca logar
- [x] 1.8 Definir a estratégia de teste da unidade
- [x] 1.9 Consolidar a stack e as versões
- [x] 1.10 Gerar `nfr-requirements.md`
- [x] 1.11 Gerar `tech-stack-decisions.md`

---

## 2. Questões

> Coletadas via `AskUserQuestion`, conforme a preferência registrada do usuário. As respostas são
> transcritas aqui para preservar o rastro documental exigido pelo método.

### Q1 — D-02: mecanismo de sessão

D-42 já fixou autenticação própria, com `senhaHash` na entidade. Falta como a sessão se sustenta
entre requisições. O front-end é uma aplicação web **em outro repositório**, consumindo esta API.

**[Answer]**: **JWT stateless**, no header `Authorization`. Sem estado de sessão no servidor. Consequência aceita: logout é do lado do cliente até o token expirar.

### Q2 — Algoritmo de hash de senha

RN-U03 exige que a credencial nunca repouse em texto claro, sem dizer com o quê.

**[Answer]**: **BCrypt**, via `PasswordEncoder` do Spring Security. Sem dependência adicional.

### Q3 — Proteção contra força bruta no login

RN-U04 já iguala o tempo de resposta para não vazar quais e-mails existem. Isso não impede tentar
milhares de senhas contra uma conta conhecida.

**[Answer]**: **Bloqueio temporário por conta** após N falhas seguidas. Contador em memória — basta com uma instância.

### Q4 — D-06: origem da especificação OpenAPI

Já existe `openapi.yaml` escrito à mão na Application Design, com 31 caminhos e 51 operações. A
questão é quem passa a mandar quando código e contrato divergirem.

**[Answer]**: **springdoc-openapi gera a partir do código.** O `openapi.yaml` escrito à mão vira referência de design; o gerado vira o contrato publicado.

### Q5 — D-05: framework de property-based testing

Pré-decidido como Kotest Property Testing pela regra PBT-09. Confirmação.

**[Answer]**: **Kotest Property Testing**, confirmando a recomendação PBT-09.

### Q6 — Validade do token

**[Answer]**: **24 horas, sem refresh token.** Elimina emissão, rotação e armazenamento de refresh —
e com eles o estado no servidor que o JWT stateless existia para evitar.

---

## 3. Decisões que esta stage fecha

| ID | Questão |
|---|---|
| D-02 | Mecanismo de sessão |
| D-48 | Algoritmo de hash de senha |
| D-49 | Proteção contra força bruta |
| D-06 | Origem da especificação OpenAPI |
| D-05 | Framework de property-based testing |
| D-50 | Validade do token JWT |

---

## 4. Artefatos gerados

`aidlc-docs/construction/u1-fundacao/nfr-requirements/`

- `nfr-requirements.md`
- `tech-stack-decisions.md`
