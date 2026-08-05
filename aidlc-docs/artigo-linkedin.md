# A IA escreveu 199 testes. O trabalho foi decidir 84 vezes.

Construí um sistema de controle financeiro do zero usando AI-DLC, o método de
desenvolvimento assistido por IA da AWS. Backend em Kotlin, front em React,
infraestrutura em Terraform, tudo rodando na AWS.

O código saiu rápido. Não foi ele o gargalo.

O que consumiu o tempo — e o que sobrou de mais valioso — foram **84 decisões
registradas**, cada uma com a alternativa que eu recusei anotada ao lado.

---

## O que foi construído

Um controle financeiro pessoal e de grupos: gastos, cartões com fatura e
parcelamento, contas a pagar com recorrência, orçamento por categoria e
objetivos de investimento.

**Números do ciclo:**

- 97 requisitos, 57 histórias de usuário, 107 regras de negócio
- 199 testes, 0 falhas
- 5 unidades de trabalho, entregues em sequência
- 4 migrations, 39 rotas de API

Os dois repositórios são públicos:
- Backend e infraestrutura: `github.com/RafaelMatheus/financial-control`
- Front-end: `github.com/RafaelMatheus/financial-control-web`

---

## O passo a passo

O AI-DLC organiza o trabalho em fases com **portões de aprovação explícitos**.
Nada avança sem eu dizer que pode. Foi assim que aconteceu:

**1. Engenharia reversa.** O repositório tinha um esqueleto Spring Boot sem
domínio nenhum. A primeira coisa que a IA fez foi ler o que existia e me
apresentar o que encontrou — incluindo um débito que quebraria a aplicação na
primeira entidade criada.

**2. Requisitos.** 17 perguntas de esclarecimento antes de escrever qualquer
linha. O escopo cresceu: o que começou como "cadastrar gastos e parcelas de
cartão" virou multiusuário, com grupos, orçamento e investimentos.

**3. Histórias e design de aplicação.** 57 histórias, 3 jornadas transversais,
o contrato da API em OpenAPI.

**4. Decomposição em unidades.** O sistema virou 5 unidades de trabalho com
dependências mapeadas. A infraestrutura foi a primeira — de propósito, para o
CI ficar verde desde o começo.

**5. Por unidade: design funcional, design não-funcional, código.** Cada uma
com plano numerado, perguntas de esclarecimento e portão de aprovação. Cada
unidade só fechava com o CI verde.

**6. Build and test, e encerramento.** 199 testes ao final.

Do primeiro comando ao ambiente no ar: um ciclo completo, com rastro de tudo.

---

## Cinco percepções

### 1. O gargalo virou a decisão, não o código

Quando a IA escreve o código em minutos, o que sobra para o humano é **escolher**.
E escolher com consequência: cada uma das 84 decisões mudava alguma coisa no
sistema.

Percebi isso na prática pelo tipo de pergunta que me era feita. Não eram
perguntas de sintaxe — eram do tipo *"a compra parcelada conta no orçamento no
mês da compra ou no mês em que você paga?"*. Não existe resposta certa. Existe
resposta que eu tenho que dar.

### 2. Uma pergunta sobre o processo valeu mais que qualquer pergunta sobre código

No meio dos requisitos eu perguntei: **"em que momento a infraestrutura vai ser
provisionada?"**

A resposta foi que não seria. O método entrega o Terraform escrito e não o
aplica — a fase de operações é um placeholder vazio. A lacuna estava lá, mas o
método não declara ter.

Aquela pergunta gerou 13 requisitos novos, 6 decisões de arquitetura e uma
unidade inteira que não existia no plano. E porque veio no começo, custou o
mesmo que qualquer outro esclarecimento. Se tivesse vindo no fim, quatro
unidades já dependeriam de um ambiente que não existe.

### 3. Registrar a alternativa recusada é o que faz o registro valer

Em uma das decisões eu escolhi somar valores no banco e **dispensei** o teste
que compararia essa soma com a do código. Isso deixa um risco em aberto: duas
aritméticas monetárias sem verificação de que concordam.

Isso ficou escrito — com a alternativa recusada ao lado.

Seis meses depois, ninguém consegue saber se um risco foi **aceito** ou
**esquecido** olhando só para o código. A diferença não está no risco: está em
poder revisar a decisão sabendo o que ela custou.

### 4. A verificação encontrou o que o design não estava olhando

O CI encontrou 7 defeitos ao longo do ciclo. **Nenhum estava numa regra de
negócio.**

Todos moravam na cola entre o código e a infraestrutura: o momento em que o JPA
manda o INSERT, a ordem entre dois validadores que discordavam sobre o mesmo
campo, o escopo de vida de um contador em memória que o `TRUNCATE` não alcança.

Um deles me marcou: numa unidade, os testes de integração foram **escritos e não
executados** — eu não tinha Docker na máquina. Estava documentado que a
aprovação deveria esperar o CI. O CI reprovou 3 de 69.

*Um teste escrito e não executado é documentação, não verificação.*

Virou regra nas unidades seguintes. Na última, o CI passou de primeira.

### 5. Regra automatizada também pode verificar a coisa errada

Criamos um teste de arquitetura que reprova o build quando uma entidade nasce
fora do padrão de isolamento de dados. Ele existia justamente para não depender
de disciplina humana.

Ele reprovou código correto.

O motivo: ele casava entidade e repositório por **prefixo de nome**, achando que
verificava uma relação de tipo. Funcionou por acaso em duas unidades, porque os
nomes coincidiam. Quebrou na primeira em que não coincidiram.

A automação não te salva de verificar a coisa errada. Ela só faz isso em escala.

---

## O que eu faria diferente

**Rodaria os testes de integração desde o primeiro dia.** O intervalo entre
"escrito" e "observado" custou defeitos em duas unidades.

**Cruzaria os artefatos mais cedo.** Encontrei uma contradição entre três
documentos já aprovados — sobre como se lança uma compra parcelada — só quando
precisei implementar os três ao mesmo tempo. Cada um era coerente sozinho. A
incoerência não tinha dono.

---

## O que fica

O sistema está no ar. Mas o que eu levo do ciclo não é o código: é o registro de
por que ele é assim.

Quando a IA escreve, o diferencial deixa de ser velocidade de digitação e passa a
ser **qualidade de decisão** — e a capacidade de deixar rastro dela.

O registro completo, com as 84 decisões, está nos repositórios.

---

*#DesenvolvimentoDeSoftware #IA #AIDLC #Kotlin #React #AWS #Arquitetura*

---
---

# Anexo — a análise editorial

> Esta parte **não vai no artigo**. É o raciocínio por trás dele, para você poder
> reescrever com conhecimento de causa.

## A tese, e o que foi sacrificado

O material tem 51 seções de research-log e 84 decisões. Um artigo que tente cobri-lo
não é lido. A decisão editorial foi escolher **uma tese** — *quando a IA escreve o
código, o gargalo vira a decisão* — e sacrificar tudo que não a sustenta:
arquitetura hexagonal, detalhes de infraestrutura, o modelo de domínio inteiro.

**O ângulo recusado**: "usei IA para construir um sistema". É o que está saturado, e
o número que impressiona (199 testes) é o menos interessante do conjunto. O contraste
**84 decisões × 199 testes** é o que diferencia.

## De onde vem cada percepção

Nenhuma foi inventada. Todas saem do registro:

| # | Percepção | Evidência |
|---|---|---|
| 1 | O gargalo virou a decisão | 84 decisões; ~30 rodadas de perguntas de esclarecimento |
| 2 | A pergunta sobre processo valeu mais | **Você fez essa pergunta.** Gerou RF-81 a RF-93, D-21 a D-26 e a unidade U5 |
| 3 | Registrar a alternativa recusada | D-64: você escolheu o risco sabendo da alternativa, e ela ficou escrita |
| 4 | A verificação achou o que o design não olhava | Os 7 defeitos do CI, nenhum numa regra de negócio |
| 5 | Regra automatizada erra o alvo | O `ArquiteturaTest` reprovando `ContaAPagar` e `Aporte`, ambos corretos |

## O padrão que talvez seja a percepção mais forte

**Você delegou o "como" e reteve o "o quê".**

Das ~30 decisões que passaram por você, divergiu da recomendação em **seis** — e
todas as seis são de **produto ou arquitetura**, nenhuma de stack:

| Decisão | O que você escolheu | O que era recomendado |
|---|---|---|
| D-51 | Hexagonal com portas e adaptadores | Três camadas |
| D-55 | Exigir o filtro de grupo | Subtotal por grupo |
| D-68 | Última parcela absorve o resíduo | Um centavo por parte |
| D-76 | Uma entrega só para U3 | Dividir em duas |
| D-77 | O usuário escolhe a base do orçamento | Competência da parcela |
| D-78 | Orçamento pode ser de grupo | Pessoal apenas |

Em contrapartida, acatou a recomendação em **todas** as decisões de stack: JWT,
BCrypt, Kotest, springdoc, ArchUnit, porta sem método cru, guarda no adaptador.

E interrompeu três vezes para perguntar do **sistema rodando**, não do código:
*"tem um /swagger no servidor já?"*, *"verifica pq a pipeline quebrou"*, *"o front
já foi deployado algo?"*.

> Isso é mais específico e mais difícil de copiar que qualquer uma das cinco
> percepções acima. Se for verdade, é a melhor abertura possível para o artigo.

## O que ficou de fora e poderia entrar

- **A reversão (D-68)**: o property-based testing achou um defeito real numa regra, e
  você escolheu voltar ao comportamento original porque ele cumpria o requisito
  escrito. Um achado técnico correto não decide sozinho uma questão de produto.
- **A contradição de três pontas**: três documentos aprovados diziam coisas
  incompatíveis sobre parcelamento. Cada um coerente sozinho; a incoerência não tinha
  dono, e só apareceu na primeira tarefa que consumiu os três juntos.
- **O front**: construído depois do ciclo, em 2 dias, consumindo a API. Mostra que o
  contrato aguentou.
