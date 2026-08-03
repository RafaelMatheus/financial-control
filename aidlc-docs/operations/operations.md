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

### 4.5 Dívida da trust policy do OIDC

A lista de `sub` confiados é a **união** do padrão desenhado com o formato herdado da role manual,
que traz IDs numéricos e cuja origem nunca foi identificada. Foi uma escolha de assimetria de custo:
errar para o lado permissivo custa uma condição a mais; errar para o restritivo custa o acesso ao CI.

**Confirmado o `sub` real, a lista deve encolher** para satisfazer RF-93.

---

## 5. Ordem sugerida, se o projeto seguir

1. **Passo 5b** — sem ele nada funciona de verdade em `dev`
2. **`domain_name`** — sem TLS não há uso real
3. **R-05** — remover `AdministratorAccess` antes de `prod` existir
4. **R-01** — decidir a retenção **antes** do primeiro dado real
5. Dívida da trust policy
6. Monitoramento — hoje não há requisito, e acrescentá-lo seria escopo novo

---

## 6. Fim do ciclo AI-DLC

| Fase | Situação |
|---|---|
| INCEPTION | ✅ 7 stages, nenhuma pulada |
| CONSTRUCTION | ✅ 5 unidades + Build and Test |
| OPERATIONS | ⬜ **Placeholder do método** — nada a executar |

**Nenhuma decisão do ciclo permanece adiada.** A última, J-02, fechou na Functional Design de U4.

O registro completo do processo, para fins de pesquisa, está em `aidlc-docs/research-log.md`.
