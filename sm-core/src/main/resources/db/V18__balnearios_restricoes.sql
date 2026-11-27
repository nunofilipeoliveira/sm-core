-- =====================================================================
-- SM-CORE | Balneários: restrições de uso (quem pode usar cada balneário)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: balneários v2 (desenho .melhorias/balnearios/02-desenho-v2.md,
-- secção 4, tarefas BD-1 a BD-3). Depende da V17 (o passo 0 pára o
-- script se faltar). V1-V17 não mudam.
--
-- Objetivo:
--   - Guardar, por balneário, uma regra de uso: TODOS (por omissão),
--     DEDICADO ("Só ...") ou EXCLUSAO ("Todos exceto ..."), com uma lista
--     de escalões e/ou "Adversários".
--
-- O que cria ou altera:
--   balneario (alterada)
--     - coluna nova uso VARCHAR(20) NOT NULL DEFAULT 'TODOS', depois de estado
--     - chk_balneario_uso: uso IN ('TODOS','DEDICADO','EXCLUSAO')
--       (SQL dinâmico, só se ainda não existir; passo 2)
--   balneario_restricao (nova, filha de balneario; herda o tenant pelo join)
--     - fk_balneario_restricao_balneario -> balneario(id) ON DELETE CASCADE
--     - fk_balneario_restricao_escalao   -> escalao(id) (RESTRICT)
--     - chk_balneario_restricao_tipo
--     - idx_balneario_restricao_balneario, idx_balneario_restricao_escalao
--   A unicidade (cada escalão e "Adversários" uma só vez por balneário) e a
--   coerência (TODOS sem linhas; DEDICADO/EXCLUSAO com pelo menos uma)
--   ficam no código, que substitui sempre a lista do balneário.
--
-- Verificações da BD-1 (feitas no dump .claude/docs/schema-testes.sql,
-- servidor 11.8.6-MariaDB, e na V17; NÃO na BD viva):
--   - escalao.id: int(11) NOT NULL AUTO_INCREMENT, PK, InnoDB, utf8mb3
--     (o charset não conta numa FK de inteiros, como na V17).
--   - balneario e balneario_preferencia não estão no dump: vêm da V17
--     (balneario.id INT(11) PK InnoDB utf8mb4_uca1400_ai_ci; estado
--     VARCHAR(1)). Nenhuma coluna uso nem tabela balneario_restricao na V17.
--   Correr as QUERIES DE VERIFICAÇÃO abaixo no schema de testes ANTES de
--   aplicar, para confirmar que a BD corresponde a isto.
--
-- QUERIES DE VERIFICAÇÃO (só leitura; correr à mão ANTES de aplicar):
-- SELECT @@version;
--    -- 11.8.x (ADD COLUMN IF NOT EXISTS existe desde a 10.0.2)
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('balneario','balneario_config','balneario_preferencia',
--                       'balneario_prioridade','treino_balneario','jogo_balneario','escalao');
--    -- 7 linhas, todas InnoDB (V17 aplicada)
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id'
--    AND TABLE_NAME IN ('balneario','escalao');
--    -- 2 linhas: int(11) NO PRI auto_increment
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario' AND COLUMN_NAME = 'uso';
--    -- esperado: 0 linhas (se houver com outra definição, o IF NOT EXISTS
--    -- ignorava-a em silêncio: parar e avisar)
-- SELECT TABLE_NAME FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao';
--    -- esperado: 0 linhas
-- SELECT TABLE_NAME, CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND (CONSTRAINT_NAME = 'chk_balneario_uso' OR CONSTRAINT_NAME LIKE '%balneario\_restricao%');
--    -- esperado: 0 linhas
-- SELECT COUNT(*) AS balnearios FROM balneario;
--    -- anotar: tem de ser igual depois de aplicar
--
-- Impacto nos dados existentes:
--   Nenhuma linha é alterada. Sem INSERT/UPDATE/DELETE. Os balneários
--   existentes passam a ler uso = 'TODOS' pelo DEFAULT (ninguém fica
--   restringido; o cálculo dá as mesmas propostas). balneario_restricao
--   fica vazia. O backend v1 não lê nada disto e o seu INSERT INTO
--   balneario tem as colunas explícitas, por isso o DEFAULT aplica-se.
--   Efeitos das FKs novas (só quando houver linhas):
--   - apagar um balneário apaga as suas restrições (CASCADE);
--   - um escalão referido numa restrição deixa de poder ser apagado (RESTRICT).
--   Locks: o ADD COLUMN com DEFAULT deve ser instantâneo no 11.8 (não
--   verificado no servidor); o ADD CONSTRAINT CHECK valida as linhas da
--   balneario (tabela pequena; pode copiar a tabela). A FK para escalao
--   toma um lock de metadados curto em escalao.
--
-- Idempotência:
--   - coluna: ADD COLUMN IF NOT EXISTS;
--   - chk_balneario_uso: SQL dinâmico, só se o nome ainda não existir em
--     information_schema.CHECK_CONSTRAINTS para a balneario;
--   - tabela: CREATE TABLE IF NOT EXISTS, com FKs e CHECK dentro do CREATE
--     (como balneario_preferencia na V17): só são criadas com a tabela.
--   Correr duas vezes não dá erro. Atenção: se a coluna ou a tabela já
--   existirem com outra definição, o IF NOT EXISTS ignora-as em silêncio
--   (o SELECT de estado do passo 4 mostra-o).
--   NÃO TESTADO: o agente não executa scripts. A BD-3 (correr duas vezes
--   no schema de testes) fica para o dono.
--
-- Ordem de aplicação:
--   DEPOIS da V17 e ANTES do backend v2 dos balneários (BalnearioHelper.lista
--   lê b.uso em cada getMicroCiclo, getTreino e gravarTreino, em todos os
--   tenants com Micro Ciclo; sem a V18 dá erro de SQL). Primeiro no schema
--   de testes, depois em produção. Pode ir sozinha: é inerte para o
--   backend v1. A V17 e a V18 podem ser aplicadas na mesma sessão, por
--   esta ordem.
--
-- Reversão (manual, só pelo dono, e só DEPOIS de repor o backend v1;
-- perde as regras de uso; não é preciso reverter para voltar ao v1):
--   DROP TABLE balneario_restricao;
--   ALTER TABLE balneario DROP CONSTRAINT chk_balneario_uso, DROP COLUMN uso;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V17 aplicada NESTE schema (balneario e
--    balneario_preferencia InnoDB)
-- =====================================================================
-- Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V18_requer_V17' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de qualquer ALTER/CREATE.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V17).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('balneario','balneario_preferencia')
      AND ENGINE = 'InnoDB') = 2,
  'DO 0',
  'SELECT 1 FROM ERRO_V18_requer_V17');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) balneario.uso
-- =====================================================================
ALTER TABLE balneario
  ADD COLUMN IF NOT EXISTS uso VARCHAR(20) NOT NULL DEFAULT 'TODOS'
    COMMENT 'TODOS / DEDICADO (só a lista) / EXCLUSAO (todos exceto a lista); lista em balneario_restricao'
    AFTER estado;

-- =====================================================================
-- 2) chk_balneario_uso (fora do ADD COLUMN: um CHECK na definição da
--    coluna ficava com o nome da coluna, não com este)
-- =====================================================================
-- Em MariaDB os nomes de CHECK são por tabela, daí o filtro por TABLE_NAME.
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'balneario'
      AND CONSTRAINT_NAME = 'chk_balneario_uso') = 0,
  'ALTER TABLE balneario ADD CONSTRAINT chk_balneario_uso CHECK (uso IN (''TODOS'',''DEDICADO'',''EXCLUSAO''))',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) balneario_restricao
-- =====================================================================
CREATE TABLE IF NOT EXISTS balneario_restricao (
  id           INT(11)     NOT NULL AUTO_INCREMENT,
  id_balneario INT(11)     NOT NULL COMMENT 'balneario.id (o tenant vem do balneário)',
  tipo         VARCHAR(20) NOT NULL COMMENT 'ESCALAO / ADVERSARIO',
  id_escalao   INT(11)     NULL DEFAULT NULL COMMENT 'escalao.id; NULL só em ADVERSARIO',
  PRIMARY KEY (id),
  KEY idx_balneario_restricao_balneario (id_balneario),
  KEY idx_balneario_restricao_escalao (id_escalao),
  CONSTRAINT fk_balneario_restricao_balneario FOREIGN KEY (id_balneario) REFERENCES balneario (id) ON DELETE CASCADE,
  CONSTRAINT fk_balneario_restricao_escalao FOREIGN KEY (id_escalao) REFERENCES escalao (id),
  CONSTRAINT chk_balneario_restricao_tipo CHECK ((tipo = 'ESCALAO' AND id_escalao IS NOT NULL)
                                              OR (tipo = 'ADVERSARIO' AND id_escalao IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 4) Estado (só leitura): esperado uso_ok = 1, chk_uso = 1,
--    tabela_restricao = 1, fks_restricao = 2, fk_restricao_cascade = 1,
--    chk_restricao = 1, balnearios_nao_todos = 0 (logo depois de aplicar;
--    deixa de ser 0 quando o ADMIN gravar regras).
--    uso_ok = 0 com a coluna presente: existia com outra definição.
-- =====================================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario' AND COLUMN_NAME = 'uso'
      AND COLUMN_TYPE = 'varchar(20)' AND IS_NULLABLE = 'NO'
      AND COLUMN_DEFAULT = '''TODOS''') AS uso_ok,
  (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario'
      AND CONSTRAINT_NAME = 'chk_balneario_uso') AS chk_uso,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao'
      AND ENGINE = 'InnoDB') AS tabela_restricao,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao') AS fks_restricao,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao'
      AND CONSTRAINT_NAME = 'fk_balneario_restricao_balneario' AND DELETE_RULE = 'CASCADE') AS fk_restricao_cascade,
  (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao'
      AND CONSTRAINT_NAME = 'chk_balneario_restricao_tipo') AS chk_restricao,
  (SELECT COUNT(*) FROM balneario WHERE uso <> 'TODOS') AS balnearios_nao_todos;

-- =====================================================================
-- 5) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('balneario','balneario_restricao')
--  ORDER BY TABLE_NAME, ORDINAL_POSITION;
--    -- balneario.uso varchar(20) NO 'TODOS' utf8mb4_uca1400_ai_ci, logo a seguir a estado
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN ('balneario','balneario_restricao');
--    -- balneario: chk_balneario_estado, chk_balneario_libertacao, chk_balneario_uso;
--    -- balneario_restricao: chk_balneario_restricao_tipo
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao';
--    -- fk_balneario_restricao_balneario balneario CASCADE;
--    -- fk_balneario_restricao_escalao escalao RESTRICT
-- SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'balneario_restricao'
--  ORDER BY INDEX_NAME, SEQ_IN_INDEX;
--    -- PRIMARY (id), idx_balneario_restricao_balneario, idx_balneario_restricao_escalao
-- SELECT uso, COUNT(*) FROM balneario GROUP BY uso;
--    -- só TODOS (CA33)
-- SELECT (SELECT COUNT(*) FROM balneario) AS balnearios,
--        (SELECT COUNT(*) FROM balneario_preferencia) AS preferencias,
--        (SELECT COUNT(*) FROM treino_balneario) AS treino_balneario,
--        (SELECT COUNT(*) FROM jogo_balneario) AS jogo_balneario;
--    -- iguais antes e depois de aplicar (CA52)
-- Se um FOREIGN KEY falhar com errno 150: SHOW ENGINE INNODB STATUS
-- (LATEST FOREIGN KEY ERROR).
