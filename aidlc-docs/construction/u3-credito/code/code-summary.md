# Resumo de Código — U3 Crédito

**CI verde** no run `30814981176`, commit `9fe61a9`. A maior entrega do projeto.

---

## 1. Escopo entregue

| | |
|---|---|
| Entidades | `Cartao`, `Fatura`, `Compra`, `Parcela`, `ContaAPagar`, `ContaRecorrente` |
| Histórias | H-18 a H-32, H-42 a H-51 (25) + J-01 e J-03 |
| Regras | 37 (RN-K, RN-F, RN-P, RN-A, RN-R) |
| Decisões | D-67 a D-76 |
| Arquivos | ~20 novos, 8 modificados |
| Migration | `V3__credito.sql` — 6 tabelas, 3 índices únicos parciais |

**A tabela `gasto` de U2 não foi alterada.** `cartao_id` e `competencia` nasceram nuláveis lá, por
decisão da Units Generation — é o `ALTER TABLE` que não precisou existir.

---

## 2. As quatro garantias estruturais

### D-73 — o bloqueio de fatura paga desce para o adaptador

`ProtecaoFatura` é invocada **duas vezes de propósito**: no serviço, para dar mensagem acionável; no
adaptador de persistência, para impedir a gravação. Esquecer no serviço produz mensagem ruim;
esquecer no adaptador não é possível, porque toda gravação passa por lá.

### D-74 — o job com advisory lock

`@Scheduled` + `pg_try_advisory_lock`. **A lista do que quebra com escala horizontal não cresceu** —
o `RegistroDeTentativas` de U1 continua sendo o único item.

A idempotência protege mais que o lock: o lock evita trabalho duplicado, a idempotência evita
**dano**. O pior caso do agendamento passa a ser trabalho desperdiçado, nunca conta a pagar em dobro.

### D-75 — `valorTotal` calculado na leitura

Oito caminhos de escrita precisariam manter a invariante persistida, e esquecer um produziria um
número errado que parece certo. Derivado, a invariante é verdadeira por construção — verificado pelo
teste que exclui um gasto e vê o total mudar sozinho.

### D-68 — a reversão de `dividirEm`

A última parcela absorve o resíduo. A propriedade *"partes diferem no máximo 0,01"* foi
**substituída** por *"as primeiras n-1 são iguais entre si"*, nunca desligada. Acrescentados os
exemplos que **distinguem** as duas regras — 100,00 em 7x e 1,19 em 120x —, já que o exemplo
canônico de 100,00 em 3x dá o mesmo resultado nas duas (O-28).

---

## 3. O algoritmo da competência

```
diaEfetivo(dia, anoMes) = min(dia, ultimoDiaDoMes)              // D-69
competenciaDe(data, diaFechamento):
    dia < diaEfetivo  ->  mes + 1
    dia >= diaEfetivo ->  mes + 2                                // corte exclusivo, E-03
```

Isolado como **função pura**, sem banco nem Spring. Sete propriedades, incluindo a de
**monotonicidade** — que é a que pega o engano de usar `diaVencimento` no lugar de `diaFechamento`,
erro natural porque o vencimento é a data que o usuário vê no aplicativo do banco.

---

## 4. Testes

| Arquivo | Cobre |
|---|---|
| `CalculadoraDeCompetenciaTest` | 11 — dia efetivo, monotonicidade, os 3 cenários de H-20, fevereiro bissexto, virada de ano |
| `ParcelamentoPropriedadesTest` | 10 — soma exata, primeiras n-1 iguais, e **a invariante após qualquer sequência de edições** (H-29) |
| `DinheiroPropriedadesTest` | 18 — reescrito para D-68 |
| `CartaoIntegracaoTest` | 8 |
| `FaturaIntegracaoTest` | 12 — competência, acumulação, parcelamento, edição, exclusão |
| `ContaIntegracaoTest` | 8 — visão consolidada, projeção, ajuste de valor, encerramento |
| `FechamentoAgendadoTest` | 6 — idempotência, recuperação, **J-01 ponta a ponta** |
| `ArquiteturaTest` | 4 — com a regra corrigida |

O teste de J-01 percorre o ciclo inteiro: gasto no cartão → competência → fechamento → conta a pagar
→ pagamento → fatura **PAGA por derivação** → bloqueio do retroativo → desmarcar libera.

---

## 5. O que o CI encontrou

Primeira execução: reprovou por **dois motivos independentes**.

### A chave YAML duplicada

`application-test.yml` ficou com duas chaves `app:`. O snakeyaml recusa, o contexto Spring não sobe,
e **todos** os testes de integração caem. Um defeito de uma linha produziu 40 testes vermelhos, e a
lista de falhas não apontava para ele em lugar nenhum.

### A regra de arquitetura verificava o nome, achando que verificava o tipo

Falso positivo em `ContaAPagar` e `ContaRecorrente`. O casamento era por **prefixo de nome**, e
passou em U2 por coincidência: `CategoriaRepositorio` começa com `Categoria`; `ContaRepositorio` não
começa com `ContaAPagar`.

Corrigida para ler o **argumento genérico** de `RepositorioComVisibilidade<T>`.

> A regra existia justamente para não depender de disciplina humana — e passou a depender de uma
> convenção não escrita, que quebrou na primeira unidade que não a seguiu por acaso.

---

## 6. Verificação final (Passo 42)

| Item | Resultado |
|---|---|
| `Double`/`Float` em caminho monetário | Nenhum |
| **Divisão monetária em SQL** | Nenhuma. Só `SUM` — a divisão vive em `Dinheiro.dividirEm` |
| Consulta de domínio sem filtro | Só `CartoesParaFechamento`, isolada e nomeada (§8.1 do plano) |
| Gravação com competência fora da guarda de D-73 | Nenhuma |
| `save` sem flush onde a garantia é do banco | Nenhum |
| As 37 regras com teste | Sim |

---

## 7. O que fica em aberto

| Item | Destino |
|---|---|
| **J-02** — o realizado do orçamento conta pela data da compra ou pela competência? | U4. U3 deixa **as duas datas** disponíveis em cada parcela |
| **RF-29 e H-27 desatualizados** por D-67 | Correção de texto nos requisitos |
| O agendador agora existe | A próxima tarefa periódica não encontrará a barreira que U3 encontrou |
