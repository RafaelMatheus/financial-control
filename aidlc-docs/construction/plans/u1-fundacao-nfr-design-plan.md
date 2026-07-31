# Plano de NFR Design — U1 Fundação

Traduz os 14 NFRs de `nfr-requirements.md` em padrões e componentes.

---

## 1. Passos

- [x] 1.1 Definir a arquitetura interna do pacote de feature
- [x] 1.2 Desenhar a imposição estrutural da `Visibilidade`
- [x] 1.3 Desenhar a cadeia de autenticação e o filtro JWT
- [x] 1.4 Desenhar o bloqueio por força bruta
- [x] 1.5 Desenhar o tratamento de erro e o id de correlação
- [x] 1.6 Avaliar as 5 categorias obrigatórias de padrão
- [x] 1.7 Gerar `nfr-design-patterns.md`
- [x] 1.8 Gerar `logical-components.md`

---

## 2. Questões

> Coletadas via `AskUserQuestion`. Transcritas aqui para o rastro documental.

### Q1 — Imposição técnica da `Visibilidade`

O design promete que esquecer o filtro é impossível por construção. A promessa se cumpre ou se perde
no como.

**[Answer]**: **Repositório base sem método cru.** Os repositórios de domínio não expõem método que
retorne dados sem o predicado. Quem escrever `findAll()` não compila, porque o método não existe.

### Q2 — Camadas dentro do pacote de feature

**[Answer]**: **Hexagonal com portas e adaptadores.** Domínio puro no centro, sem anotação de JPA.

> Escolhido contra a recomendação, que era três camadas. A ressalva permanece registrada: num CRUD
> de 15 entidades, parte do mapeamento entre domínio e persistência é cerimônia. Em compensação,
> combina bem com Q1 — a **porta** de repositório vira o lugar exato onde mora a garantia de que não
> existe consulta sem filtro, e o domínio sequer conhece a forma de consultar.

### Q3 — Formato de log

**[Answer]**: **Texto simples com id de correlação** via MDC. Sem coletor de log, JSON só atrapalha
a leitura no `docker logs`.

---

## 3. Categorias obrigatórias — avaliação

| Categoria | Aplicável? | Justificativa |
|---|---|---|
| **Resilience** | Parcial | Sem integração externa em U1. Aplica-se apenas à indisponibilidade do banco — ver §3 de `nfr-design-patterns.md` |
| **Scalability** | **Não** | RNF-12 exclui escala. Registrado o que quebraria se um dia houver segunda instância |
| **Performance** | Parcial | Alvo p95 < 500 ms. O único ponto quente conhecido é o BCrypt, que é lento de propósito |
| **Security** | **Sim** | É o eixo da unidade: RNF-05, NFR-U1-01 a 05 |
| **Logical Components** | Parcial | Nenhuma fila, cache ou circuit breaker se justifica. O que existe é in-process — ver `logical-components.md` |

> Marcar categorias como não-aplicáveis **com justificativa** é o objetivo do passo, não uma forma
> de pulá-lo. Introduzir cache ou circuit breaker aqui seria desenhar contra RNF-12.

---

## 4. Decisões fechadas

| ID | Decisão |
|---|---|
| D-51 | Arquitetura hexagonal com portas e adaptadores |
| D-52 | `Visibilidade` imposta por porta de repositório sem método cru |
| D-53 | Log em texto simples com id de correlação via MDC |

---

## 5. Artefatos gerados

`aidlc-docs/construction/u1-fundacao/nfr-design/`

- `nfr-design-patterns.md`
- `logical-components.md`
