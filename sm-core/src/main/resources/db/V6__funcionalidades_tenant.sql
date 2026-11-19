-- =====================================================================
-- SM-CORE | Funcionalidades ativas por clube (tenant)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo, Entrega A (desenho 02-desenho.md, secção 3.1,
-- tarefa DBA-1; especificação R57-R64).
--
-- Objetivo:
--   Catálogo genérico de funcionalidades que o ADMIN de cada clube pode
--   ligar ou desligar, com registo de quem mudou e quando. A primeira
--   funcionalidade é o Micro Ciclo (MICRO_CICLO).
--
-- O que cria:
--   funcionalidade                    (catálogo)
--     - uq_funcionalidade_codigo      UNIQUE (codigo)
--     - chk_funcionalidade_estado_omissao  estado_omissao IN ('0','1')
--     - 1 linha: MICRO_CICLO, estado_omissao '0' (INSERT IGNORE)
--   funcionalidade_tenant             (estado por clube; tabela raiz,
--                                      por isso tem tenant_id)
--     - uq_funcionalidade_tenant      UNIQUE (tenant_id, id_funcionalidade)
--     - fk_funcionalidade_tenant_funcionalidade  -> funcionalidade(id)
--     - fk_funcionalidade_tenant_utilizador      -> utilizadores(id)
--     - chk_funcionalidade_tenant_estado         estado IN ('0','1')
--     - sem linhas: sem registo vale funcionalidade.estado_omissao,
--       ou seja, desligado nos 5 clubes (R58, CA41)
--   funcionalidade_tenant_historico   (auditoria, R64)
--     - idx_funcionalidade_tenant_historico (tenant_id, id_funcionalidade, data)
--     - fk_funcionalidade_tenant_historico_funcionalidade -> funcionalidade(id)
--     - fk_funcionalidade_tenant_historico_utilizador     -> utilizadores(id)
--     - chk_funcionalidade_tenant_historico_estado        estado IN ('0','1')
--   Todas sem cascade. INT(11) com sinal, como utilizadores.id (int(11)).
--   utf8mb4 / utf8mb4_uca1400_ai_ci, como performance_config.
--
-- Impacto nos dados existentes:
--   Nenhum. Só cria tabelas novas; não altera tabelas nem dados
--   existentes. A única linha inserida é a do catálogo, numa tabela nova.
--   O backend atual não usa estas tabelas.
--
-- Idempotência:
--   CREATE TABLE IF NOT EXISTS, com índices, FKs e CHECKs dentro do
--   CREATE (o CREATE é atómico: ou a tabela nasce com tudo, ou não nasce).
--   A linha MICRO_CICLO entra com INSERT IGNORE, protegida pelo UNIQUE de
--   codigo. Correr duas vezes não dá erro nem duplica.
--   Atenção: se alguma destas tabelas já existir com outra definição, o
--   IF NOT EXISTS ignora-a em silêncio (ver diagnóstico no fim).
--
-- Ordem de aplicação:
--   ANTES do backend da Entrega A (BK-2, FuncionalidadeHelper/WS).
--   Antes da V7 (a V7 referencia funcionalidade). Primeiro no schema de
--   testes, depois em produção. Pode ir sozinho: o backend atual continua
--   a funcionar.
--
-- Reversão (manual, pelo dono; só se nada depender destas tabelas, ou
-- seja, antes da V7). Ordem: filhas primeiro.
--   DROP TABLE funcionalidade_tenant_historico;
--   DROP TABLE funcionalidade_tenant;
--   DROP TABLE funcionalidade;
-- =====================================================================

-- A descrição tem acentos: garante que a sessão envia UTF-8.
SET NAMES utf8mb4;

-- =====================================================================
-- 1) funcionalidade (catálogo)
-- =====================================================================
CREATE TABLE IF NOT EXISTS funcionalidade (
  id             INT(11)      NOT NULL AUTO_INCREMENT,
  codigo         VARCHAR(50)  NOT NULL COMMENT 'Identificador estável usado pelo código (ex.: MICRO_CICLO)',
  nome           VARCHAR(100) NOT NULL COMMENT 'Nome mostrado na secção Funcionalidades',
  descricao      VARCHAR(255) NULL DEFAULT NULL COMMENT 'Descrição mostrada na secção Funcionalidades',
  estado_omissao VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '1 = ligado / 0 = desligado quando o clube não tem configuração',
  PRIMARY KEY (id),
  UNIQUE KEY uq_funcionalidade_codigo (codigo),
  CONSTRAINT chk_funcionalidade_estado_omissao CHECK (estado_omissao IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

INSERT IGNORE INTO funcionalidade (codigo, nome, descricao, estado_omissao)
VALUES ('MICRO_CICLO', 'Micro Ciclo',
        'Plano semanal de treinos: o administrador cria a semana, os treinadores aprovam os treinos e os atletas veem os seus treinos e podem avisar que não vão.',
        '0');

-- =====================================================================
-- 2) funcionalidade_tenant (estado por clube)
-- =====================================================================
CREATE TABLE IF NOT EXISTS funcionalidade_tenant (
  id                      INT(11)    NOT NULL AUTO_INCREMENT,
  id_funcionalidade       INT(11)    NOT NULL,
  tenant_id               INT(11)    NOT NULL,
  estado                  VARCHAR(1) NOT NULL COMMENT '1 = ligado / 0 = desligado',
  data_alteracao          DATETIME   NOT NULL COMMENT 'Última mudança de estado',
  id_utilizador_alteracao INT(11)    NOT NULL COMMENT 'Quem fez a última mudança (utilizadores.id)',
  PRIMARY KEY (id),
  UNIQUE KEY uq_funcionalidade_tenant (tenant_id, id_funcionalidade),
  CONSTRAINT fk_funcionalidade_tenant_funcionalidade FOREIGN KEY (id_funcionalidade) REFERENCES funcionalidade (id),
  CONSTRAINT fk_funcionalidade_tenant_utilizador FOREIGN KEY (id_utilizador_alteracao) REFERENCES utilizadores (id),
  CONSTRAINT chk_funcionalidade_tenant_estado CHECK (estado IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 3) funcionalidade_tenant_historico (auditoria)
-- =====================================================================
CREATE TABLE IF NOT EXISTS funcionalidade_tenant_historico (
  id                INT(11)    NOT NULL AUTO_INCREMENT,
  id_funcionalidade INT(11)    NOT NULL,
  tenant_id         INT(11)    NOT NULL,
  estado            VARCHAR(1) NOT NULL COMMENT 'Valor novo: 1 = ligado / 0 = desligado',
  data              DATETIME   NOT NULL,
  id_utilizador     INT(11)    NOT NULL COMMENT 'Quem mudou (utilizadores.id)',
  PRIMARY KEY (id),
  KEY idx_funcionalidade_tenant_historico (tenant_id, id_funcionalidade, data),
  CONSTRAINT fk_funcionalidade_tenant_historico_funcionalidade FOREIGN KEY (id_funcionalidade) REFERENCES funcionalidade (id),
  CONSTRAINT fk_funcionalidade_tenant_historico_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT chk_funcionalidade_tenant_historico_estado CHECK (estado IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 4) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'funcionalidade%';
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'funcionalidade%'
--  ORDER BY TABLE_NAME, ORDINAL_POSITION;
-- SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'funcionalidade%'
--  ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
--   FROM information_schema.TABLE_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'funcionalidade%';
-- SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'funcionalidade%';
-- SELECT * FROM funcionalidade;                  -- 1 linha MICRO_CICLO, '0'
-- SELECT COUNT(*) FROM funcionalidade_tenant;    -- 0 (CA41)
