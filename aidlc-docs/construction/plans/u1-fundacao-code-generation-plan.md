# Plano de Code Generation — U1 Fundação

> **Este plano é a fonte única de verdade da Code Generation de U1.** Cada passo tem checkbox e é
> marcado no mesmo momento em que o trabalho é concluído.

---

## 1. Contexto da unidade

| | |
|---|---|
| **Componentes** | `common`, `usuario`, `grupo` |
| **Entidades** | `Usuario`, `Grupo`, `MembroGrupo` |
| **Histórias** | H-01 a H-08 (8) |
| **Requisitos** | RF-01 a RF-10, RNF-01, RNF-04, RNF-05, RNF-09, RNF-10 |
| **Regras de negócio** | 21 (RN-U01 a U07, RN-G01 a G09, RN-V01 a V04) |
| **Depende de** | — (primeira unidade do caminho crítico) |
| **Bloqueia** | U2, U3, U4 |

**Estado do repositório**: brownfield com esqueleto executável. `src/` tem apenas
`FinancialControlApplication.kt`, um teste de contexto e a configuração de Testcontainers. **Nenhuma
`@Entity` existe** — é a primeira unidade a criar domínio.

**Interfaces que U2, U3 e U4 vão consumir**: `Dinheiro`, `Competencia`, `Escopo`,
`ContextoUsuario`, `RepositorioComVisibilidade`. São contrato entre unidades, não detalhe interno.

---

## 2. Onde o código vai

**Raiz do workspace**, nunca em `aidlc-docs/`. Arquitetura hexagonal por feature (D-51, D-03):

```
src/main/kotlin/com/rafaelmatheus/financialcontrol/
├── common/
│   ├── dominio/        Dinheiro · Competencia · Escopo
│   ├── seguranca/      ContextoUsuario · FiltroJwt · EmissorDeToken ·
│   │                   RegistroDeTentativas · ConfiguracaoSeguranca · CodificadorDeSenha
│   ├── persistencia/   RepositorioComVisibilidade (porta) · SuporteVisibilidade (adaptador)
│   └── web/            ErroHandler · FiltroCorrelacao · RespostaErro · CodigoErro
├── usuario/
│   ├── dominio/        Usuario · UsuarioRepositorio
│   ├── aplicacao/      UsuarioService · AutenticacaoService · comandos
│   └── adaptador/
│       ├── web/        UsuarioController · AutenticacaoController · DTOs
│       └── persistencia/ UsuarioJpa · UsuarioSpringData · UsuarioRepositorioAdaptador
└── grupo/
    ├── dominio/        Grupo · MembroGrupo · GrupoRepositorio
    ├── aplicacao/      GrupoService
    └── adaptador/
        ├── web/        GrupoController · DTOs
        └── persistencia/ GrupoJpa · MembroGrupoJpa · SpringData · Adaptador

src/main/resources/db/migration/V1__fundacao.sql
src/test/kotlin/...  espelhando a estrutura
```

---

## 3. Passos

### Preparação

- [ ] **Passo 1** — `build.gradle.kts`: adicionar Spring Security, jjwt (api/impl/jackson), Flyway
      core + `flyway-database-postgresql`, springdoc, Kotest property + runner, spring-security-test
- [ ] **Passo 2** — `application.yml`: bloco `app.auth`, Flyway, springdoc com Swagger UI desligado;
      `application-test.yml` com segredo fixo de teste

### `common` — o que todas as unidades herdam

- [ ] **Passo 3** — `dominio/`: `Dinheiro` (escala 2, HALF_UP, `dividirEm` com resíduo na última),
      `Competencia` (ano-mês, `proxima`/`anterior`), `Escopo` — *NFR-U1-06, D-43*
- [ ] **Passo 4** — 🔬 **Testes de propriedade** de `Dinheiro` e `Competencia` com Kotest:
      soma exata da divisão, partes diferindo no máximo 0,01, round-trip, associatividade,
      virada de ano — *PBT-02, PBT-03, PBT-07, PBT-08*
- [ ] **Passo 5** — `web/`: `CodigoErro` (8 códigos), `RespostaErro`, `ErroHandler`,
      `FiltroCorrelacao` — *NFR-U1-09, RNF-09, D-53*
- [ ] **Passo 6** — `persistencia/`: `RepositorioComVisibilidade` (porta **sem método cru**) e o
      suporte que aplica o predicado — *D-52, RN-V01, o padrão central da unidade*
- [ ] **Passo 7** — `seguranca/`: `ContextoUsuario` (escopo de requisição), `EmissorDeToken`,
      `FiltroJwt`, `RegistroDeTentativas`, `CodificadorDeSenha`, `ConfiguracaoSeguranca` com as
      rotas públicas incluindo `/health` — *D-02, D-48, D-49, D-50, NFR-U1-02 a 05*
- [ ] **Passo 8** — Testes de `common`: `RegistroDeTentativas` (bloqueio e expiração),
      `EmissorDeToken` (emite, valida, recusa expirado e assinatura errada)
- [ ] **Passo 9** — Resumo em `aidlc-docs/construction/u1-fundacao/code/common-summary.md`

### `usuario` — H-01 a H-04

- [ ] **Passo 10** — `dominio/`: `Usuario` puro (sem JPA) e a porta `UsuarioRepositorio` —
      *RN-U01 a U07*
- [ ] **Passo 11** — `aplicacao/`: `UsuarioService` (cadastrar, consultarPerfil, atualizarPerfil) e
      `AutenticacaoService` (login com tempo constante) — *H-01, H-02, H-04*
- [ ] **Passo 12** — `adaptador/persistencia/`: `UsuarioJpa`, Spring Data, mapeador, adaptador
- [ ] **Passo 13** — `adaptador/web/`: `UsuarioController`, `AutenticacaoController`, DTOs com
      Bean Validation — *RNF-10*
- [ ] **Passo 14** — Testes de `usuario`: unidade para o serviço; integração com Testcontainers para
      e-mail duplicado (inclusive a corrida que só a restrição do banco pega), normalização,
      tempo constante no login, bloqueio após 5 falhas
- [ ] **Passo 15** — Resumo em `code/usuario-summary.md`

### `grupo` — H-05 a H-08

- [ ] **Passo 16** — `dominio/`: `Grupo`, `MembroGrupo` (histórico de participações), porta
      `GrupoRepositorio` — *RN-G01 a G09*
- [ ] **Passo 17** — `aplicacao/`: `GrupoService` — criar (grupo + associação do criador na mesma
      transação), renomear, listar, consultar, adicionar, remover, sair — *H-05 a H-08*
- [ ] **Passo 18** — `adaptador/persistencia/` e `adaptador/web/`
- [ ] **Passo 19** — Testes de `grupo`: 404 e não 403 para não-membro, reentrada criando linha nova,
      grupo vazio permitido, ex-membro perdendo visibilidade, membro novo vendo todo o histórico
- [ ] **Passo 20** — Resumo em `code/grupo-summary.md`

### Persistência e isolamento

- [ ] **Passo 21** — `V1__fundacao.sql`: 3 tabelas, PKs, FKs, único em `usuario.email`, **índice
      único parcial** em `membro_grupo(grupo_id, usuario_id) WHERE saiu_em IS NULL`, índice em
      `membro_grupo(usuario_id)` — *RNF-04, D-01*
- [ ] **Passo 22** — 🔒 **Teste de isolamento de dados** — o mais importante da unidade: dois
      usuários, um grupo, cobrindo os três casos de H-03 — *RN-V01 a V04, NFR-U1-04*
- [ ] **Passo 23** — Teste de integração da invariante de associação única, com inserção concorrente

### Infraestrutura decorrente

- [ ] **Passo 24** — `parameters.tf`: sexto parâmetro `/{nome}/auth/jwt-secret` como `SecureString`
      com valor gerado; `user-data.sh` e `write-env.sh` exportando `JWT_SECRET`;
      `docker-compose.prod.yml` repassando ao container
- [ ] **Passo 25** — `README.md`: como rodar local, como rodar os testes, como autenticar

### Fechamento

- [ ] **Passo 26** — Compilar e rodar a suíte inteira localmente
- [ ] **Passo 27** — Verificação final: nenhum `Double`/`Float` em caminho monetário; nenhum
      repositório de domínio com método sem filtro; nenhum segredo no repositório; nenhuma senha
      ou token em log; as 21 regras com teste que falha se a regra sair
- [ ] **Passo 28** — Resumo consolidado em `code/code-summary.md`

---

## 4. Rastreabilidade

| História | Passos |
|---|---|
| H-01 Criar conta | 10, 11, 12, 13, 14 |
| H-02 Autenticar | 7, 11, 13, 14 |
| H-03 Isolamento | 6, 7, **22** |
| H-04 Perfil | 11, 13 |
| H-05 Gerenciar grupos | 16, 17, 18, 19 |
| H-06 Gerenciar membros | 16, 17, 18, 19 |
| H-07 Histórico ao entrar | 16, 19, 22 |
| H-08 Sair do grupo | 17, 19, 22 |

---

## 5. Escopo e riscos

**28 passos.** Cerca de 45 arquivos novos e 5 modificados (`build.gradle.kts`, `application.yml`,
`parameters.tf`, `user-data.sh`, `write-env.sh`, `docker-compose.prod.yml`, `README.md`).

**O que pode dar errado, e o que fazer**:

| Risco | Tratamento |
|---|---|
| `ddl-auto: validate` reprovar o mapeamento contra a migration | É o ponto do requisito. Ajustar a migration, nunca desligar o `validate` |
| Hexagonal inflar o código | Ressalva já registrada e aceita (Q2 do NFR Design). Manter o mapeador burro e sem lógica |
| Teste de concorrência instável no CI | Se ficar intermitente, marcar e isolar em vez de enfraquecer a asserção |
| O deploy quebrar por falta de `JWT_SECRET` | Passo 24 vem antes do primeiro deploy com autenticação; sem o parâmetro a aplicação não sobe — e é o comportamento correto |

**O que este plano não faz**: nenhum endpoint de U2, U3 ou U4; nenhuma troca de e-mail ou de senha
(sem requisito); nenhum front-end.
