-- =====================================================================
-- SM-CORE | Micro Ciclo: origem do cancelamento de um treino
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v4 (desenho 02-desenho-v4.md, secção 4, tarefa
-- DBA-v4-1). Depende da V7 (tabela treino). V6, V7 e V8 não mudam.
--
-- Objetivo:
--   Saber se um treino foi cancelado pelo cancelamento do micro ciclo
--   (MICRO_CICLO) ou um a um (TREINO), para que reativar o micro ciclo
--   só traga de volta os primeiros.
--
-- O que cria:
--   treino
--     - coluna  origem_cancelamento VARCHAR(20) NULL
--               (herda utf8mb4_uca1400_ai_ci da tabela, criada na V7)
--     - CHECK   chk_treino_origem_cancelamento
--               NULL ou IN ('MICRO_CICLO','TREINO')
--   Sem índices novos.
--
-- Impacto nos dados existentes:
--   Só estrutura. Os treinos existentes ficam com origem_cancelamento
--   NULL, sem UPDATE: os cancelados antes da V9 resolvem-se no backend
--   pela regra do motivo (Q51). O CHECK aceita NULL, por isso as linhas
--   existentes passam. Os inserts atuais em treino usam lista de colunas
--   (desenho §4) e o backend atual ignora a coluna.
--   Locks: a coluna no fim da tabela é instantânea em InnoDB; o CHECK
--   pode obrigar a copiar a tabela (bloqueia escritas durante a cópia).
--   A tabela treino é recente e pequena, mas os dois ALTER esperam por
--   transações abertas em treino (metadata lock). Aplicar fora das
--   horas de uso.
--
-- Idempotência:
--   Coluna com ADD COLUMN IF NOT EXISTS. O CHECK só é criado se não
--   existir em information_schema.TABLE_CONSTRAINTS (SQL dinâmico, como
--   na V5/V8), porque ADD CONSTRAINT IF NOT EXISTS para CHECK não foi
--   verificado em MariaDB 11.8. Correr duas vezes não dá erro.
--   Atenção: se a coluna já existir com outra definição, o IF NOT EXISTS
--   ignora-a em silêncio (ver diagnóstico no fim).
--
-- Ordem de aplicação:
--   DEPOIS da V7 e ANTES do backend v4 (cancelaMicroCiclo e CANCELAR
--   passam a escrever a coluna). Primeiro no schema de testes, depois em
--   produção. Pode ir sozinho: o backend atual ignora a coluna.
--
-- Reversão (manual, pelo dono; a coluna pode ficar, é NULL e ignorada).
-- Ordem: CHECK, depois coluna.
--   ALTER TABLE treino DROP CONSTRAINT chk_treino_origem_cancelamento;
--   ALTER TABLE treino DROP COLUMN origem_cancelamento;
-- =====================================================================

-- =====================================================================
-- 1) treino.origem_cancelamento
-- =====================================================================
ALTER TABLE treino
  ADD COLUMN IF NOT EXISTS origem_cancelamento VARCHAR(20) NULL DEFAULT NULL COMMENT 'Quem cancelou: MICRO_CICLO (cancelamento do micro ciclo) ou TREINO (um a um); NULL = não cancelado ou anterior à V9';

-- =====================================================================
-- 2) CHECK dos valores
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'chk_treino_origem_cancelamento'
      AND CONSTRAINT_TYPE = 'CHECK') = 0,
  'ALTER TABLE treino ADD CONSTRAINT chk_treino_origem_cancelamento CHECK (origem_cancelamento IS NULL OR origem_cancelamento IN (''MICRO_CICLO'',''TREINO''))',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--    AND COLUMN_NAME = 'origem_cancelamento';      -- varchar(20), YES, NULL
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE
--   FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--  ORDER BY CONSTRAINT_NAME;                       -- 3 linhas: estado, origem_cancelamento, tipo
-- SELECT estado, COUNT(*) AS total, COUNT(origem_cancelamento) AS com_origem
--   FROM treino GROUP BY estado;                  -- com_origem = 0 antes do backend v4
