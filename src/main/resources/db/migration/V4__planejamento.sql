-- U4 Planejamento: receitas, orcamento por categoria e objetivos de investimento
-- (RF-39 a RF-44, RF-68 a RF-77, RNF-04, D-01).
--
-- A ULTIMA migration do ciclo. Quatro tabelas novas; nenhuma tabela de U1, U2 ou
-- U3 e alterada.

CREATE TABLE receita (
    id        UUID           NOT NULL,
    descricao VARCHAR(200)   NOT NULL,
    valor     NUMERIC(15, 2) NOT NULL,
    data      DATE           NOT NULL,
    dono_id   UUID           NOT NULL,
    criado_em TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_receita PRIMARY KEY (id),
    CONSTRAINT fk_receita_dono FOREIGN KEY (dono_id) REFERENCES usuario (id),
    -- Receita negativa e gasto, e gasto tem tabela propria.
    CONSTRAINT ck_receita_valor CHECK (valor > 0)
);

-- SEM colunas escopo e grupo_id, e a ausencia e a REGRA (P-05, RN-RC02).
--
-- Receita e a unica entidade com dono do sistema sem escopo: nao existe "receita
-- da casa". A consequencia nao e de modelagem, e de produto — NAO HA RENDA
-- FAMILIAR, e por isso o balanco e sempre pessoal. Renda compartilhada seria
-- requisito novo, nao ajuste.
CREATE INDEX ix_receita_dono ON receita (dono_id, data);

CREATE TABLE orcamento (
    id           UUID           NOT NULL,
    categoria_id UUID           NOT NULL,
    competencia  VARCHAR(7)     NOT NULL,
    valor_teto   NUMERIC(15, 2) NOT NULL,
    -- D-77, J-02: cada orcamento declara se o realizado conta pela DATA DA
    -- COMPRA ou pela COMPETENCIA da fatura. Para gasto a vista as duas
    -- coincidem; a escolha so importa onde ha cartao.
    base         VARCHAR(20)    NOT NULL,
    dono_id      UUID           NOT NULL,
    escopo       VARCHAR(20)    NOT NULL,
    grupo_id     UUID,
    criado_em    TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_orcamento PRIMARY KEY (id),
    CONSTRAINT fk_orcamento_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_orcamento_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    -- RESTRICT, como em U2 e U3: RF-37 existe para nao perder a classificacao.
    CONSTRAINT fk_orcamento_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    -- ZERO e teto VALIDO: "nao quero gastar nada nesta categoria este mes"
    -- (RN-O02). Remover o orcamento e operacao distinta.
    CONSTRAINT ck_orcamento_teto CHECK (valor_teto >= 0),
    CONSTRAINT ck_orcamento_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

-- RN-O01: um teto por categoria, competencia E ESCOPO.
--
-- A chave inclui escopo e grupo, e sem isso D-78 seria inexpressavel: nao daria
-- para ter um teto pessoal de "Mercado" em agosto e um teto da casa para a mesma
-- categoria no mesmo mes.
--
-- Indice unico PARCIAL em duas formas, porque grupo_id nulo nao participa de
-- UNIQUE comum no PostgreSQL — dois tetos pessoais da mesma categoria passariam.
CREATE UNIQUE INDEX uk_orcamento_pessoal
    ON orcamento (dono_id, categoria_id, competencia)
    WHERE escopo = 'PESSOAL';

CREATE UNIQUE INDEX uk_orcamento_grupo
    ON orcamento (grupo_id, categoria_id, competencia)
    WHERE escopo = 'GRUPO';

CREATE INDEX ix_orcamento_dono  ON orcamento (dono_id, competencia);
CREATE INDEX ix_orcamento_grupo ON orcamento (grupo_id, competencia);

CREATE TABLE objetivo_investimento (
    id          UUID           NOT NULL,
    nome        VARCHAR(120)   NOT NULL,
    -- Meta e prazo OPCIONAIS (RF-73, RF-74): "Geral" pode nao ter alvo.
    meta        NUMERIC(15, 2),
    prazo_alvo  DATE,
    -- PERSISTIDO, e deliberadamente. Diferente de total_aportado, que e derivado
    -- (D-82), este e FATO DECLARADO PELO USUARIO: quanto o dinheiro vale hoje.
    -- O sistema nao tem cotacao, nao tem integracao com corretora e nao pretende
    -- ter — e e por isso que RF-71 existe.
    saldo_atual NUMERIC(15, 2) NOT NULL,
    dono_id     UUID           NOT NULL,
    escopo      VARCHAR(20)    NOT NULL,
    grupo_id    UUID,
    criado_em   TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_objetivo PRIMARY KEY (id),
    CONSTRAINT fk_objetivo_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_objetivo_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT ck_objetivo_meta CHECK (meta IS NULL OR meta > 0),
    CONSTRAINT ck_objetivo_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

-- SEM coluna total_aportado (D-82): e a soma dos aportes, calculada na leitura.
-- SEM coluna rendimento: e saldo_atual - total_aportado, derivado dos dois.
--
-- O criterio consolidado do ciclo, em uma frase: SE O NUMERO E UMA SOMA,
-- CALCULE; SE E UM FATO, GUARDE.

CREATE INDEX ix_objetivo_dono  ON objetivo_investimento (dono_id);
CREATE INDEX ix_objetivo_grupo ON objetivo_investimento (grupo_id);

CREATE TABLE aporte (
    id          UUID           NOT NULL,
    objetivo_id UUID           NOT NULL,
    valor       NUMERIC(15, 2) NOT NULL,
    data        DATE           NOT NULL,
    -- RF-75: cada aporte registra o seu dono. Num objetivo de grupo todos
    -- aportam, e o saldo e a soma de todos — SEM RATEIO (D-27).
    dono_id     UUID           NOT NULL,
    criado_em   TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_aporte PRIMARY KEY (id),
    -- CASCADE: aporte nao existe sem objetivo. A mesma direcao de parcela para
    -- compra em U3, e pelo mesmo motivo — a cascata segue a POSSE DO AGREGADO.
    CONSTRAINT fk_aporte_objetivo FOREIGN KEY (objetivo_id)
        REFERENCES objetivo_investimento (id) ON DELETE CASCADE,
    CONSTRAINT fk_aporte_dono FOREIGN KEY (dono_id) REFERENCES usuario (id),
    -- Resgate NAO e aporte negativo: ajusta-se o saldo_atual.
    CONSTRAINT ck_aporte_valor CHECK (valor > 0)
);

CREATE INDEX ix_aporte_objetivo ON aporte (objetivo_id);

-- RF-76, D-18: o aporte conta como gasto no balanco do periodo. Este indice e o
-- da consulta que o balanco faz.
CREATE INDEX ix_aporte_dono_data ON aporte (dono_id, data);
