-- =====================================================================
-- SM-CORE | Micro Ciclo: treino físico complementar (ligado a um treino
--           principal)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v6 (desenho 02-desenho-v6.md, secção 3.1, tarefa
-- BD-v6-1). Depende da V7 (tabela treino). V6-V10 não mudam.
--
-- Objetivo:
--   Permitir que um treino (na v6, só o FISICO) seja um complemento de
--   outro treino (o principal, ESCALAO): a sessão é uma só, com uma
--   convocatória, uma presença e um estado.
--
-- O que cria:
--   treino
--     - coluna  id_treino_principal INT(11) NULL (com sinal, igual a
--               treino.id; herda utf8mb4_uca1400_ai_ci da tabela, V7)
--     - índice  idx_treino_principal (id_treino_principal)
--     - FK      fk_treino_principal -> treino(id) ON DELETE CASCADE
--               (auto-referenciada: apagar o principal apaga os físicos,
--               R183)
--   Sem CHECK: as regras "o principal é ESCALAO, da mesma equipa, do mesmo
--   dia e micro ciclo; um ligado não é principal" atravessam linhas e são
--   do código (desenho §5.1). A coluna é genérica (Q95).
--
-- Impacto nos dados existentes:
--   Só estrutura. Nenhum UPDATE. As linhas atuais ficam com NULL, ou seja,
--   treinos autónomos, com o comportamento de hoje. O backend v5 não
--   conhece a coluna e continua a funcionar (os insert dele têm lista de
--   colunas e não a incluem).
--   Cascata dupla ao apagar um micro_ciclo com principal e físico
--   (fk_treino_micro_ciclo e fk_treino_principal): o InnoDB suporta FKs
--   auto-referenciadas com ON DELETE CASCADE (limite de 15 níveis; aqui há
--   1). NÃO TESTADO (os agentes não executam): ver o teste no fim.
--   Locks: ADD COLUMN no fim é instantâneo; ADD INDEX é INPLACE; ADD
--   FOREIGN KEY pode copiar a tabela (bloqueia escritas durante a cópia).
--   Cada ALTER espera por transações abertas em treino (metadata lock).
--   Aplicar fora das horas de uso.
--
-- Idempotência:
--   - coluna: ADD COLUMN IF NOT EXISTS;
--   - índice: ADD INDEX IF NOT EXISTS (MariaDB);
--   - FK: só se não existir em information_schema.TABLE_CONSTRAINTS (SQL
--     dinâmico, como a V10).
--   Correr duas vezes não dá erro. Atenção: se a coluna já existir com
--   outra definição, o IF NOT EXISTS ignora-a em silêncio (diagnóstico no
--   fim).
--
-- Ordem de aplicação:
--   DEPOIS da V10 (o passo 0 pára o script se faltar) e ANTES do backend
--   v6, que lê id_treino_principal. Primeiro no schema de testes, depois
--   em produção. Pode ir sozinho: é inerte para o backend v5.
--   A V12 (migração dos físicos existentes) é separada e vem DEPOIS do
--   backend v6.
--
-- Reversão (manual, só pelo dono). Antes, se houver físicos ligados,
-- desligá-los (perdem a ligação; ver desenho §8):
--   UPDATE treino SET id_treino_principal = NULL WHERE id_treino_principal IS NOT NULL;
-- Depois, por esta ordem:
--   ALTER TABLE treino DROP FOREIGN KEY fk_treino_principal;
--   ALTER TABLE treino DROP INDEX idx_treino_principal;
--   ALTER TABLE treino DROP COLUMN id_treino_principal;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V7 e V10 aplicadas NESTE schema
-- =====================================================================
-- Verifica treino.id int(11) PK (alvo da FK) e treino.id_grupo (V10).
-- Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V11_requer_V10_aplicada' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de qualquer ALTER.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND ((COLUMN_NAME = 'id' AND COLUMN_TYPE = 'int(11)' AND COLUMN_KEY = 'PRI')
           OR COLUMN_NAME = 'id_grupo')) = 2,
  'DO 0',
  'SELECT 1 FROM ERRO_V11_requer_V10_aplicada');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) treino.id_treino_principal
-- =====================================================================
ALTER TABLE treino
  ADD COLUMN IF NOT EXISTS id_treino_principal INT(11) NULL DEFAULT NULL COMMENT 'Treino principal (treino.id) de um complemento (v6: físico); NULL = treino autónomo';

-- =====================================================================
-- 2) Índice (antes da FK, para a FK o usar em vez de criar outro)
-- =====================================================================
ALTER TABLE treino
  ADD INDEX IF NOT EXISTS idx_treino_principal (id_treino_principal);

-- =====================================================================
-- 3) FK auto-referenciada com cascata
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'fk_treino_principal'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE treino ADD CONSTRAINT fk_treino_principal FOREIGN KEY (id_treino_principal) REFERENCES treino (id) ON DELETE CASCADE',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 4) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--    AND COLUMN_NAME = 'id_treino_principal';       -- int(11), YES, NULL
-- SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--    AND COLUMN_NAME = 'id_treino_principal';       -- só idx_treino_principal
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--  ORDER BY CONSTRAINT_NAME;                       -- fk_treino_principal treino RESTRICT CASCADE
-- SELECT COUNT(*) FROM treino WHERE id_treino_principal IS NOT NULL;  -- 0 antes do backend v6
-- Se o passo 3 falhar: SHOW ENGINE INNODB STATUS (LATEST FOREIGN KEY ERROR).
--
-- Teste da cascata (SÓ no schema de testes, numa transação que se desfaz
-- com ROLLBACK; não grava nada). Substituir <id_micro_ciclo>, <id_equipa>
-- e <id_utilizador> por ids existentes do mesmo tenant:
-- START TRANSACTION;
-- INSERT INTO treino (id_micro_ciclo, id_equipa, tipo, data, hora_inicio, hora_fim,
--                     estado, datacriacao, id_utilizador_criacao)
-- VALUES (<id_micro_ciclo>, <id_equipa>, 'ESCALAO', 20991231, '18:00', '19:30',
--         'RASCUNHO', NOW(), <id_utilizador>);
-- SET @p = LAST_INSERT_ID();
-- INSERT INTO treino (id_micro_ciclo, id_equipa, tipo, data, hora_inicio, hora_fim,
--                     estado, datacriacao, id_utilizador_criacao, id_treino_principal)
-- VALUES (<id_micro_ciclo>, <id_equipa>, 'FISICO', 20991231, '17:00', '18:00',
--         'RASCUNHO', NOW(), <id_utilizador>, @p);
-- SET @f = LAST_INSERT_ID();
-- DELETE FROM treino WHERE id = @p;                -- a) apagar o principal
-- SELECT COUNT(*) FROM treino WHERE id = @f;       --    tem de dar 0
-- ROLLBACK;
-- b) cascata dupla: repetir os dois INSERT num micro_ciclo de teste criado
--    para o efeito (data_inicio sem uso, ex. 20991228) e, em vez do DELETE
--    do principal, DELETE FROM micro_ciclo WHERE id = <esse id>; depois
--    SELECT COUNT(*) FROM treino WHERE id IN (@p, @f) -> 0; ROLLBACK.
