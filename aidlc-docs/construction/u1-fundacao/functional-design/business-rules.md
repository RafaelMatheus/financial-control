# Regras de Negócio — U1 Fundação

Cada regra tem identificador, enunciado verificável e o requisito de origem. As que produzem erro
declaram o código que o `ErroHandler` devolve.

---

## 1. `usuario`

### RN-U01 — E-mail é único, na forma normalizada
Antes de qualquer comparação ou gravação, o e-mail sofre `trim` e conversão para minúsculas.
A unicidade vale sobre esse valor. `  Rafael@X.com ` e `rafael@x.com` são a mesma conta (D-46).

**Origem**: RF-01, H-01 · **Erro**: `EMAIL_JA_CADASTRADO` (409)

### RN-U02 — E-mail precisa ter formato válido
Rejeitado antes de tocar o banco.

**Origem**: RF-01, H-01 · **Erro**: `EMAIL_INVALIDO` (400)

### RN-U03 — A credencial nunca trafega nem repousa em texto claro
A senha é transformada em hash na entrada e nunca é devolvida por nenhuma operação. Nenhum DTO de
resposta contém o campo. Nenhum log o registra.

**Origem**: RF-01, H-01 · **Verificação**: revisão de todo DTO de saída de `usuario`

### RN-U04 — Falha de autenticação não revela a causa
Resposta idêntica para e-mail inexistente e para senha errada. Nem a mensagem, nem o código, nem o
tempo de resposta devem permitir distinguir os dois casos.

**Origem**: RF-02, H-02 · **Erro**: `CREDENCIAIS_INVALIDAS` (401)

> O tempo de resposta é parte da regra. Retornar cedo quando o e-mail não existe cria um oráculo de
> enumeração de contas: quem mede a latência descobre quais e-mails estão cadastrados. A
> implementação deve calcular o hash mesmo quando o usuário não existe.

### RN-U05 — Operação sobre dado financeiro exige autenticação
Sem contexto de usuário, a operação é rejeitada antes de qualquer lógica de negócio.

**Origem**: RF-02, H-02 · **Erro**: `NAO_AUTENTICADO` (401)

### RN-U06 — Perfil é próprio e intransferível
`consultarPerfil` e `atualizarPerfil` operam **sempre** sobre `contexto.usuarioAtual()`. Não existe
parâmetro de identificador de usuário nessas operações.

**Origem**: RF-05, H-04

> A regra está expressa na **assinatura**, não em validação. Um método que aceitasse `usuarioId` e
> depois verificasse se é o próprio seria uma verificação que alguém pode esquecer; um método que
> não aceita o parâmetro não tem como errar.

### RN-U07 — Apenas `nome` é editável
`email`, `senhaHash`, `id` e `criadoEm` não mudam por `atualizarPerfil`.

**Origem**: RF-05

---

## 2. `grupo`

### RN-G01 — Nome de grupo é obrigatório e não vazio
Após `trim`, precisa ter ao menos um caractere. **Não** há unicidade: dois grupos podem ter o mesmo
nome, inclusive para o mesmo usuário.

**Origem**: RF-06, H-05 · **Erro**: `NOME_OBRIGATORIO` (400)

### RN-G02 — Não há hierarquia entre membros
Qualquer membro ativo pode renomear o grupo, adicionar membro e remover membro — inclusive remover
quem o adicionou. Quem criou não tem privilégio algum.

**Origem**: RF-06, RF-08, H-05, H-06

### RN-G03 — Só membro ativo opera sobre o grupo
Toda operação de `grupo` exige que `contexto.usuarioAtual()` tenha associação ativa nele. Quem nunca
foi membro e quem já saiu recebem a mesma resposta.

**Origem**: RF-08, RNF-05 · **Erro**: `GRUPO_NAO_ENCONTRADO` (404)

> **404, não 403.** Responder "proibido" confirma que o grupo existe, o que já é vazamento: permite
> descobrir identificadores válidos por tentativa. Para quem não é membro, o grupo não existe.

### RN-G04 — Membro adicionado precisa existir
Adicionar usuário inexistente é erro de validação, não silêncio.

**Origem**: RF-08, H-06, E-07 · **Erro**: `USUARIO_NAO_ENCONTRADO` (404)

### RN-G05 — Não há associação ativa duplicada
Adicionar quem já é membro ativo é rejeitado. Garantido no domínio **e** por índice único parcial no
banco, para resistir a requisições concorrentes.

**Origem**: RF-08 · **Erro**: `JA_E_MEMBRO` (409)

### RN-G06 — Sair encerra a associação, não a apaga
`saiuEm` recebe o instante da saída. A linha permanece. Os lançamentos de que o usuário é dono
continuam existindo, inalterados, e continuam somando no total do grupo.

**Origem**: RF-10, H-08, E-05

### RN-G07 — Reentrada cria associação nova
Uma associação encerrada nunca é reaberta. Voltar ao grupo insere uma linha nova, com `entrouEm`
atual e `saiuEm` nulo (D-45).

**Origem**: RF-08, H-06

### RN-G08 — Grupo sem membros continua existindo
A saída do último membro é permitida e não dispara exclusão. O grupo fica invisível para todos até
que alguém seja adicionado — o que só pode acontecer por outra via, já que RN-G03 exige ser membro
para operar (D-47).

**Origem**: RF-10 · **Consequência registrada**: ver §5

### RN-G09 — Membro novo enxerga todo o histórico
Quem entra hoje enxerga os lançamentos de escopo GRUPO anteriores à entrada. `entrouEm` **não**
participa do predicado de visibilidade.

**Origem**: RF-09, H-07, E-10, D-13

---

## 3. Visibilidade — a regra transversal

### RN-V01 — Todo dado é filtrado pelo predicado de visibilidade
Nenhuma consulta de nenhum componente do sistema retorna registro sem aplicar:

```
dono == usuarioAtual   OU   (escopo == GRUPO   E   grupoId ∈ gruposDoUsuario)
```

**Origem**: RF-03, RF-04, RNF-05, H-03

### RN-V02 — `gruposDoUsuario` considera apenas associação ativa
Grupo de que o usuário saiu não entra no conjunto. É o corte total de D-44: ao sair, perde-se a
visibilidade inclusive do passado.

**Origem**: RF-10, H-08, D-44

### RN-V03 — Escopo PESSOAL é impenetrável
Lançamento pessoal é visível **apenas** ao dono, mesmo entre membros do mesmo grupo.

**Origem**: RF-04, H-03

### RN-V04 — Total pessoal e total de grupo nunca se somam
São grandezas distintas. Nenhuma resposta apresenta um número que misture as duas.

**Origem**: RF-97, D-28 · *(sem consumidor em U1; vale a partir de U2)*

---

## 4. Erros — taxonomia

`ErroHandler` devolve formato único (RNF-09):

```json
{
  "codigo": "EMAIL_JA_CADASTRADO",
  "mensagem": "Já existe uma conta com este e-mail.",
  "detalhes": [ { "campo": "email", "problema": "duplicado" } ]
}
```

| Código | HTTP | Regra |
|---|---|---|
| `EMAIL_INVALIDO` | 400 | RN-U02 |
| `NOME_OBRIGATORIO` | 400 | RN-G01 |
| `NAO_AUTENTICADO` | 401 | RN-U05 |
| `CREDENCIAIS_INVALIDAS` | 401 | RN-U04 |
| `GRUPO_NAO_ENCONTRADO` | 404 | RN-G03 |
| `USUARIO_NAO_ENCONTRADO` | 404 | RN-G04 |
| `EMAIL_JA_CADASTRADO` | 409 | RN-U01 |
| `JA_E_MEMBRO` | 409 | RN-G05 |

**Mensagem acionável** (RNF-09): diz o que fazer, não o que aconteceu internamente. "Já existe uma
conta com este e-mail" — não "violação de constraint uk_usuario_email".

---

## 5. Consequência registrada de RN-G08

Um grupo cujo último membro saiu fica **permanentemente inacessível**: ninguém o enxerga, e RN-G03
impede que qualquer um opere sobre ele para se adicionar de volta.

Não é defeito — é a combinação correta de D-47 (permitir grupo vazio) com RN-G03 (só membro opera).
Mas é um estado sem saída pela API, e vale estar escrito. Se algum dia o produto precisar de
"recuperar grupo abandonado", será requisito novo, não correção de bug.

---

## 6. Alvos de property-based testing 🔬

Extensão `testing/property-based` em modo **Parcial**: PBT-02, PBT-03, PBT-07, PBT-08 e PBT-09 são
bloqueantes.

| Alvo | Propriedade | Regra PBT |
|---|---|---|
| `Dinheiro.dividirEm` | `soma(dividirEm(n)) == valor`, exato | PBT-03 (invariante) |
| `Dinheiro.dividirEm` | partes diferem entre si em no máximo 0,01 | PBT-03 |
| `Dinheiro.de` / representação | ida e volta preserva o valor | PBT-02 (round-trip) |
| `Dinheiro.mais` / `menos` | associatividade e comutatividade da soma | PBT-03 |
| `Competencia.proxima`/`anterior` | ida e volta é identidade; atravessa a virada de ano | PBT-02 |

Gerador de `Dinheiro` (PBT-07) precisa cobrir: zero, negativos, valores com resíduo de divisão
(0,01 dividido em 3), e magnitudes altas o bastante para expor erro de ponto flutuante caso alguém
troque o tipo por engano.

Seed registrado no CI para reprodução (PBT-08) — `ci-app.yml` já faz isso.
