-- =====================================================================
-- SM-CORE | Cor da equipa (escalao_epoca)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo (pedido do dono: cada equipa tem uma cor, fixa
-- durante a época). A "equipa" é escalao_epoca, por isso a cor fica aí.
--
-- Objetivo:
--   Guardar a cor de cada equipa, no formato #RRGGBB, para o Micro Ciclo.
--
-- O que cria:
--   escalao_epoca
--     - coluna  cor VARCHAR(7) NULL      (herda o charset da tabela, utf8mb3)
--     - CHECK   chk_escalao_epoca_cor    cor IS NULL ou '^#[0-9A-Fa-f]{6}$'
--
-- Nota: é a primeira alteração a escalao_epoca, uma tabela do schema base
-- (não versionado). Não é a primeira a uma tabela base: V2 alterou
-- presenca_jogador e V4/V5 alteraram utilizadores.
--
-- Impacto nos dados existentes:
--   Só acrescenta a coluna; os registos existentes ficam com cor NULL.
--   Sem UPDATE/DELETE. O CHECK aceita NULL, por isso valida as linhas
--   existentes sem falhar. O único insert atual (EquipaHelper) usa lista
--   de colunas, por isso não é afetado; novas equipas ficam com NULL.
--   Locks: a coluna no fim da tabela é instantânea em InnoDB; o CHECK
--   pode obrigar a copiar a tabela (bloqueia escritas durante a cópia).
--   A tabela é pequena (AUTO_INCREMENT=36 no dump de testes), mas o ALTER
--   espera por transações abertas na tabela (metadata lock). Aplicar fora
--   das horas de uso.
--
-- Idempotência:
--   Coluna com ADD COLUMN IF NOT EXISTS. O CHECK só é criado se não
--   existir em information_schema.CHECK_CONSTRAINTS (SQL dinâmico, como as
--   FKs da V5), para não depender de IF NOT EXISTS em ADD CONSTRAINT ...
--   CHECK. Correr duas vezes não dá erro.
--   Atenção: se a coluna cor já existir com outra definição, o IF NOT
--   EXISTS ignora-a em silêncio (ver diagnóstico no fim).
--
-- Ordem de aplicação:
--   ANTES do backend que lê/grava a cor. Primeiro no schema de testes,
--   depois em produção. Pode ir sozinho: o código atual ignora a coluna.
--
-- Reversão (manual, pelo dono). Ordem: CHECK, depois coluna.
--   ALTER TABLE escalao_epoca DROP CONSTRAINT chk_escalao_epoca_cor;
--   ALTER TABLE escalao_epoca DROP COLUMN cor;
-- =====================================================================

-- =====================================================================
-- 1) escalao_epoca.cor
-- =====================================================================
ALTER TABLE escalao_epoca
  ADD COLUMN IF NOT EXISTS cor VARCHAR(7) NULL DEFAULT NULL COMMENT 'Cor da equipa na época, formato #RRGGBB';

-- =====================================================================
-- 2) CHECK do formato #RRGGBB
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'escalao_epoca'
      AND CONSTRAINT_NAME = 'chk_escalao_epoca_cor') = 0,
  'ALTER TABLE escalao_epoca ADD CONSTRAINT chk_escalao_epoca_cor CHECK (cor IS NULL OR cor REGEXP ''^#[0-9A-Fa-f]{6}$'')',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'escalao_epoca'
--    AND COLUMN_NAME = 'cor';                 -- varchar(7), YES, NULL
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE
--   FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'escalao_epoca';
-- SELECT COUNT(*) AS total, COUNT(cor) AS com_cor FROM escalao_epoca;  -- com_cor = 0
-- Teste do REGEXP sem escrever na tabela:
-- SELECT '#1A2b3C' REGEXP '^#[0-9A-Fa-f]{6}$' AS ok,       -- 1
--        '#12345'  REGEXP '^#[0-9A-Fa-f]{6}$' AS curto,    -- 0
--        '123456#' REGEXP '^#[0-9A-Fa-f]{6}$' AS sem_cardinal, -- 0
--        '#GGGGGG' REGEXP '^#[0-9A-Fa-f]{6}$' AS nao_hex;  -- 0
