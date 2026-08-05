# Operations — estado ao fim do ciclo

## 1. A stage do método está vazia

`operations/operations.md` das regras é explícito:

> *"This phase is currently a placeholder and will be expanded in future versions. (...) The AI-DLC
> workflow currently ends after the Build and Test phase in CONSTRUCTION."*

**Não há passos a executar aqui.** Registrar isso é o conteúdo da stage.

---

## 2. A lacuna foi fechada dentro da Construction, e isso não é acidente

Esta ausência foi **detectada na Requirements Analysis**, quando o usuário perguntou em que momento a
infraestrutura seria de fato provisionada. A resposta exigiu ler as regras do método e constatar que
*não acontece* — o AI-DLC entrega o Terraform escrito e não o aplica.

A lacuna gerou **13 requisitos novos** (RF-81 a RF-93) e uma decisão de arquitetura de entrega:

| ID | Decisão |
|---|---|
| D-21 | GitHub Actions como plataforma de CI/CD |
| D-22 | Autenticação AWS por OIDC, sem credencial de longa duração |
| D-23 | Amazon ECR como registry |
| D-24 | Deploy via SSM Run Command; porta 22 fechada |
| D-25 | `terraform apply` automático no merge |
| D-26 | Bootstrap manual e único |

**O resultado é que a fase de Operations deste projeto já aconteceu** — como U5, dentro da
Construction. O ambiente `dev` está de pé desde 2026-07-31.

> Vale registrar o que isso diz sobre o método: uma pergunta de esclarecimento sobre o **processo**
> mereceu o mesmo rigor de investigação que uma pergunta sobre o **produto** (research-log O-09).

---

## 3. O que existe hoje, operacionalmente

| Item | Estado |
|---|---|
| Ambiente `dev` | ✅ Provisionado — `api_url=http://52.73.89.203`, `instance_id=i-0151f919886de23ca` |
| Banco | ✅ RDS PostgreSQL 16 em subnet privada |
| State do Terraform | ✅ `s3://financial-control-tfstate-594116288641/` |
| CI da aplicação | ✅ Verde — 199 testes |
| Deploy | ✅ Automático no merge, via SSM |
| Ambiente `prod` | ❌ **Não existe** |
| Monitoramento / alarmes | ❌ Não existe. Nenhum requisito o pede |
| Runbook de incidente | ❌ Não existe |

---

## 4. Pendências operacionais reais

Estas **não** são do placeholder — são itens concretos que o ciclo deixou abertos.

### 4.1 Passo 5b do runbook — usuário `financial_app` (bloqueante para uso real)

O Terraform cria o RDS, mas **não alcança o banco** para criar o usuário da aplicação: ele está em
subnet privada, e o provider não tem rota até lá.

**Consequência hoje**: o deploy passa, porque o healthcheck não toca o banco. **A primeira
requisição autenticada falha.**

**Como resolver**: SQL via SSM, a partir da instância, conforme
`aidlc-docs/inception/requirements/bootstrap-runbook.md`.

### 4.2 R-01 reaberto para `prod` — retenção de backup

A conta está no plano **Free Tier**, que recusou retenção de 7 dias com `FreeTierRestrictionError`.
Em `dev` caiu para **1 dia**, o que é inconsequente sem dado real.

Em `prod`, isso reabre um risco declarado **fechado** na revisão 9 dos requisitos — que o considerou
resolvido justamente porque o RDS gerenciado traria 7 dias.

**Duas saídas**: subir o plano da conta, ou aceitar formalmente a retenção menor e reabrir o R-01 em
`requirements.md`. **Decidir antes do primeiro dado real.**

### 4.3 R-05 — `AdministratorAccess` na role do CI

A role em uso foi criada à mão no console com `AdministratorAccess` anexado. A inline policy de
privilégio mínimo **existe no Terraform**, mas é um subconjunto inoperante enquanto a outra estiver
lá.

Combinado com D-25 (apply automático no merge), qualquer push na `main` que toque
`infra/terraform/**` tem poder total sobre a conta.

### 4.4 `domain_name` vazio — sem TLS

A API responde por **HTTP** no IP elástico. O nginx e o Let's Encrypt estão no Terraform e esperam
um domínio.

### 4.5 ~~Dívida da trust policy do OIDC~~ — ✅ RESOLVIDA em 2026-08-05

A lista de `sub` confiados era a união do padrão desenhado com um formato herdado da role manual,
que traz IDs numéricos e cuja origem o ciclo registrou como **não identificada**.

**A origem está identificada.** Ao integrar o repositório do front, o primeiro deploy falhou com
`Not authorized to perform sts:AssumeRoleWithWebIdentity` — porque a entrada nova usava o formato
documentado do GitHub, `repo:OWNER/REPO:ref:...`, e **esta conta não o emite**.

Ela emite com IDs numéricos embutidos:

```
repo:OWNER@<owner_id>/REPO@<repo_id>:<contexto>
```

Conferidos pela API do GitHub:

| Valor | Origem |
|---|---|
| `25590639` | `id` do usuário `RafaelMatheus` |
| `1316467420` | `id` do repositório `financial-control` |
| `1322237708` | `id` do repositório `financial-control-web` |

Batem exatamente com o `sub` herdado. A variável `github_repositories_front` passou a receber os
três campos e gerar **as duas formas** por repositório — a documentada não custa nada e cobre o caso
de a conta voltar ao padrão; a numérica é a que de fato autentica.

> **A dívida se pagou sozinha ao ser exercitada.** Ela ficou aberta enquanto só havia um
> repositório, porque o valor herdado funcionava e ninguém precisava entendê-lo. O segundo
> repositório obrigou a reproduzir o formato — e reproduzir exigiu compreender.

---

---

## 4.6 O front-end, integrado em 2026-08-05

Fora do ciclo AI-DLC, mas parte do estado operacional.

### O que mudou na AWS

| Recurso | Mudança |
|---|---|
| **ECR** | Segundo repositório, `financial-control-web`. **`MUTABLE`**, ao contrário do da aplicação — além da tag por commit SHA, o front publica `latest`, e a composição a usa como default |
| **IAM** | A trust policy da role do CI passou a confiar no repositório do front. Mudança **puramente aditiva**: uma linha nova, nenhuma removida |
| **Instância** | Container `web` na composição, alcançável só pela rede interna |
| **nginx** | `/api/` → aplicação (a barra final **remove o prefixo**), `/` → front |

**Uma role para os dois repositórios**, e não uma por repo. Eles precisam exatamente das mesmas
permissões — ECR e SSM na mesma instância — e duas roles com a mesma policy só duplicariam a
manutenção sem aumentar a segurança.

### A decisão que evita derrubar a API

O endereço do front vai numa **variável** no `proxy_pass` do nginx:

```nginx
location / {
    set $front http://web:80;
    proxy_pass $front;
}
```

Com literal, o nginx resolve o nome no **carregamento da configuração** e se recusa a subir se o
container `web` não existir — **derrubando a API junto**. Com variável, resolve por requisição: a
API continua no ar e `/` responde 502 até a imagem chegar.

Pelo mesmo motivo, `web` **não** entra em `depends_on`.

### A ordem que importa

O deploy do front **não entrega a composição** — ela vive neste repositório e já está na instância.
A sequência correta é:

1. Aplicar o bootstrap (cria o ECR do front, amplia a trust policy)
2. Definir `ECR_WEB_REPOSITORY` nos dois repositórios, e as variáveis AWS no repo do front
3. Deploy do front (publica a imagem `latest`)
4. Deploy do backend (entrega o nginx e a composição novos)

> **Esta ordem foi violada durante a execução, e o registro fica.** O `git push` da correção do OIDC
> levou junto o commit do nginx, disparando o deploy do backend **antes** de a imagem existir. Falhou
> com `manifest for ...financial-control-web:latest not found` — exatamente o cenário mapeado.
>
> Na retentativa, a tag imutável da aplicação barrou o rebuild (`the image tag already exists and
> cannot be overwritten`). O caminho de rollback do próprio workflow resolveu: informar `image_tag`
> pula o build e vai direto ao deploy. **RF-87 funcionando como projetado**, num uso que não era o
> previsto.

### Verificação final

| Rota | Resultado |
|---|---|
| `/` | HTML do front |
| `/health` | `{"status":"UP"}` |
| `/api/v3/api-docs` | Contrato OpenAPI |
| `POST /api/usuarios` → `POST /api/auth/login` → `GET /api/usuarios/eu` | Fluxo completo, 201 e 200 |

**Consequência de o banco responder**: o passo 5b do runbook (§4.1) **deixou de ser bloqueante** —
a aplicação conecta e persiste. Ele permanece registrado como pendência de higiene, não de operação.

---

## 5. Ordem sugerida, se o projeto seguir

1. **`domain_name`** — sem TLS não há uso real, e hoje a **senha do login trafega em claro**
2. **R-05** — remover `AdministratorAccess` da role do CI antes de `prod` existir
3. **R-01** — decidir a retenção de backup **antes** do primeiro dado real
4. **Passo 5b** — higiene; deixou de ser bloqueante (§4.6)
5. ~~Dívida da trust policy~~ — ✅ resolvida (§4.5)
6. Monitoramento — hoje não há requisito, e acrescentá-lo seria escopo novo

> O item 1 subiu para primeiro **por causa do front**. Enquanto a API era consumida por `curl`, HTTP
> era um incômodo. Com uma tela de login num navegador, é a senha de quem entra.

---

## 6. Fim do ciclo AI-DLC

| Fase | Situação |
|---|---|
| INCEPTION | ✅ 7 stages, nenhuma pulada |
| CONSTRUCTION | ✅ 5 unidades + Build and Test |
| OPERATIONS | ⬜ **Placeholder do método** — nada a executar |

**Nenhuma decisão do ciclo permanece adiada.** A última, J-02, fechou na Functional Design de U4.

O registro completo do processo, para fins de pesquisa, está em `aidlc-docs/research-log.md`.
