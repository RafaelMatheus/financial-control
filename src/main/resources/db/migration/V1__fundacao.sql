-- U1 Fundacao: identidade e visibilidade (RF-01 a RF-10, RNF-04, D-01).
--
-- Primeira migration do projeto. Resolve o debito bloqueante apontado na
-- engenharia reversa: `ddl-auto: validate` sem ferramenta de migration faria a
-- aplicacao deixar de subir assim que a primeira @Entity existisse.
--
-- Convencao: V{n}__{descricao}.sql, sequencial, e migration aplicada NUNCA e
-- editada.

CREATE TABLE usuario (
    id         UUID         NOT NULL,
    email      VARCHAR(320) NOT NULL,
    senha_hash VARCHAR(72)  NOT NULL,
    nome       VARCHAR(120) NOT NULL,
    criado_em  TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    -- Garantia real de RN-U01. A verificacao na aplicacao existe para dar boa
    -- mensagem; duas requisicoes simultaneas so sao barradas aqui.
    -- O e-mail chega sempre normalizado (D-46), entao a restricao simples basta.
    CONSTRAINT uk_usuario_email UNIQUE (email)
);

-- 320 = 64 (parte local) + 1 (@) + 255 (dominio), o maximo do RFC 5321.
-- 72 em senha_hash: o BCrypt produz 60 caracteres; a folga cobre prefixo de
-- versao caso o algoritmo mude.

CREATE TABLE grupo (
    id        UUID         NOT NULL,
    nome      VARCHAR(120) NOT NULL,
    criado_em TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_grupo PRIMARY KEY (id)
);

-- Sem coluna de criador, de proposito: RF-06 nega hierarquia, e uma coluna
-- `criador_id` viraria autoridade na primeira vez que alguem precisasse de uma.
-- Sem unicidade de nome: duas casas podem se chamar "Apartamento 42".

CREATE TABLE membro_grupo (
    id         UUID         NOT NULL,
    grupo_id   UUID         NOT NULL,
    usuario_id UUID         NOT NULL,
    entrou_em  TIMESTAMP(6) NOT NULL,
    saiu_em    TIMESTAMP(6),
    CONSTRAINT pk_membro_grupo PRIMARY KEY (id),
    CONSTRAINT fk_membro_grupo_grupo   FOREIGN KEY (grupo_id)   REFERENCES grupo (id),
    CONSTRAINT fk_membro_grupo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_membro_grupo_saida CHECK (saiu_em IS NULL OR saiu_em >= entrou_em)
);

-- INVARIANTE CENTRAL de RN-G05: no maximo UMA associacao ativa por par.
--
-- Indice unico PARCIAL — so vale onde saiu_em IS NULL. E o que permite o
-- historico de participacoes de D-45: a mesma pessoa pode ter varias linhas no
-- mesmo grupo, desde que so uma esteja aberta.
--
-- ATENCAO: isto e PostgreSQL puro e NAO e expressavel como constraint JPA.
-- `ddl-auto: validate` nao o verifica. Ele vive apenas aqui, e a invariante que
-- ele protege precisa de teste de integracao proprio, com insercao concorrente.
CREATE UNIQUE INDEX uk_membro_grupo_ativo
    ON membro_grupo (grupo_id, usuario_id)
    WHERE saiu_em IS NULL;

-- Consulta de gruposDoUsuario(), executada em TODA requisicao autenticada.
CREATE INDEX ix_membro_grupo_usuario ON membro_grupo (usuario_id);

-- Listagem de membros de um grupo.
CREATE INDEX ix_membro_grupo_grupo ON membro_grupo (grupo_id);
