-- =====================================================================
-- SM-CORE | Micro Ciclo: participantes de cada treino físico ligado
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v7 (desenho 02-desenho-v7.md, secção 3.1, tarefa
-- BD-v7-1). Depende da V11 (treino.id_treino_principal). V6-V12 não
-- mudam.
--
-- Objetivo:
--   Guardar quem NÃO faz um físico ligado. Participantes = convocatória
--   efetiva do principal - excluídos do físico. Sem linhas = todos.
--
-- O que cria (InnoDB, utf8mb4_uca1400_ai_ci, como a V7; inteiros INT(11)
-- com sinal, como treino.id, jogador.id e utilizadores.id):
--   treino_fisico_excluido (filha de treino, sem tenant_id: o tenant vem
--   de treino -> micro_ciclo)
--     - PK (id_treino, id_jogador)
--     - idx_treino_fisico_excluido_jogador (id_jogador)
--     - fk_treino_fisico_excluido_treino      -> treino(id) ON DELETE CASCADE
--     - fk_treino_fisico_excluido_jogador     -> jogador(id)
--     - fk_treino_fisico_excluido_utilizador  -> utilizadores(id)
--   Cascata coerente com fk_treino_principal (V11): apagar o principal
--   apaga os físicos, e apagar um físico (ou o micro_ciclo) apaga as
--   exclusões dele. Sem CHECK: "só físicos ligados e só jogadores
--   convocados" atravessa linhas e é do código.
--
-- Impacto nos dados existentes:
--   Nenhum. Só cria uma tabela, vazia ("vazia = todos participam", R203).
--   Sem INSERT, UPDATE nem DELETE. O backend v6 não a conhece. A tabela
--   treino não é alterada (a FK fica na tabela nova), mas o CREATE pede
--   um metadata lock curto em treino, jogador e utilizadores.
--   Efeito da FK sem cascade para jogador e utilizadores: apagar um
--   jogador ou um utilizador com exclusões passa a falhar (como
--   treino_convocado na V7).
--
-- Idempotência:
--   CREATE TABLE IF NOT EXISTS, com índice e FKs dentro do CREATE
--   (atómico, como a V7). Correr duas vezes não dá erro. Atenção: se a
--   tabela já existir com outra definição, o IF NOT EXISTS ignora-a em
--   silêncio (diagnóstico no fim).
--
-- Ordem de aplicação:
--   DEPOIS da V11 (o passo 0 pára o script se faltar) e ANTES do backend
--   v7, que lê a tabela. Primeiro no schema de testes, depois em
--   produção. Pode ir sozinho: é inerte para o backend v6. Independente
--   da V12.
--
-- Reversão (manual, só pelo dono; perde só as escolhas de participantes,
-- os físicos voltam a "todos"):
--   DROP TABLE treino_fisico_excluido;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V11 aplicada NESTE schema, e as PK alvo das FKs
-- =====================================================================
-- Exige treino.id_treino_principal (V11) e treino.id, jogador.id e
-- utilizadores.id int(11) PK. Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V13_requer_V11' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes do CREATE.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND ((TABLE_NAME = 'treino' AND COLUMN_NAME = 'id_treino_principal')
           OR (TABLE_NAME IN ('treino','jogador','utilizadores')
               AND COLUMN_NAME = 'id' AND COLUMN_TYPE = 'int(11)' AND COLUMN_KEY = 'PRI'))) = 4,
  'DO 0',
  'SELECT 1 FROM ERRO_V13_requer_V11');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) treino_fisico_excluido
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino_fisico_excluido (
  id_treino               INT(11)  NOT NULL COMMENT 'Físico ligado (treino.id com id_treino_principal)',
  id_jogador              INT(11)  NOT NULL COMMENT 'Jogador que não faz este físico (jogador.id)',
  data_alteracao          DATETIME NOT NULL,
  id_utilizador_alteracao INT(11)  NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id_treino, id_jogador),
  KEY idx_treino_fisico_excluido_jogador (id_jogador),
  CONSTRAINT fk_treino_fisico_excluido_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_fisico_excluido_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id),
  CONSTRAINT fk_treino_fisico_excluido_utilizador FOREIGN KEY (id_utilizador_alteracao) REFERENCES utilizadores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_fisico_excluido';
--    -- InnoDB, utf8mb4_uca1400_ai_ci
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_fisico_excluido'
--  ORDER BY ORDINAL_POSITION;                      -- 4 colunas, todas NO
-- SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_fisico_excluido'
--  ORDER BY INDEX_NAME, SEQ_IN_INDEX;               -- PRIMARY (2) e idx_..._jogador
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_fisico_excluido'
--  ORDER BY CONSTRAINT_NAME;                       -- jogador RESTRICT, treino CASCADE, utilizador RESTRICT
-- SELECT COUNT(*) FROM treino_fisico_excluido;     -- 0 antes do backend v7
-- Se o CREATE falhar com errno 150: SHOW ENGINE INNODB STATUS
-- (LATEST FOREIGN KEY ERROR).
--
-- Teste da cascata (SÓ no schema de testes, numa transação que se desfaz
-- com ROLLBACK; não grava nada). Substituir <id_fisico> por um físico
-- ligado de teste, <id_jogador> e <id_utilizador> por ids existentes:
-- START TRANSACTION;
-- INSERT INTO treino_fisico_excluido (id_treino, id_jogador, data_alteracao, id_utilizador_alteracao)
-- VALUES (<id_fisico>, <id_jogador>, NOW(), <id_utilizador>);
-- DELETE FROM treino WHERE id = <id_fisico>;       -- a) apagar o físico
-- SELECT COUNT(*) FROM treino_fisico_excluido WHERE id_treino = <id_fisico>;  -- 0
-- ROLLBACK;
-- b) repetir com DELETE FROM treino WHERE id = <id do principal> (cascata
--    dupla: principal -> físico -> exclusão); a mesma contagem dá 0;
--    ROLLBACK.
