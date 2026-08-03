# Build and Test — Resumo

**Estado**: ✅ **199 testes, 0 falhas** — run `30817833392`, commit `c67bac5`, 16 s.

As cinco unidades estão entregues e verdes.

---

## 1. A suíte, em números

| Categoria | Testes | Precisa de Docker? |
|---|---|---|
| Unidade e propriedade | 82 | Não |
| Integração (Testcontainers) | 117 | **Sim** |
| **Total** | **199** | |

**22 classes de teste.** Nenhuma pulada, nenhuma ignorada.

### Por unidade

| Unidade | Testes | Destaque |
|---|---|---|
| U1 — Fundação | ~50 | `IsolamentoDeDadosTest` (H-03) |
| U2 — Lançamentos | ~37 | `IsolamentoDeGastosTest`, os dois totais |
| U3 — Crédito | ~50 | `FechamentoAgendadoTest` (**J-01** ponta a ponta) |
| U4 — Planejamento | ~28 | `PlanejamentoIntegracaoTest` (**J-02** nas duas bases) |
| U5 — Infraestrutura | — | Verificada por `terraform plan` e pelo ambiente `dev` de pé |

---

## 2. Como rodar

```bash
gradle build --no-daemon        # tudo, com Docker
gradle assemble --no-daemon     # so compila, sem testes
```

Detalhes em `build-instructions.md`, `unit-test-instructions.md` e
`integration-test-instructions.md`.

---

## 3. O que a suíte prova

### As invariantes monetárias

| Invariante | Onde |
|---|---|
| `soma(parcelas) == valorTotal`, **inclusive após sequências de edição** | `ParcelamentoPropriedadesTest` |
| `mensal × meses >= falta` — **suficiente**, não exata | `InvestimentoPropriedadesTest` |
| `totalPessoal` e `totalGrupo` **nunca somados** | `TotaisPropriedadesTest` + 3 testes de DTO |
| Nenhum `Double`/`Float` em caminho monetário | Verificação final de cada unidade |

### O isolamento de dados

| Camada | Mecanismo |
|---|---|
| **Compilador** | A porta não tem método cru (D-52, D-63) — consulta sem filtro é erro de compilação |
| **CI** | `ArquiteturaTest` (D-66) reprova entidade com dono fora do padrão |
| **Comportamento** | `IsolamentoDeDadosTest` e `IsolamentoDeGastosTest` |
| **Banco** | Índices únicos parciais e `CHECK`s |

### As três jornadas

| Jornada | Situação |
|---|---|
| J-01 — da compra ao pagamento | ✅ `FechamentoAgendadoTest`, ponta a ponta |
| J-02 — o realizado do orçamento | ✅ `PlanejamentoIntegracaoTest`, as duas bases |
| J-03 — total do grupo × pessoal | ✅ Resolvida na revisão 8; verificada em U2 |

---

## 4. O que a suíte **não** cobre, e por quê

| Não coberto | Razão |
|---|---|
| Testes de carga | Sem requisito de desempenho; RNF-12 é uso doméstico. Ver `performance-test-instructions.md` |
| Testes de contrato | Serviço único, sem consumidor externo além do front que ainda não existe |
| Testes end-to-end com navegador | Não há front-end neste repositório (escopo excluído na Requirements Analysis) |
| Testes de segurança automatizados | A extensão Security foi **desligada** na Question 14. Autenticação, isolamento e permissões permanecem como requisitos funcionais e **estão testados** |
| Comportamento com duas instâncias | RNF-12. O que quebraria está inventariado — um item |

---

## 5. Débitos e pendências que atravessam para fora do ciclo

| # | Item | Natureza |
|---|---|---|
| 1 | **RF-29 e H-27 desatualizados** por D-67 | Correção de texto nos requisitos. Não afeta código |
| 2 | **Passo 5b do runbook** — usuário `financial_app` não existe no RDS | O deploy passa (healthcheck não toca o banco), mas a primeira requisição autenticada falha |
| 3 | **R-01 reaberto para `prod`** — Free Tier recusou 7 dias de retenção | Decidir antes do primeiro dado real |
| 4 | **R-05** — role do CI com `AdministratorAccess` | A inline policy de privilégio mínimo existe, mas é subconjunto inoperante enquanto a outra estiver anexada |
| 5 | `RegistroDeTentativas` quebra com escala horizontal | Mantido deliberadamente; resolvê-lo exigiria armazenamento compartilhado |
| 6 | Duas aritméticas monetárias sem teste de comparação (D-64) | **Risco aceito por decisão**, com a alternativa registrada |
| 7 | `domain_name` vazio em `dev` | A API responde por HTTP no IP elástico |

> Os itens 2, 3, 4 e 7 são de infraestrutura e não bloqueiam a suíte. Os itens 1, 5 e 6 são decisões
> registradas, não esquecimentos.

---

## 6. O critério que este projeto adotou, e por que

**Nenhuma stage se declarou concluída antes do CI verde.**

A regra nasceu de um erro: em U1, os testes de integração foram escritos e não executados, e o CI
reprovou 3 de 69 — nenhum deles no código que os testes descrevem, todos na cola entre esse código e
a infraestrutura.

Virou passo explícito nos planos de U2, U3 e U4. Em U4, o CI passou de primeira.

| Unidade | CI na 1ª execução |
|---|---|
| U1 | ❌ 3 falhas em 69 |
| U2 | ❌ 2 falhas em 82 |
| U3 | ❌ 2 causas independentes |
| U4 | ✅ **verde** |
