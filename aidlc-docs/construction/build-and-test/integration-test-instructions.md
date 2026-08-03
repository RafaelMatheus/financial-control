# Execução dos Testes de Integração

**117 dos 199 testes.** Rodam contra **PostgreSQL real** via Testcontainers — e a escolha não é
purismo.

---

## 1. Por que PostgreSQL real, e não H2

Três invariantes centrais do sistema são **índices únicos parciais**, sintaxe PostgreSQL pura que o
JPA não expressa e o `ddl-auto: validate` não confere:

| Índice | Unidade | Protege |
|---|---|---|
| `uk_membro_grupo_ativo` | U1 | No máximo uma associação ativa por par (RN-G05) |
| `uk_categoria_pessoal` / `uk_categoria_grupo` | U2 | Nome único por dono ou por grupo (RN-C02) |
| `uk_conta_ocorrencia` | U3 | Uma ocorrência por competência (RN-R04) |
| `uk_orcamento_pessoal` / `uk_orcamento_grupo` | U4 | Um teto por categoria, mês e escopo (RN-O01) |

**Num banco em memória essas invariantes simplesmente não existiriam** — e os testes de concorrência
passariam por não ter nada para barrar. Passariam pelo motivo errado, que é a pior forma de passar.

---

## 2. Rodar

```bash
docker info                 # precisa responder
gradle test --no-daemon     # roda a suite inteira
```

Só os de integração:

```bash
gradle test --tests '*IntegracaoTest*' \
            --tests '*Isolamento*' \
            --tests '*ConcorrenciaTest*' \
            --tests '*FechamentoAgendadoTest*' --no-daemon
```

### 2.1 Sem Docker

**Não há alternativa local.** Foi a situação de toda a máquina de desenvolvimento deste projeto, e a
razão de o CI ser a única verificação real — ver §6.

---

## 3. Como a base é preparada

`SuporteDeIntegracao` é a classe-base. Duas decisões nela merecem atenção:

**`TRUNCATE ... CASCADE` entre testes, e não `@Transactional` com rollback.** Os testes de
concorrência precisam de **commits de verdade**; um rollback automático esconderia justamente o que
eles verificam.

**`RegistroDeTentativas.limparTudo()` no mesmo `@BeforeEach`.** Ele é singleton com estado **em
memória**, e o `TRUNCATE` não o alcança. Sem essa limpeza, o teste de bloqueio deixa a conta travada
por 15 minutos e derruba todo teste seguinte que use o mesmo e-mail.

> A segunda é a mesma propriedade que faria o bloqueio quebrar com duas instâncias. **Dois testes no
> mesmo processo são, para aquele componente, indistinguíveis de duas requisições na mesma
> instância** — foi assim que o CI a encontrou, em U2.

---

## 4. O que existe, por unidade

| Classe | Testes | Cobre |
|---|---|---|
| `UsuarioIntegracaoTest` | 11 | Cadastro, login, tempo constante, bloqueio |
| `GrupoIntegracaoTest` | 9 | Grupos, membros, reentrada, grupo vazio |
| **`IsolamentoDeDadosTest`** | 8 | **H-03 — o mais importante de U1** |
| `CategoriaIntegracaoTest` | 12 | Unicidade por escopo, realocação, conjunto inicial |
| `GastoIntegracaoTest` | 13 | Os dois totais, filtro de grupo, troca de escopo |
| **`IsolamentoDeGastosTest`** | 5 | **O mais importante de U2** |
| `CartaoIntegracaoTest` | 8 | Dias 1–31, cartão de grupo, encerramento |
| `FaturaIntegracaoTest` | 12 | Competência, acumulação, parcelamento, edição |
| `ContaIntegracaoTest` | 8 | Vencimentos, projeção, ajuste de valor |
| **`FechamentoAgendadoTest`** | 6 | **J-01 ponta a ponta**, idempotência, recuperação |
| `PlanejamentoIntegracaoTest` | 16 | **J-02 nas duas bases**, balanço, investimento |
| `ConcorrenciaTest` | 4 | As invariantes que só o banco garante |
| `FinancialControlApplicationTests` | 1 | O contexto sobe |

---

## 5. Os testes que exercem as jornadas

### J-01 — da compra ao pagamento (`FechamentoAgendadoTest`)

```
gasto no cartao -> competencia -> job fecha -> conta a pagar -> pagamento
    -> fatura PAGA por derivacao -> retroativo bloqueado -> desmarcar libera
```

Atravessa U1, U2 e U3. O `Clock` é **injetado e substituído** no teste, em vez de esperar o cron — é
precisamente a razão de o relógio existir como bean desde U1.

### J-02 — o realizado do orçamento (`PlanejamentoIntegracaoTest`)

A mesma compra parcelada dando **1.200,00 em julho** por uma base e **100,00 em setembro** por outra.
Atravessa U2, U3 e U4.

### Consistência entre unidades

`as duas bases COINCIDEM quando nao ha cartao` é uma **prova cruzada**: se falhar, o realizado de U4
divergiu dos totais de U2, e o defeito estaria numa das duas.

---

## 6. A lição que este projeto aprendeu duas vezes

Em U1, os testes de integração foram **escritos e não executados** — sem Docker na máquina. O plano
registrou o desvio e disse, com todas as letras, que a aprovação deveria esperar o CI.

O CI reprovou **3 de 69**. Nenhum dos três estava no código que os testes descrevem; todos estavam
na cola entre esse código e a infraestrutura.

Em U3, o mesmo intervalo produziu outros dois defeitos.

> **Um teste escrito e não executado é documentação, não verificação.** Ele registra a intenção com
> a mesma precisão do teste que roda — e é por isso que engana: passa na revisão, entra no commit e
> conta no total (research-log 3.37, O-29).

**Consequência prática**: nenhuma stage deste projeto se declarou concluída antes do CI verde, e
essa regra virou passo explícito nos planos de U2, U3 e U4.
