-- =====================================================================
-- SM-CORE | Micro Ciclo: confirmação "Vou" do atleta por sessão
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v8 (desenho 02-desenho-v8.md, secção 3, tarefa
-- BD-v8-1). Depende da V13 (o passo 0 pára o script se faltar). V6-V13
-- não mudam.
--
-- Objetivo:
--   Guardar o "Vou" de cada atleta numa sessão (o principal ou um físico
--   avulso), com o texto da sessão que o atleta viu, para detetar na
--   leitura se a confirmação caducou. O "Não vou" continua a ser
--   treino_ausencia ATIVA; a exclusão mútua é do código.
--
-- O que cria (InnoDB, utf8mb4_uca1400_ai_ci, como a V7 e a V13; inteiros
-- INT(11) com sinal, como treino.id, jogador.id e utilizadores.id):
--   treino_confirmacao (filha de treino, sem tenant_id: o tenant vem de
--   treino -> micro_ciclo, como em treino_ausencia)
--     - PK (id_treino, id_jogador)
--     - idx_treino_confirmacao_jogador (id_jogador)
--     - fk_treino_confirmacao_treino      -> treino(id) ON DELETE CASCADE
--     - fk_treino_confirmacao_jogador     -> jogador(id)
--     - fk_treino_confirmacao_utilizador  -> utilizadores(id)
--   Cascata coerente com a V11 e a V13: apagar o treino (ou o micro
--   ciclo, por fk_treino_micro_ciclo) apaga as confirmações dele.
--   Sem CHECK: "nunca um físico ligado" atravessa linhas e é do código.
--
-- Impacto nos dados existentes:
--   Nenhum. Só cria uma tabela, vazia: todos começam "Sem resposta" e as
--   ausências continuam "Não vai" (CA170). Sem INSERT, UPDATE nem DELETE.
--   Não toca em treino_ausencia nem nos seus CHECK. O backend v7 não a
--   conhece. O CREATE pede um metadata lock curto em treino, jogador e
--   utilizadores.
--   Efeito das FKs sem cascade: apagar um jogador ou um utilizador com
--   confirmações passa a falhar (como treino_convocado na V7).
--
-- Idempotência:
--   CREATE TABLE IF NOT EXISTS, com índice e FKs dentro do CREATE
--   (atómico, como a V7 e a V13). Correr duas vezes não dá erro. Atenção:
--   se a tabela já existir com outra definição, o IF NOT EXISTS ignora-a
--   em silêncio (diagnóstico no fim).
--
-- Ordem de aplicação:
--   DEPOIS da V13 e ANTES do backend v8, que lê a tabela. Primeiro no
--   schema de testes, depois em produção. Pode ir sozinho: é inerte para
--   o backend v7. Independente da V12.
--
-- Reversão (manual, só pelo dono; perde as confirmações):
--   DROP TABLE treino_confirmacao;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V13 aplicada NESTE schema, e as PK alvo das FKs
-- =====================================================================
-- Exige a tabela treino_fisico_excluido (V13) e treino.id, jogador.id e
-- utilizadores.id int(11) PK. Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V14_requer_V13' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes do CREATE.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_fisico_excluido') = 1
  AND
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('treino','jogador','utilizadores')
      AND COLUMN_NAME = 'id' AND COLUMN_TYPE = 'int(11)' AND COLUMN_KEY = 'PRI') = 3,
  'DO 0',
  'SELECT 1 FROM ERRO_V14_requer_V13');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) treino_confirmacao
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino_confirmacao (
  id_treino         INT(11)       NOT NULL COMMENT 'Sessão: o principal ou o físico avulso (treino.id); nunca um físico ligado',
  id_jogador        INT(11)       NOT NULL COMMENT 'jogador.id',
  sessao_confirmada VARCHAR(1000) NOT NULL COMMENT 'Texto canónico da sessão que o atleta confirmou (data|início|fim|local; físicos)',
  sessao_avisada    VARCHAR(1000) NULL DEFAULT NULL COMMENT 'Última sessão para a qual se enviou CONFIRMACAO_CADUCOU',
  data_confirmacao  DATETIME      NOT NULL COMMENT 'Última resposta Vou',
  id_utilizador     INT(11)       NOT NULL COMMENT 'Conta do atleta que respondeu (utilizadores.id)',
  PRIMARY KEY (id_treino, id_jogador),
  KEY idx_treino_confirmacao_jogador (id_jogador),
  CONSTRAINT fk_treino_confirmacao_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_confirmacao_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id),
  CONSTRAINT fk_treino_confirmacao_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_confirmacao';
--    -- InnoDB, utf8mb4_uca1400_ai_ci
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_confirmacao'
--  ORDER BY ORDINAL_POSITION;                      -- 6 colunas; só sessao_avisada YES NULL
-- SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_confirmacao'
--  ORDER BY INDEX_NAME, SEQ_IN_INDEX;               -- PRIMARY (2) e idx_treino_confirmacao_jogador
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_confirmacao'
--  ORDER BY CONSTRAINT_NAME;                       -- jogador RESTRICT, treino CASCADE, utilizador RESTRICT
-- SELECT COUNT(*) FROM treino_confirmacao;         -- 0 antes do backend v8
-- Se o CREATE falhar com errno 150: SHOW ENGINE INNODB STATUS
-- (LATEST FOREIGN KEY ERROR).
--
-- Teste da cascata (SÓ no schema de testes, numa transação que se desfaz
-- com ROLLBACK; não grava nada). Substituir <id_treino> por um treino de
-- teste, <id_jogador> e <id_utilizador> por ids existentes:
-- START TRANSACTION;
-- INSERT INTO treino_confirmacao (id_treino, id_jogador, sessao_confirmada, data_confirmacao, id_utilizador)
-- VALUES (<id_treino>, <id_jogador>, '20991231|18:00|19:30|pavilhão', NOW(), <id_utilizador>);
-- DELETE FROM treino WHERE id = <id_treino>;       -- a) apagar o treino
-- SELECT COUNT(*) FROM treino_confirmacao WHERE id_treino = <id_treino>;  -- 0
-- ROLLBACK;
-- b) repetir com DELETE FROM micro_ciclo WHERE id = <micro ciclo de teste
--    desse treino> (cascata micro_ciclo -> treino -> confirmação); a mesma
--    contagem dá 0; ROLLBACK.
