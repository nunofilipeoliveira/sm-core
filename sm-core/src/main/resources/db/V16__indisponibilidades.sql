-- =====================================================================
-- SM-CORE | Indisponibilidades do atleta (períodos em que não pode ir a
--           treinos nem a jogos)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: indisponibilidades (desenho .melhorias/indisponibilidades/
-- 02-desenho.md, secção 4, tarefas BD-1 e BD-2). Depende da V15 (o
-- passo 0 pára o script se faltar). V1-V15 não mudam.
--
-- Objetivo:
--   - Guardar as indisponibilidades indicadas pelo atleta (intervalo de
--     datas, categoria, motivo).
--   - Marcar em treino_ausencia as ausências que vêm de uma
--     indisponibilidade (NULL = ausência indicada à mão).
--   - Guardar a origem das entradas automáticas INDISPONÍVEL em
--     jogo_jogador, numa tabela à parte (jogo_jogador é apagado e
--     reinserido noutros fluxos; uma coluna lá perder-se-ia).
--
-- O que cria / altera:
--   indisponibilidade (nova; filha de jogador, sem tenant_id)
--     - fk_indisponibilidade_jogador     -> jogador(id)
--     - fk_indisponibilidade_utilizador  -> utilizadores(id)
--     - idx_indisponibilidade_jogador (id_jogador, estado, data_inicio)
--     - chk_indisponibilidade_categoria, chk_indisponibilidade_estado,
--       chk_indisponibilidade_datas
--   treino_ausencia (alterada)
--     - coluna id_indisponibilidade INT(11) NULL
--     - idx_treino_ausencia_indisponibilidade
--     - fk_treino_ausencia_indisponibilidade -> indisponibilidade(id)
--       (sem cascata: as indisponibilidades nunca se apagam)
--   indisponibilidade_jogo (nova)
--     - PK (id_indisponibilidade, id_jogo)
--     - idx_indisponibilidade_jogo_jogo (id_jogo, estado)
--     - fk_indisponibilidade_jogo_indisponibilidade -> indisponibilidade(id)
--     - fk_indisponibilidade_jogo_jogo -> jogo(id) ON DELETE CASCADE
--       (só se jogo.id for int(11) InnoDB; ver passo 4)
--     - chk_indisponibilidade_jogo_estado
--
-- Verificações da BD-1 (feitas no dump .claude/docs/schema-testes.sql,
-- MariaDB 11.8.6, schema "teste"; NÃO na BD viva):
--   - jogo.id: int(11) NOT NULL AUTO_INCREMENT, PK, InnoDB, utf8mb3.
--     Compatível com INT(11) com sinal: a FK com CASCADE é criada.
--   - jogo_jogador.obs: varchar(200) (o texto automático tem 35).
--   - jogo_jogador.estado: varchar(50), utf8mb3_general_ci. O literal
--     'INDISPONÍVEL' (í é BMP) cabe em utf8mb3. Nota: em general_ci
--     'INDISPONIVEL' = 'INDISPONÍVEL' nas comparações.
--   - jogo_jogador: sem PK e sem UNIQUE (id_jogo, id_jogador). Só uma FK:
--     jogo_jogador_jogo_FK (id_jogo) -> jogo(id) ON DELETE CASCADE. Sem FK
--     em id_jogador. Logo o risco R-6 do desenho NÃO se verifica: apagar
--     um jogo continua a apagar as suas linhas (e, com a FK do passo 4,
--     as ligações de indisponibilidade_jogo).
--   - jogo_jogador: todas as colunas fora de (id_jogo, id_jogador, estado,
--     obs, numero) têm DEFAULT (NULL, ou 0 em gr/titular/em_campo/
--     tempo_jogo_segundos/tempo_manual). O INSERT automático funciona.
--   - jogador.id e utilizadores.id: int(11) NOT NULL AUTO_INCREMENT, PK,
--     InnoDB (utf8mb3; o charset não conta numa FK de inteiros, como na V7).
--   - O dump não tem as tabelas da V7 em diante (treino_ausencia vem da
--     V7, linhas 199-218). Correr as QUERIES DE VERIFICAÇÃO abaixo no
--     schema de testes ANTES de aplicar, para confirmar que o dump ainda
--     corresponde à BD.
--
-- QUERIES DE VERIFICAÇÃO (só leitura; correr à mão ANTES de aplicar):
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('jogo','jogo_jogador','jogador','utilizadores','treino_ausencia','micro_ciclo');
--    -- todas InnoDB
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND ((TABLE_NAME IN ('jogo','jogador','utilizadores') AND COLUMN_NAME = 'id')
--      OR (TABLE_NAME = 'jogo_jogador' AND COLUMN_NAME IN ('id_jogo','id_jogador','estado','obs','numero')));
--    -- ids int(11) PRI; obs varchar(200) (>= 35); estado varchar(50)
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jogo_jogador'
--    AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL;
--    -- esperado: 0 linhas (o INSERT automático só preenche 5 colunas)
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'jogo_jogador';
--    -- esperado: jogo_jogador_jogo_FK jogo ... CASCADE (se RESTRICT: risco R-6)
-- SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jogo_jogador'
--  ORDER BY INDEX_NAME, SEQ_IN_INDEX;
--    -- esperado: só índices NON_UNIQUE = 1
-- SELECT estado, COUNT(*) FROM jogo_jogador GROUP BY estado;
--    -- confirmar a grafia exata de 'INDISPONÍVEL' já usada
--
-- Impacto nos dados existentes:
--   Nenhum. Sem UPDATE/DELETE/INSERT. As linhas existentes de
--   treino_ausencia ficam com id_indisponibilidade NULL, que se lê como
--   "ausência manual". Não converte ausências, presenças nem
--   convocatórias. jogo e jogo_jogador não são alterados (só passam a ser
--   referidos por uma FK nova). O backend atual ignora a coluna e as
--   tabelas novas.
--   Locks: ADD COLUMN no fim é instantâneo; ADD INDEX é INPLACE; ADD
--   FOREIGN KEY em treino_ausencia pode copiar a tabela (pequena). Cada
--   ALTER espera por transações abertas em treino_ausencia (metadata
--   lock). Criar a FK para jogo toma um lock de metadados curto em jogo.
--
-- Idempotência:
--   - tabelas: CREATE TABLE IF NOT EXISTS;
--   - coluna: ADD COLUMN IF NOT EXISTS; índice: ADD INDEX IF NOT EXISTS;
--   - FKs fora do CREATE: SQL dinâmico, só se o nome ainda não existir em
--     information_schema.TABLE_CONSTRAINTS (como V10/V11).
--   Correr duas vezes não dá erro. Atenção: se uma tabela ou coluna já
--   existir com outra definição, o IF NOT EXISTS ignora-a em silêncio
--   (ver o diagnóstico no fim).
--   NÃO TESTADO: o agente não executa scripts. A BD-3 (correr duas vezes
--   no schema de testes) fica para o dono.
--
-- Ordem de aplicação:
--   DEPOIS da V15 e ANTES do backend das indisponibilidades (que lê
--   treino_ausencia.id_indisponibilidade e indisponibilidade_jogo em
--   todos os tenants; sem a V16 dá erro de SQL). Primeiro no schema de
--   testes, depois em produção. Pode ir sozinha: é inerte para o backend
--   atual.
--
-- Reversão (manual, só pelo dono, e só DEPOIS de repor um backend
-- anterior; perde as indisponibilidades e as ligações aos jogos; as
-- ausências e as linhas INDISPONÍVEL criadas ficam como manuais):
--   DROP TABLE indisponibilidade_jogo;
--   ALTER TABLE treino_ausencia DROP FOREIGN KEY fk_treino_ausencia_indisponibilidade;
--   ALTER TABLE treino_ausencia DROP INDEX idx_treino_ausencia_indisponibilidade;
--   ALTER TABLE treino_ausencia DROP COLUMN id_indisponibilidade;
--   DROP TABLE indisponibilidade;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V15 aplicada NESTE schema e treino_ausencia existe
-- =====================================================================
-- Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V16_requer_V15' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de qualquer CREATE/ALTER.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'micro_ciclo'
      AND COLUMN_NAME = 'estado_antes_cancelamento') = 1
  AND
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino_ausencia'
      AND ENGINE = 'InnoDB') = 1,
  'DO 0',
  'SELECT 1 FROM ERRO_V16_requer_V15');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) indisponibilidade
-- =====================================================================
CREATE TABLE IF NOT EXISTS indisponibilidade (
  id             INT(11)     NOT NULL AUTO_INCREMENT,
  id_jogador     INT(11)     NOT NULL COMMENT 'jogador.id (o tenant vem do jogador)',
  data_inicio    INT(11)     NOT NULL COMMENT 'yyyymmdd, como treino.data',
  data_fim       INT(11)     NOT NULL COMMENT 'yyyymmdd, inclusiva',
  categoria      VARCHAR(20) NOT NULL COMMENT 'DOENCA / LESAO / ESCOLA / FAMILIA / OUTRO',
  motivo         VARCHAR(90) NULL DEFAULT NULL COMMENT 'Texto livre (os mesmos 90 de treino_ausencia.motivo)',
  estado         VARCHAR(20) NOT NULL COMMENT 'ATIVA / CANCELADA; terminada = ATIVA com data_fim < hoje',
  data_criacao   DATETIME    NOT NULL,
  data_alteracao DATETIME    NOT NULL,
  id_utilizador  INT(11)     NOT NULL COMMENT 'Conta do atleta (utilizadores.id)',
  PRIMARY KEY (id),
  KEY idx_indisponibilidade_jogador (id_jogador, estado, data_inicio),
  KEY idx_indisponibilidade_utilizador (id_utilizador),
  CONSTRAINT fk_indisponibilidade_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id),
  CONSTRAINT fk_indisponibilidade_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT chk_indisponibilidade_categoria CHECK (categoria IN ('DOENCA','LESAO','ESCOLA','FAMILIA','OUTRO')),
  CONSTRAINT chk_indisponibilidade_estado CHECK (estado IN ('ATIVA','CANCELADA')),
  CONSTRAINT chk_indisponibilidade_datas CHECK (data_fim >= data_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) treino_ausencia.id_indisponibilidade (coluna e índice)
-- =====================================================================
ALTER TABLE treino_ausencia
  ADD COLUMN IF NOT EXISTS id_indisponibilidade INT(11) NULL DEFAULT NULL COMMENT 'NULL = ausência indicada à mão; senão vem desta indisponibilidade (indisponibilidade.id)';

ALTER TABLE treino_ausencia
  ADD INDEX IF NOT EXISTS idx_treino_ausencia_indisponibilidade (id_indisponibilidade);

-- =====================================================================
-- 3) fk_treino_ausencia_indisponibilidade (sem cascata)
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino_ausencia'
      AND CONSTRAINT_NAME = 'fk_treino_ausencia_indisponibilidade'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE treino_ausencia ADD CONSTRAINT fk_treino_ausencia_indisponibilidade FOREIGN KEY (id_indisponibilidade) REFERENCES indisponibilidade (id)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 4) indisponibilidade_jogo
-- =====================================================================
-- A FK para jogo fica fora do CREATE: se jogo.id não for int(11) InnoDB
-- (não esperado; o dump diz que é), a tabela é criada na mesma e a FK não
-- (o código não depende dela). Nesse caso, o diagnóstico mostra a falta.
CREATE TABLE IF NOT EXISTS indisponibilidade_jogo (
  id_indisponibilidade INT(11)     NOT NULL COMMENT 'indisponibilidade.id',
  id_jogo              INT(11)     NOT NULL COMMENT 'jogo.id',
  estado               VARCHAR(20) NOT NULL COMMENT 'AUTOMATICA (linha INDISPONÍVEL posta pela indisponibilidade) / LIBERTADA (o treinador convocou) / RETIRADA (a linha automática saiu)',
  data_alteracao       DATETIME    NOT NULL,
  PRIMARY KEY (id_indisponibilidade, id_jogo),
  KEY idx_indisponibilidade_jogo_jogo (id_jogo, estado),
  CONSTRAINT fk_indisponibilidade_jogo_indisponibilidade FOREIGN KEY (id_indisponibilidade) REFERENCES indisponibilidade (id),
  CONSTRAINT chk_indisponibilidade_jogo_estado CHECK (estado IN ('AUTOMATICA','LIBERTADA','RETIRADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- ON DELETE CASCADE obrigatório: deleteJogo faz só DELETE FROM jogo.
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'indisponibilidade_jogo'
      AND CONSTRAINT_NAME = 'fk_indisponibilidade_jogo_jogo'
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
  'ALTER TABLE indisponibilidade_jogo ADD CONSTRAINT fk_indisponibilidade_jogo_jogo FOREIGN KEY (id_jogo) REFERENCES jogo (id) ON DELETE CASCADE',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 5) Estado das FKs (só leitura): fk_jogo_ok = 1; se 0, jogo.id não é
--    compatível e a FK para jogo não foi criada (o backend funciona, mas
--    apagar um jogo deixa ligações órfãs em indisponibilidade_jogo)
-- =====================================================================
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'indisponibilidade_jogo'
      AND CONSTRAINT_NAME = 'fk_indisponibilidade_jogo_jogo') AS fk_jogo_ok,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_ausencia'
      AND CONSTRAINT_NAME = 'fk_treino_ausencia_indisponibilidade') AS fk_ausencia_ok;

-- =====================================================================
-- 6) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('indisponibilidade','indisponibilidade_jogo');
--    -- InnoDB, utf8mb4_uca1400_ai_ci
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND (TABLE_NAME IN ('indisponibilidade','indisponibilidade_jogo')
--      OR (TABLE_NAME = 'treino_ausencia' AND COLUMN_NAME = 'id_indisponibilidade'))
--  ORDER BY TABLE_NAME, ORDINAL_POSITION;
--    -- treino_ausencia.id_indisponibilidade int(11) YES NULL
-- SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('indisponibilidade','indisponibilidade_jogo','treino_ausencia')
--  ORDER BY TABLE_NAME, CONSTRAINT_NAME;
--    -- indisponibilidade: jogador RESTRICT, utilizador RESTRICT
--    -- indisponibilidade_jogo: indisponibilidade RESTRICT, jogo CASCADE
--    -- treino_ausencia: + indisponibilidade RESTRICT (as 3 da V7 iguais)
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('indisponibilidade','indisponibilidade_jogo');
--    -- 4 CHECK
-- SELECT COUNT(*) AS total, COUNT(id_indisponibilidade) AS com_indisp FROM treino_ausencia;
--    -- com_indisp = 0 antes do backend novo
-- Se um ADD FOREIGN KEY falhar com errno 150: SHOW ENGINE INNODB STATUS
-- (LATEST FOREIGN KEY ERROR).
--
-- Teste da cascata (SÓ no schema de testes, numa transação que se desfaz
-- com ROLLBACK; não grava nada). Substituir <id_jogador> e
-- <id_utilizador> por um atleta e a sua conta, e <id_jogo> por um jogo
-- de testes:
-- START TRANSACTION;
-- INSERT INTO indisponibilidade (id_jogador, data_inicio, data_fim, categoria, estado, data_criacao, data_alteracao, id_utilizador)
-- VALUES (<id_jogador>, 20991201, 20991205, 'LESAO', 'ATIVA', NOW(), NOW(), <id_utilizador>);
-- INSERT INTO indisponibilidade_jogo (id_indisponibilidade, id_jogo, estado, data_alteracao)
-- VALUES (LAST_INSERT_ID(), <id_jogo>, 'AUTOMATICA', NOW());
-- DELETE FROM jogo WHERE id = <id_jogo>;                     -- tem de passar
-- SELECT COUNT(*) FROM indisponibilidade_jogo WHERE id_jogo = <id_jogo>;  -- 0
-- INSERT INTO indisponibilidade (id_jogador, data_inicio, data_fim, categoria, estado, data_criacao, data_alteracao, id_utilizador)
-- VALUES (<id_jogador>, 20991205, 20991201, 'LESAO', 'ATIVA', NOW(), NOW(), <id_utilizador>);  -- tem de falhar (4025)
-- ROLLBACK;
