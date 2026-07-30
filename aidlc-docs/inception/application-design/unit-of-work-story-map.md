# Mapa de Histórias por Unidade

**Stage**: INCEPTION - Units Generation - Part 2
**Timestamp**: 2026-07-30T16:11:59Z

**Cobertura**: 57 histórias + 3 jornadas = **60**, todas atribuídas a exatamente uma unidade.

---

## U1 — Fundação · 8 histórias

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-01 | Criar conta | M | ✅ |
| H-02 | Autenticar | M | ✅ |
| H-03 | Isolamento de dados entre usuários | M | ✅ |
| H-04 | Gerenciar o próprio perfil | S | |
| H-05 | Gerenciar grupos | M | ✅ |
| H-06 | Gerenciar membros do grupo | M | ✅ |
| H-07 | Visibilidade do histórico ao entrar num grupo | M | ✅ |
| H-08 | Sair de um grupo | S | |

**Épicos**: E1 (Identidade e Acesso), E2 (Grupos)

---

## U2 — Lançamentos · 9 histórias

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-09 | Definir o escopo de um lançamento | M | ✅ |
| H-13 | Editar lançamento do grupo como qualquer membro | M | ✅ |
| H-14 | Registrar o dono do lançamento | M | ✅ |
| H-15 | Gerenciar gastos — **à vista apenas** | M | ✅ |
| H-16 | Consultar gastos por período | M | ✅ |
| H-17 | Totalizar: total pessoal e total do grupo | M | ✅ |
| H-33 | Gerenciar categorias | M | ✅ |
| H-34 | Proteger categoria em uso | S | |
| H-35 | Categorias iniciais | C | |

**Épicos**: E3 (Compartilhamento e Visibilidade), E4 (Gastos), E7 (Categorias)

> **H-15 é parcial nesta unidade.** O critério *"associar forma de pagamento — à vista ou cartão"*
> só é cumprido integralmente em U3. Em U2, o endpoint aceita apenas gastos à vista.

---

## U3 — Crédito · 25 histórias + 2 jornadas

### Cartões de crédito

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-18 | Gerenciar cartões | M | ✅ |
| H-19 | Cartão pessoal ou do grupo | M | ✅ |
| H-20 | Determinar a fatura de competência | M | ✅ |
| H-21 | Consultar a fatura consolidada | M | ✅ |
| H-22 | Fatura aberta acumula novas compras | M | ✅ |
| H-23 | Marcar e desmarcar o pagamento da fatura | M | ✅ |
| H-24 | Proteger fatura paga contra alterações | M | ✅ |
| H-25 | Lançamento retroativo em fatura fechada | M | |
| H-26 | Consultar faturas futuras | S | |

### Compras parceladas

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-27 | Lançar uma compra parcelada | M | ✅ |
| H-28 | Distribuir o resíduo de centavos 🔬 | M | ✅ |
| H-29 | Garantir a integridade do parcelamento 🔬 | M | ✅ |
| H-30 | Corrigir uma compra parcelada | M | ✅ |
| H-31 | Excluir uma compra parcelada | M | ✅ |
| H-32 | Identificar a posição da parcela | S | |

### Contas a pagar

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-42 | Gerenciar contas a pagar | M | ✅ |
| H-43 | Ver tudo o que vence no período | M | ✅ |
| H-44 | Marcar conta como paga | M | ✅ |
| H-45 | Fatura de cartão vira conta a pagar | M | ✅ |
| H-46 | Cadastrar conta recorrente | M | |
| H-47 | Gerar as ocorrências de uma conta recorrente | M | |
| H-48 | Ajustar o valor no pagamento | M | |
| H-49 | Conta a pagar compartilhada no grupo | M | |
| H-50 | Consultar contas a vencer e vencidas | S | |
| H-51 | Encerrar uma conta recorrente | S | |

### Jornadas

| Jornada | Título | Áreas |
|---|---|---|
| J-01 | Compra parcelada em cartão do grupo | Cartões + Parcelamento + Grupo |
| J-03 | Entrar num grupo e começar a compartilhar | Grupo + Gastos + Cartões + Contas |

**Épicos**: E5 (Cartões), E6 (Compras Parceladas), E10 (Contas a Pagar)

> **Complemento de H-15**: a integração de `gasto` com cartão é implementada aqui, completando o
> critério de aceitação iniciado em U2.

---

## U4 — Planejamento · 15 histórias + 1 jornada

| História | Título | Prioridade | Núcleo |
|---|---|---|---|
| H-36 | Gerenciar receitas | M | |
| H-37 | Consultar receitas por período | M | |
| H-38 | Ver o balanço do período | S | |
| H-39 | Definir orçamento por categoria | M | |
| H-40 | Acompanhar orçado × realizado | M | |
| H-41 | Sinalizar estouro de orçamento | S | |
| H-52 | Gerenciar objetivos de investimento | M | |
| H-53 | Registrar aportes | M | |
| H-54 | Atualizar o saldo à mão | M | |
| H-55 | Ver o rendimento do objetivo | M | |
| H-56 | Definir meta e acompanhar progresso | M | |
| H-57 | Definir prazo e calcular o aporte necessário | M | |
| H-58 | Objetivo compartilhado no grupo | M | |
| H-59 | Aporte entra no balanço como gasto | M | |
| H-60 | Ver a posição consolidada | S | |

| Jornada | Título | Áreas |
|---|---|---|
| J-02 | Fechar o mês | Contas + Cartões + Gastos + Orçamento |

**Épicos**: E8 (Receitas), E9 (Orçamento), E11 (Investimentos)

> **J-02 é a única amarra de U4 com U3.** Todas as demais histórias desta unidade dependem apenas
> de U1 e U2. Adiar J-02 tornaria U3 e U4 paralelizáveis.

---

## U5 — Infraestrutura · nenhuma história

Coberta por requisitos técnicos, sem interação de usuário final: **RF-45 a RF-54** (infraestrutura),
**RF-81 a RF-93** (CI/CD), **RNF-13 a RNF-17**.

Justificativa da ausência de histórias em `user-stories-assessment.md`: forçá-los em formato de
história produziria narrativas artificiais ("como desenvolvedor, quero um pipeline...") sem ganho
de clareza.

---

## Verificação de cobertura

### Todas as histórias atribuídas

| Unidade | Histórias | Jornadas | Total |
|---|---|---|---|
| U1 | 8 | 0 | 8 |
| U2 | 9 | 0 | 9 |
| U3 | 25 | 2 | 27 |
| U4 | 15 | 1 | 16 |
| U5 | 0 | 0 | 0 |
| **Total** | **57** | **3** | **60** |

✅ **57 histórias + 3 jornadas = 60.** Confere com `stories.md`.
✅ **Nenhuma história sem unidade.**
✅ **Nenhuma história em duas unidades.** (`H-15` é a única parcial — mesma história, critérios
completados em U3.)

### Épicos por unidade

| Épico | Unidade |
|---|---|
| E1 — Identidade e Acesso | U1 |
| E2 — Grupos | U1 |
| E3 — Compartilhamento e Visibilidade | U2 |
| E4 — Gastos | U2 (+ complemento em U3) |
| E5 — Cartões de Crédito | U3 |
| E6 — Compras Parceladas | U3 |
| E7 — Categorias | U2 |
| E8 — Receitas | U4 |
| E9 — Orçamento | U4 |
| E10 — Contas a Pagar | U3 |
| E11 — Investimentos | U4 |

### Núcleo mínimo

**28 histórias** marcadas como `NÚCLEO`, distribuídas em:

| Unidade | Histórias do núcleo |
|---|---|
| U1 | 6 de 8 |
| U2 | 7 de 9 |
| U3 | 15 de 25 |
| U4 | 0 de 15 |

O núcleo está **inteiramente em U1, U2 e U3**. Concluídas as três, o sistema é utilizável — sem
receitas, orçamento nem investimentos.

### Alvos de property-based testing 🔬

| História | Unidade | Invariante |
|---|---|---|
| H-28 | U3 | `soma(parcelas) == valorTotal`, para qualquer valor e `n ≥ 1` |
| H-29 | U3 | A igualdade se mantém após qualquer sequência de criação e edição |

O componente `Dinheiro.dividirEm` nasce em **U1**, mas só é exercitado por PBT em **U3**, quando o
parcelamento existe. Os testes de propriedade de U1 cobrem soma e subtração; os de divisão entram
com o parcelamento.
