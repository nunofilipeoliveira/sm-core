-- =====================================================================
-- SM-CORE | Balneários (catálogo do clube, regras de atribuição e
--           atribuições a treinos e jogos)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: balneários (desenho .melhorias/balnearios/02-desenho.md,
-- secção 4, tarefas BD-1 e BD-2). Depende da V16 (o passo 0 pára o
-- script se faltar). V1-V16 não mudam.
--
-- Objetivo:
--   - Guardar os balneários de cada clube (nome, ordem, libertação
--     própria, ativo/inativo).
--   - Guardar as regras do cálculo automático: tempos do clube,
--     preferências por escalão e dos adversários, prioridade dos escalões.
--   - Guardar os balneários atribuídos a cada treino e a cada lado
--     (equipa/adversário) de cada jogo, com a origem MANUAL/AUTOMATICA.
--
-- O que cria (6 tabelas novas, nenhuma alterada):
--   balneario (raiz, com tenant_id; sem FK de tenant: não há tabela de tenants)
--     - uq_balneario_tenant_nome (tenant_id, nome): a collation
--       utf8mb4_uca1400_ai_ci ignora maiúsculas e acentos
--       ("Balneário 1" = "balneario 1")
--     - fk_balneario_utilizador -> utilizadores(id)
--     - chk_balneario_estado, chk_balneario_libertacao
--   balneario_config (raiz, PK tenant_id; uma linha por clube; sem linha
--     = valores por omissão do código, 30/30/0)
--     - fk_balneario_config_utilizador -> utilizadores(id)
--     - chk_balneario_config_tempos
--   balneario_preferencia (filha de balneario; herda o tenant pelo join)
--     - fk_balneario_preferencia_balneario -> balneario(id) ON DELETE CASCADE
--     - fk_balneario_preferencia_escalao   -> escalao(id)
--     - chk_balneario_preferencia_tipo
--   balneario_prioridade (raiz, PK (tenant_id, id_escalao))
--     - fk_balneario_prioridade_escalao -> escalao(id)
--   treino_balneario (PK (id_treino, id_balneario))
--     - fk_treino_balneario_treino      -> treino(id) ON DELETE CASCADE
--     - fk_treino_balneario_balneario   -> balneario(id) (RESTRICT)
--     - fk_treino_balneario_utilizador  -> utilizadores(id)
--     - chk_treino_balneario_origem
--   jogo_balneario (PK (id_jogo, lado, id_balneario))
--     - fk_jogo_balneario_jogo -> jogo(id) ON DELETE CASCADE
--       (fora do CREATE; só se jogo.id for int(11) PK InnoDB; passo 7)
--     - fk_jogo_balneario_balneario   -> balneario(id) (RESTRICT)
--     - fk_jogo_balneario_utilizador  -> utilizadores(id)
--     - chk_jogo_balneario_lado, chk_jogo_balneario_origem
--
-- Verificações da BD-1 (feitas no dump .claude/docs/schema-testes.sql e
-- nos scripts V7-V16; NÃO na BD viva):
--   - jogo.id, escalao.id, utilizadores.id: int(11) NOT NULL
--     AUTO_INCREMENT, PK, InnoDB, utf8mb3 (o charset não conta numa FK de
--     inteiros, como na V7/V16).
--   - treino.id: INT(11) NOT NULL AUTO_INCREMENT, PK, InnoDB, utf8mb4
--     (V7, linha 144); nenhum script posterior altera treino.id.
--   - Nenhuma tabela balneario* nem *_balneario no dump nem em V1-V16.
--   Correr as QUERIES DE VERIFICAÇÃO abaixo no schema de testes ANTES de
--   aplicar, para confirmar que o dump ainda corresponde à BD.
--
-- QUERIES DE VERIFICAÇÃO (só leitura; correr à mão ANTES de aplicar):
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('jogo','treino','escalao','utilizadores','indisponibilidade_jogo');
--    -- 5 linhas, todas InnoDB
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id'
--    AND TABLE_NAME IN ('jogo','treino','escalao','utilizadores');
--    -- 4 linhas: int(11) NO PRI auto_increment
-- SELECT TABLE_NAME FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'balneario_prioridade','treino_balneario','jogo_balneario');
--    -- esperado: 0 linhas (se houver, o IF NOT EXISTS ignorava-as em silêncio)
-- SELECT CONSTRAINT_NAME, TABLE_NAME FROM information_schema.TABLE_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND (CONSTRAINT_NAME LIKE 'fk\_%balneario%' OR CONSTRAINT_NAME LIKE 'chk\_%balneario%'
--      OR CONSTRAINT_NAME LIKE 'uq\_balneario%');
--    -- esperado: 0 linhas (nomes de constraints são únicos no schema)
-- SELECT COLLATION_NAME FROM information_schema.COLLATIONS
--  WHERE COLLATION_NAME = 'utf8mb4_uca1400_ai_ci';
--    -- 1 linha
-- SELECT 'Balneário 1' = 'balneario 1' COLLATE utf8mb4_uca1400_ai_ci AS igual;
--    -- 1: a unicidade do nome vai ignorar maiúsculas e acentos
-- SELECT @@version, @@transaction_isolation;
--    -- 11.8.x; REPEATABLE-READ (o desenho, B9, assume este isolamento)
--
-- Impacto nos dados existentes:
--   Nenhum. Sem INSERT/UPDATE/DELETE. Não altera treino, jogo, escalao nem
--   utilizadores: só passam a ser referidas por FKs novas. As tabelas
--   novas ficam vazias; o backend atual não as lê.
--   Efeitos das FKs novas (só quando houver linhas):
--   - apagar um treino ou um jogo apaga as suas atribuições (CASCADE);
--   - um escalao, um utilizador ou um balneário referidos deixam de poder
--     ser apagados (RESTRICT), salvo as preferências, que vão com o
--     balneário (CASCADE).
--   Locks: criar FKs para treino, jogo, escalao e utilizadores toma um
--   lock de metadados curto nessas tabelas (espera por transações abertas
--   nelas). Tabelas novas e vazias: sem cópia de dados.
--
-- Idempotência:
--   - tabelas: CREATE TABLE IF NOT EXISTS (FKs e CHECK dentro do CREATE,
--     salvo a FK para jogo);
--   - fk_jogo_balneario_jogo: SQL dinâmico, só se o nome ainda não existir
--     em information_schema.TABLE_CONSTRAINTS (como V16, passo 4).
--   Correr duas vezes não dá erro. Atenção: se uma tabela já existir com
--   outra definição, o IF NOT EXISTS ignora-a em silêncio (ver o
--   diagnóstico no fim).
--   NÃO TESTADO: o agente não executa scripts. A BD-3 (correr duas vezes
--   no schema de testes) fica para o dono.
--
-- Ordem de aplicação:
--   DEPOIS da V16 e ANTES do backend dos balneários (que lê balneario em
--   cada getMicroCiclo, getTreino e gravarTreino, em todos os tenants com
--   Micro Ciclo; sem a V17 dá erro de SQL). Primeiro no schema de testes,
--   depois em produção. Pode ir sozinha: é inerte para o backend atual.
--
-- Reversão (manual, só pelo dono, e só DEPOIS de repor um backend
-- anterior; perde balneários, regras e atribuições; por esta ordem):
--   DROP TABLE jogo_balneario;
--   DROP TABLE treino_balneario;
--   DROP TABLE balneario_prioridade;
--   DROP TABLE balneario_preferencia;
--   DROP TABLE balneario_config;
--   DROP TABLE balneario;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V16 aplicada NESTE schema (indisponibilidade_jogo
--    InnoDB) e treino InnoDB (V7)
-- =====================================================================
-- Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V17_requer_V16' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de qualquer CREATE.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7/V16).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('indisponibilidade_jogo','treino')
      AND ENGINE = 'InnoDB') = 2,
  'DO 0',
  'SELECT 1 FROM ERRO_V17_requer_V16');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) balneario
-- =====================================================================
CREATE TABLE IF NOT EXISTS balneario (
  id                    INT(11)     NOT NULL AUTO_INCREMENT,
  tenant_id             INT(11)     NOT NULL COMMENT 'Clube (sem FK: não há tabela de tenants)',
  nome                  VARCHAR(30) NOT NULL COMMENT 'Único por clube, sem distinguir maiúsculas nem acentos (collation)',
  ordem                 INT(11)     NOT NULL DEFAULT 0 COMMENT 'Ordem do clube; empates por id',
  libertacao_min        INT(11)     NULL DEFAULT NULL COMMENT 'Minutos de libertação próprios; NULL = o global (balneario_config)',
  estado                VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1 ativo / 0 inativo',
  datacriacao           DATETIME    NOT NULL,
  id_utilizador_criacao INT(11)     NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id),
  UNIQUE KEY uq_balneario_tenant_nome (tenant_id, nome),
  KEY idx_balneario_utilizador (id_utilizador_criacao),
  CONSTRAINT fk_balneario_utilizador FOREIGN KEY (id_utilizador_criacao) REFERENCES utilizadores (id),
  CONSTRAINT chk_balneario_estado CHECK (estado IN ('0','1')),
  CONSTRAINT chk_balneario_libertacao CHECK (libertacao_min IS NULL OR libertacao_min BETWEEN 0 AND 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) balneario_config
-- =====================================================================
CREATE TABLE IF NOT EXISTS balneario_config (
  tenant_id               INT(11)  NOT NULL COMMENT 'Clube (sem FK); sem linha = omissão do código',
  antecedencia_min        INT(11)  NOT NULL COMMENT 'Minutos antes do treino',
  permanencia_min         INT(11)  NOT NULL COMMENT 'Minutos depois do treino/jogo',
  libertacao_min          INT(11)  NOT NULL COMMENT 'Minutos de libertação globais',
  data_alteracao          DATETIME NOT NULL,
  id_utilizador_alteracao INT(11)  NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (tenant_id),
  KEY idx_balneario_config_utilizador (id_utilizador_alteracao),
  CONSTRAINT fk_balneario_config_utilizador FOREIGN KEY (id_utilizador_alteracao) REFERENCES utilizadores (id),
  CONSTRAINT chk_balneario_config_tempos CHECK (antecedencia_min BETWEEN 0 AND 240
                                            AND permanencia_min BETWEEN 0 AND 240
                                            AND libertacao_min BETWEEN 0 AND 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 3) balneario_preferencia
-- =====================================================================
CREATE TABLE IF NOT EXISTS balneario_preferencia (
  id           INT(11)     NOT NULL AUTO_INCREMENT,
  id_balneario INT(11)     NOT NULL COMMENT 'balneario.id (o tenant vem do balneário)',
  tipo         VARCHAR(20) NOT NULL COMMENT 'ESCALAO / ADVERSARIO',
  id_escalao   INT(11)     NULL DEFAULT NULL COMMENT 'escalao.id; NULL só em ADVERSARIO',
  ordem        INT(11)     NOT NULL COMMENT 'Posição na lista (1..n)',
  PRIMARY KEY (id),
  KEY idx_balneario_preferencia_balneario (id_balneario),
  KEY idx_balneario_preferencia_escalao (id_escalao),
  CONSTRAINT fk_balneario_preferencia_balneario FOREIGN KEY (id_balneario) REFERENCES balneario (id) ON DELETE CASCADE,
  CONSTRAINT fk_balneario_preferencia_escalao FOREIGN KEY (id_escalao) REFERENCES escalao (id),
  CONSTRAINT chk_balneario_preferencia_tipo CHECK ((tipo = 'ESCALAO' AND id_escalao IS NOT NULL)
                                                OR (tipo = 'ADVERSARIO' AND id_escalao IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 4) balneario_prioridade
-- =====================================================================
CREATE TABLE IF NOT EXISTS balneario_prioridade (
  tenant_id  INT(11) NOT NULL COMMENT 'Clube (sem FK)',
  id_escalao INT(11) NOT NULL COMMENT 'escalao.id',
  ordem      INT(11) NOT NULL COMMENT '1 = mais prioritário',
  PRIMARY KEY (tenant_id, id_escalao),
  KEY idx_balneario_prioridade_escalao (id_escalao),
  CONSTRAINT fk_balneario_prioridade_escalao FOREIGN KEY (id_escalao) REFERENCES escalao (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 5) treino_balneario
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino_balneario (
  id_treino      INT(11)     NOT NULL COMMENT 'treino.id',
  id_balneario   INT(11)     NOT NULL COMMENT 'balneario.id',
  origem         VARCHAR(20) NOT NULL COMMENT 'MANUAL / AUTOMATICA',
  data_alteracao DATETIME    NOT NULL,
  id_utilizador  INT(11)     NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id_treino, id_balneario),
  KEY idx_treino_balneario_balneario (id_balneario),
  KEY idx_treino_balneario_utilizador (id_utilizador),
  CONSTRAINT fk_treino_balneario_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_balneario_balneario FOREIGN KEY (id_balneario) REFERENCES balneario (id),
  CONSTRAINT fk_treino_balneario_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT chk_treino_balneario_origem CHECK (origem IN ('MANUAL','AUTOMATICA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 6) jogo_balneario
-- =====================================================================
-- A FK para jogo fica fora do CREATE (passo 7): se jogo.id não for
-- int(11) InnoDB (não esperado; o dump diz que é), a tabela é criada na
-- mesma e a FK não. Nesse caso, o SELECT de estado mostra a falta.
CREATE TABLE IF NOT EXISTS jogo_balneario (
  id_jogo        INT(11)     NOT NULL COMMENT 'jogo.id',
  lado           VARCHAR(20) NOT NULL COMMENT 'EQUIPA / ADVERSARIO',
  id_balneario   INT(11)     NOT NULL COMMENT 'balneario.id',
  origem         VARCHAR(20) NOT NULL COMMENT 'MANUAL / AUTOMATICA',
  data_alteracao DATETIME    NOT NULL,
  id_utilizador  INT(11)     NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id_jogo, lado, id_balneario),
  KEY idx_jogo_balneario_balneario (id_balneario),
  KEY idx_jogo_balneario_utilizador (id_utilizador),
  CONSTRAINT fk_jogo_balneario_balneario FOREIGN KEY (id_balneario) REFERENCES balneario (id),
  CONSTRAINT fk_jogo_balneario_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT chk_jogo_balneario_lado CHECK (lado IN ('EQUIPA','ADVERSARIO')),
  CONSTRAINT chk_jogo_balneario_origem CHECK (origem IN ('MANUAL','AUTOMATICA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 7) fk_jogo_balneario_jogo (ON DELETE CASCADE obrigatório: deleteJogo
--    faz só DELETE FROM jogo)
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jogo_balneario'
      AND CONSTRAINT_NAME = 'fk_jogo_balneario_jogo'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
  AND
  (SELECT COUNT(*)
     FROM information_schema.TABLES t
     JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
    WHERE t.TABLE_SCHEMA = DATABASE()
      AND t.TABLE_NAME = 'jogo'
      AND t.ENGINE = 'InnoDB'
      AND c.COLUMN_NAME = 'id'
      AND c.COLUMN_TYPE = 'int(11)'
      AND c.COLUMN_KEY = 'PRI') = 1,
  'ALTER TABLE jogo_balneario ADD CONSTRAINT fk_jogo_balneario_jogo FOREIGN KEY (id_jogo) REFERENCES jogo (id) ON DELETE CASCADE',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 8) Estado (só leitura): esperado tabelas = 6, fks = 11, checks = 7,
--    fk_jogo_ok = 1, fk_treino_cascade = 1. Se fk_jogo_ok = 0, jogo.id não
--    é compatível e a FK não foi criada (o backend funciona, mas apagar um
--    jogo deixa atribuições órfãs em jogo_balneario).
-- =====================================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
                         'balneario_prioridade','treino_balneario','jogo_balneario')) AS tabelas,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
                         'balneario_prioridade','treino_balneario','jogo_balneario')) AS fks,
  (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
                         'balneario_prioridade','treino_balneario','jogo_balneario')) AS checks,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'jogo_balneario'
      AND CONSTRAINT_NAME = 'fk_jogo_balneario_jogo' AND DELETE_RULE = 'CASCADE') AS fk_jogo_ok,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_balneario'
      AND CONSTRAINT_NAME = 'fk_treino_balneario_treino' AND DELETE_RULE = 'CASCADE') AS fk_treino_cascade;

-- =====================================================================
-- 9) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'balneario_prioridade','treino_balneario','jogo_balneario');
--    -- 6 linhas, InnoDB, utf8mb4_uca1400_ai_ci
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'balneario_prioridade','treino_balneario','jogo_balneario')
--  ORDER BY TABLE_NAME, ORDINAL_POSITION;
--    -- balneario.nome varchar(30) utf8mb4_uca1400_ai_ci
-- SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'balneario_prioridade','treino_balneario','jogo_balneario')
--  ORDER BY TABLE_NAME, CONSTRAINT_NAME;
--    -- CASCADE: preferencia->balneario, treino_balneario->treino,
--    --          jogo_balneario->jogo; as outras 8 RESTRICT
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'treino_balneario','jogo_balneario');
--    -- 7 CHECK
-- SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario'
--  ORDER BY INDEX_NAME, SEQ_IN_INDEX;
--    -- uq_balneario_tenant_nome NON_UNIQUE = 0 (tenant_id, nome)
-- SELECT (SELECT COUNT(*) FROM treino) AS treinos, (SELECT COUNT(*) FROM jogo) AS jogos,
--        (SELECT COUNT(*) FROM jogo_jogador) AS jogo_jogador;
--    -- iguais antes e depois de aplicar (CA32)
-- Se um FOREIGN KEY falhar com errno 150: SHOW ENGINE INNODB STATUS
-- (LATEST FOREIGN KEY ERROR).
