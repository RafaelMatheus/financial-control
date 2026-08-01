-- U2 Lancamentos: categoria e gasto a vista (RF-11, RF-16 a RF-22, RF-36 a RF-38,
-- RF-97, RNF-04, D-01).
--
-- Nao toca em nenhuma tabela de U1. Duas tabelas novas, e nada mais.
--
-- Convencao: V{n}__{descricao}.sql, sequencial, e migration aplicada NUNCA e
-- editada.

CREATE TABLE categoria (
    id         UUID         NOT NULL,
    nome       VARCHAR(80)  NOT NULL,
    -- Forma normalizada (trim + minusculas). E sobre ela que a unicidade vale:
    -- "Mercado" e "  mercado " sao a mesma categoria (RN-C02), pela mesma razao
    -- que RN-U01 normaliza e-mail — o usuario nao percebe a diferenca, e o
    -- sistema nao deveria criar duas linhas por ela.
    nome_chave VARCHAR(80)  NOT NULL,
    dono_id    UUID         NOT NULL,
    escopo     VARCHAR(20)  NOT NULL,
    grupo_id   UUID,
    criado_em  TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_categoria PRIMARY KEY (id),
    CONSTRAINT fk_categoria_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_categoria_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    -- Bicondicional escopo-grupo, nos dois sentidos. A mesma invariante esta no
    -- construtor de `Categoria`; aqui ela cobre carga de dados e migration
    -- futura, que nao passam pelo construtor.
    CONSTRAINT ck_categoria_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

-- RN-C02 tem DUAS formas, e por isso sao dois indices.
--
-- Nas PESSOAIS o nome e unico por DONO: Rafael e Ana podem ter, cada um, a sua
-- "Mercado" pessoal.
--
-- Nas de GRUPO o nome e unico por GRUPO, de quem quer que seja o dono. Sem isto,
-- Ana e Rafael criariam duas "Mercado" no mesmo grupo, com UUIDs diferentes, e o
-- total por categoria do grupo mostraria duas linhas com o mesmo rotulo — que e
-- exatamente o problema que D-54 veio resolver.
--
-- ATENCAO: indice unico PARCIAL e PostgreSQL puro e NAO e expressavel como
-- constraint JPA. `ddl-auto: validate` nao o verifica. Vive apenas aqui, como o
-- uk_membro_grupo_ativo de V1, e precisa de teste de integracao proprio com
-- insercao concorrente.
CREATE UNIQUE INDEX uk_categoria_pessoal
    ON categoria (dono_id, nome_chave)
    WHERE escopo = 'PESSOAL';

CREATE UNIQUE INDEX uk_categoria_grupo
    ON categoria (grupo_id, nome_chave)
    WHERE escopo = 'GRUPO';

-- Predicado de visibilidade de RN-V01: as duas metades do OU.
CREATE INDEX ix_categoria_dono  ON categoria (dono_id);
CREATE INDEX ix_categoria_grupo ON categoria (grupo_id);

CREATE TABLE gasto (
    id           UUID           NOT NULL,
    descricao    VARCHAR(200)   NOT NULL,
    -- NUMERIC(15,2), nunca double precision. A escala 2 aqui e o que o
    -- `ddl-auto: validate` confere contra o mapeamento — e a unica protecao que
    -- sobra contra as duas aritmeticas monetarias divergirem (D-64), depois de a
    -- decisao ter dispensado o teste de comparacao.
    valor        NUMERIC(15, 2) NOT NULL,
    data         DATE           NOT NULL,
    categoria_id UUID           NOT NULL,
    dono_id      UUID           NOT NULL,
    escopo       VARCHAR(20)    NOT NULL,
    grupo_id     UUID,
    -- Sempre nulos ate U3 (RN-L10). Nascem aqui para que a integracao com cartao
    -- nao precise de ALTER TABLE numa tabela que ja tera dados.
    cartao_id    UUID,
    competencia  VARCHAR(7),
    criado_em    TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_gasto PRIMARY KEY (id),
    CONSTRAINT fk_gasto_dono  FOREIGN KEY (dono_id)  REFERENCES usuario (id),
    CONSTRAINT fk_gasto_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    -- RESTRICT, e NAO CASCADE. Em cascata, excluir uma categoria apagaria
    -- silenciosamente os gastos — o oposto exato de RF-37, cuja razao de existir
    -- e nao perder a classificacao do historico. O banco vira a ultima linha de
    -- defesa da regra: mesmo que o servico falhe em contar, o RESTRICT recusa.
    CONSTRAINT fk_gasto_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    -- RN-L01. Duplica o que o construtor de `Gasto` ja garante, de proposito: a
    -- invariante monetaria e a que menos pode falhar, e uma carga de dados fora
    -- da aplicacao nao passa pelo construtor.
    CONSTRAINT ck_gasto_valor CHECK (valor > 0),
    CONSTRAINT ck_gasto_escopo CHECK (
        (escopo = 'GRUPO'   AND grupo_id IS NOT NULL) OR
        (escopo = 'PESSOAL' AND grupo_id IS NULL)
    )
);

-- Os dois indices sao exatamente as duas metades do predicado de RN-V01, cada
-- uma combinada com a data — e toda consulta desta unidade passa por eles.
CREATE INDEX ix_gasto_dono_data  ON gasto (dono_id, data);
CREATE INDEX ix_gasto_grupo_data ON gasto (grupo_id, data);

-- RN-C05: a contagem de lancamentos vinculados, feita a cada tentativa de
-- excluir categoria.
CREATE INDEX ix_gasto_categoria ON gasto (categoria_id);
