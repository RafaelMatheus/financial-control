-- U3 Credito: cartoes, faturas, compras parceladas, contas a pagar e recorrencia
-- (RF-23 a RF-35, RF-55 a RF-67, RF-94 a RF-96, RNF-04, D-01).
--
-- Seis tabelas novas. A tabela `gasto` de U2 NAO e alterada: `cartao_id` e
-- `competencia` ja nasceram nulaveis la, por decisao da Units Generation, e
-- passam a ser preenchidos. E o ALTER TABLE que nao precisou existir.

CREATE TABLE cartao (
    id             UUID         NOT NULL,
    apelido        VARCHAR(80)  NOT NULL,
    -- Qualquer dia de 1 a 31 e aceito (RN-K01). A queda para o ultimo dia do mes
    -- acontece na aplicacao, na CalculadoraDeCompetencia (D-69) — recusar dias
    -- acima de 28 eliminaria o caso de borda ao custo de recusar cartoes reais.
    dia_fechamento INTEGER      NOT NULL,
    dia_vencimento INTEGER      NOT NULL,
    dono_id        UUID         NOT NULL,
    escopo         VARCHAR(20)  NOT NULL,
    grupo_id       UUID,
    encerrado_em   TIMESTAMP(6),
    criado_em      TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_cartao PRIMARY KEY (id),
    CONSTRAINT fk_cartao_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_cartao_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT ck_cartao_dias CHECK (
        dia_fechamento BETWEEN 1 AND 31 AND dia_vencimento BETWEEN 1 AND 31
    ),
    CONSTRAINT ck_cartao_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

CREATE INDEX ix_cartao_dono  ON cartao (dono_id);
CREATE INDEX ix_cartao_grupo ON cartao (grupo_id);

-- Consulta do job de fechamento (D-71): cartoes ativos.
CREATE INDEX ix_cartao_ativo ON cartao (encerrado_em) WHERE encerrado_em IS NULL;

CREATE TABLE fatura (
    id             UUID         NOT NULL,
    cartao_id      UUID         NOT NULL,
    -- Formato ISO AAAA-MM, que ordena lexicograficamente.
    competencia    VARCHAR(7)   NOT NULL,
    -- SEM coluna valor_total: D-75 tornou o total uma agregacao sobre os
    -- lancamentos da competencia. A invariante 'total = soma' dependia de oito
    -- caminhos de escrita lembrarem de recalcular, e esquecer um faria o numero
    -- divergir em silencio. Derivado, ela e verdadeira por construcao.
    data_fechamento DATE,
    data_vencimento DATE        NOT NULL,
    conta_a_pagar_id UUID,
    criado_em      TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_fatura PRIMARY KEY (id),
    CONSTRAINT fk_fatura_cartao FOREIGN KEY (cartao_id) REFERENCES cartao (id),
    -- RN-F02: uma fatura por cartao e competencia.
    CONSTRAINT uk_fatura_cartao_competencia UNIQUE (cartao_id, competencia),
    -- So ha conta a pagar depois do fechamento (RN-A05).
    CONSTRAINT ck_fatura_conta CHECK (
        conta_a_pagar_id IS NULL OR data_fechamento IS NOT NULL
    )
);

CREATE INDEX ix_fatura_cartao ON fatura (cartao_id, competencia);

-- Consulta do job: faturas ainda abertas.
CREATE INDEX ix_fatura_aberta ON fatura (data_fechamento) WHERE data_fechamento IS NULL;

CREATE TABLE compra (
    id              UUID           NOT NULL,
    descricao       VARCHAR(200)   NOT NULL,
    -- O usuario informa o TOTAL (D-67), e o sistema divide. A Application Design
    -- desenhou o inverso; a inversao esta registrada, e RF-29/H-27 ficaram
    -- desatualizados por decisao.
    valor_total     NUMERIC(15, 2) NOT NULL,
    numero_parcelas INTEGER        NOT NULL,
    data_compra     DATE           NOT NULL,
    cartao_id       UUID           NOT NULL,
    categoria_id    UUID           NOT NULL,
    dono_id         UUID           NOT NULL,
    escopo          VARCHAR(20)    NOT NULL,
    grupo_id        UUID,
    criado_em       TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_compra PRIMARY KEY (id),
    CONSTRAINT fk_compra_cartao FOREIGN KEY (cartao_id) REFERENCES cartao (id),
    CONSTRAINT fk_compra_dono   FOREIGN KEY (dono_id)   REFERENCES usuario (id),
    CONSTRAINT fk_compra_grupo  FOREIGN KEY (grupo_id)  REFERENCES grupo (id),
    -- RESTRICT como em U2: RF-37 existe para nao perder a classificacao do
    -- historico, e cascata faria o oposto.
    CONSTRAINT fk_compra_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    CONSTRAINT ck_compra_valor    CHECK (valor_total > 0),
    CONSTRAINT ck_compra_parcelas CHECK (numero_parcelas >= 1),
    CONSTRAINT ck_compra_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

CREATE INDEX ix_compra_dono      ON compra (dono_id, data_compra);
CREATE INDEX ix_compra_grupo     ON compra (grupo_id, data_compra);
CREATE INDEX ix_compra_cartao    ON compra (cartao_id);
CREATE INDEX ix_compra_categoria ON compra (categoria_id);

CREATE TABLE parcela (
    id          UUID           NOT NULL,
    compra_id   UUID           NOT NULL,
    numero      INTEGER        NOT NULL,
    valor       NUMERIC(15, 2) NOT NULL,
    competencia VARCHAR(7)     NOT NULL,
    CONSTRAINT pk_parcela PRIMARY KEY (id),
    -- CASCADE, e nao RESTRICT: parcela nao existe sem compra (RF-34, RN-P07).
    --
    -- A direcao oposta a de compra.categoria, e as duas estao certas: a cascata
    -- segue a POSSE DO AGREGADO, nao uma preferencia de estilo. Parcela pertence
    -- a Compra; Categoria nao pertence a ninguem.
    CONSTRAINT fk_parcela_compra FOREIGN KEY (compra_id)
        REFERENCES compra (id) ON DELETE CASCADE,
    -- RN-P05: numero unico dentro da compra, sem lacuna nem repeticao.
    CONSTRAINT uk_parcela_numero UNIQUE (compra_id, numero),
    CONSTRAINT ck_parcela_numero CHECK (numero >= 1)
);

-- Consulta que monta a fatura: parcelas de uma competencia.
CREATE INDEX ix_parcela_competencia ON parcela (competencia);
CREATE INDEX ix_parcela_compra      ON parcela (compra_id);

CREATE TABLE conta_recorrente (
    id             UUID           NOT NULL,
    descricao      VARCHAR(200)   NOT NULL,
    -- NAO muda quando uma ocorrencia e ajustada no pagamento (RN-R03, H-48).
    valor_base     NUMERIC(15, 2) NOT NULL,
    dia_vencimento INTEGER        NOT NULL,
    frequencia     VARCHAR(20)    NOT NULL,
    tipo           VARCHAR(30)    NOT NULL,
    categoria_id   UUID           NOT NULL,
    dono_id        UUID           NOT NULL,
    escopo         VARCHAR(20)    NOT NULL,
    grupo_id       UUID,
    inicio_em      VARCHAR(7)     NOT NULL,
    encerrada_em   VARCHAR(7),
    criado_em      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_conta_recorrente PRIMARY KEY (id),
    CONSTRAINT fk_recorrente_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_recorrente_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT fk_recorrente_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    CONSTRAINT ck_recorrente_dia   CHECK (dia_vencimento BETWEEN 1 AND 31),
    CONSTRAINT ck_recorrente_valor CHECK (valor_base > 0),
    CONSTRAINT ck_recorrente_fim   CHECK (encerrada_em IS NULL OR encerrada_em >= inicio_em),
    CONSTRAINT ck_recorrente_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

CREATE INDEX ix_recorrente_dono  ON conta_recorrente (dono_id);
CREATE INDEX ix_recorrente_grupo ON conta_recorrente (grupo_id);

CREATE TABLE conta_a_pagar (
    id              UUID           NOT NULL,
    descricao       VARCHAR(200)   NOT NULL,
    -- PERSISTIDO, e deliberadamente. Diferente do total da fatura (D-75), este
    -- numero e FATO HISTORICO: o que foi cobrado no fechamento. Se ele derivasse,
    -- corrigir um gasto de marco mudaria o valor de uma conta paga em abril, e o
    -- historico deixaria de bater com o extrato do banco.
    valor           NUMERIC(15, 2) NOT NULL,
    data_vencimento DATE           NOT NULL,
    tipo            VARCHAR(30)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    data_pagamento  DATE,
    -- NULAVEL, e so para a conta derivada de fatura. Uma fatura MISTURA
    -- categorias — forcar uma seria inventar dado. O CHECK abaixo garante que a
    -- ausencia so acontece onde ela faz sentido.
    categoria_id    UUID,
    dono_id         UUID           NOT NULL,
    escopo          VARCHAR(20)    NOT NULL,
    grupo_id        UUID,
    origem_fatura_id     UUID,
    origem_recorrente_id UUID,
    competencia_recorrencia VARCHAR(7),
    criado_em       TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_conta_a_pagar PRIMARY KEY (id),
    CONSTRAINT fk_conta_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_conta_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT fk_conta_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    CONSTRAINT fk_conta_fatura FOREIGN KEY (origem_fatura_id) REFERENCES fatura (id),
    CONSTRAINT fk_conta_recorrente FOREIGN KEY (origem_recorrente_id)
        REFERENCES conta_recorrente (id),
    CONSTRAINT ck_conta_valor CHECK (valor > 0),
    -- Toda conta tem categoria, EXCETO a derivada de fatura (RN-A01).
    CONSTRAINT ck_conta_categoria_obrigatoria CHECK (
        categoria_id IS NOT NULL OR origem_fatura_id IS NOT NULL
    ),
    -- RN-A03: status e data de pagamento andam juntos, nos dois sentidos.
    CONSTRAINT ck_conta_pagamento CHECK (
        (status = 'PAGA'      AND data_pagamento IS NOT NULL) OR
        (status = 'EM_ABERTO' AND data_pagamento IS NULL)
    ),
    -- RN-A09: uma conta tem NO MAXIMO uma origem. Fatura e recorrencia nunca
    -- juntas.
    CONSTRAINT ck_conta_origem CHECK (
        origem_fatura_id IS NULL OR origem_recorrente_id IS NULL
    ),
    -- A competencia da ocorrencia so existe se ha origem recorrente.
    CONSTRAINT ck_conta_competencia CHECK (
        (origem_recorrente_id IS NULL     AND competencia_recorrencia IS NULL) OR
        (origem_recorrente_id IS NOT NULL AND competencia_recorrencia IS NOT NULL)
    ),
    CONSTRAINT ck_conta_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

-- RN-R04: UMA ocorrencia por (recorrente, competencia).
--
-- TERCEIRO indice unico parcial do projeto, e a garantia real de que duas
-- materializacoes simultaneas nao criam duas linhas (D-72). Como os outros dois,
-- e PostgreSQL puro e INVISIVEL ao `ddl-auto: validate` — vive so aqui, e a
-- invariante que ele protege precisa de teste de concorrencia proprio.
CREATE UNIQUE INDEX uk_conta_ocorrencia
    ON conta_a_pagar (origem_recorrente_id, competencia_recorrencia)
    WHERE origem_recorrente_id IS NOT NULL;

-- RN-A05: uma conta por fatura fechada.
CREATE UNIQUE INDEX uk_conta_fatura
    ON conta_a_pagar (origem_fatura_id)
    WHERE origem_fatura_id IS NOT NULL;

-- As duas metades do predicado de RN-V01, cada uma com a data de vencimento —
-- e toda consulta de vencimentos passa por elas (RN-A04, RN-A08).
CREATE INDEX ix_conta_dono_venc  ON conta_a_pagar (dono_id, data_vencimento);
CREATE INDEX ix_conta_grupo_venc ON conta_a_pagar (grupo_id, data_vencimento);
CREATE INDEX ix_conta_categoria  ON conta_a_pagar (categoria_id);
