-- =====================================================================
-- SM-CORE | Micro Ciclo: eventos sem equipa (grupos de evento)
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v5 (desenho 02-desenho-v5.md, secção 4, tarefa
-- DBA-v5-1). Depende da V7 e da V9 (tabela treino).
-- V6-V9 não mudam.
--
-- Objetivo:
--   Permitir no micro ciclo eventos que não são de uma equipa, mas de um
--   grupo definido pelo clube (grupo_evento), e marcar eventos para
--   serem aprovados automaticamente ao publicar.
--
-- O que cria / altera (por esta ordem):
--   grupo_evento (nova, raiz, com tenant_id)
--     - uq_grupo_evento_tenant_nome  UNIQUE (tenant_id, nome); com
--       utf8mb4_uca1400_ai_ci é insensível a maiúsculas E a acentos
--       ('Física' = 'fisica')
--     - fk_grupo_evento_utilizador   -> utilizadores(id)
--     - chk_grupo_evento_estado      estado IN ('0','1')
--   treino
--     - id_equipa  passa de INT(11) NOT NULL a INT(11) NULL (MODIFY),
--                  mantendo a FK fk_treino_escalao_epoca e o comentário
--     - id_grupo   INT(11) NULL, FK fk_treino_grupo_evento ->
--                  grupo_evento(id), sem cascade
--     - aprovar_ao_publicar VARCHAR(1) NOT NULL DEFAULT '0',
--                  CHECK chk_treino_aprovar_ao_publicar IN ('0','1')
--     - CHECK chk_treino_equipa_ou_grupo: exatamente um de id_equipa /
--                  id_grupo preenchido
--
-- MODIFY com a FK existente:
--   O MariaDB/InnoDB aceita mudar só a nulidade de uma coluna filha de
--   uma FK, porque o tipo (INT(11) com sinal) não muda; o erro 1832
--   ("Cannot change column used in a foreign key constraint") é para
--   mudanças de tipo, e o 1830 para passar a NOT NULL com SET NULL. Não
--   foi testado (os agentes não executam). Se o passo 2 falhar com 1832,
--   o script pára aí: grupo_evento fica criada e treino fica igual. A
--   alternativa do desenho (apagar a FK, MODIFY, recriar a FK) implica
--   um DROP FOREIGN KEY, que fica para o dono decidir; comandos no fim,
--   em comentário.
--
-- Impacto nos dados existentes:
--   Nenhum UPDATE. As linhas atuais de treino têm id_equipa preenchido e
--   ficam com id_grupo NULL e aprovar_ao_publicar '0', por isso cumprem
--   os CHECKs. O backend v4 continua a funcionar: insere sempre
--   id_equipa e ignora as colunas novas.
--   Locks: tabela treino pequena, mas o script faz vários ALTER seguidos:
--   - o MODIFY da nulidade reconstrói a tabela (INPLACE ou COPY);
--   - ADD COLUMN no fim é instantâneo;
--   - ADD FOREIGN KEY e ADD CHECK podem copiar a tabela (bloqueia
--     escritas durante a cópia).
--   Cada ALTER espera por transações abertas em treino (metadata lock).
--   Aplicar fora das horas de uso.
--
-- Idempotência:
--   - grupo_evento: CREATE TABLE IF NOT EXISTS.
--   - MODIFY: só se information_schema.COLUMNS.IS_NULLABLE = 'NO'.
--   - colunas: ADD COLUMN IF NOT EXISTS.
--   - FK e CHECKs: só se não existirem em
--     information_schema.TABLE_CONSTRAINTS (SQL dinâmico, como V5/V8/V9).
--   Correr duas vezes não dá erro.
--   Atenção: se uma coluna já existir com outra definição, o IF NOT
--   EXISTS ignora-a em silêncio (ver diagnóstico no fim).
--
-- Ordem de aplicação:
--   DEPOIS da V9 e ANTES do backend v5. Primeiro no schema de testes,
--   depois em produção. Pode ir sozinho: o backend v4 continua a
--   funcionar.
--
-- Reversão (manual, só pelo dono, e só sem eventos gravados: voltar
-- id_equipa a NOT NULL falha se houver treinos com id_equipa NULL).
-- Ordem:
--   ALTER TABLE treino DROP CONSTRAINT chk_treino_equipa_ou_grupo;
--   ALTER TABLE treino DROP CONSTRAINT chk_treino_aprovar_ao_publicar;
--   ALTER TABLE treino DROP FOREIGN KEY fk_treino_grupo_evento;
--   ALTER TABLE treino DROP COLUMN id_grupo;
--   ALTER TABLE treino DROP COLUMN aprovar_ao_publicar;
--   ALTER TABLE treino MODIFY id_equipa INT(11) NOT NULL COMMENT 'escalao_epoca.id (como presencas.id_equipa)';
--   DROP TABLE grupo_evento;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 1) grupo_evento
-- =====================================================================
CREATE TABLE IF NOT EXISTS grupo_evento (
  id                    INT(11)     NOT NULL AUTO_INCREMENT,
  tenant_id             INT(11)     NOT NULL,
  nome                  VARCHAR(60) NOT NULL,
  cor                   VARCHAR(7)  NULL DEFAULT NULL COMMENT 'Formato #RRGGBB; NULL = cor automática',
  estado                VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1 = ativo / 0 = inativo',
  datacriacao           DATETIME    NOT NULL,
  id_utilizador_criacao INT(11)     NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id),
  UNIQUE KEY uq_grupo_evento_tenant_nome (tenant_id, nome),
  CONSTRAINT fk_grupo_evento_utilizador FOREIGN KEY (id_utilizador_criacao) REFERENCES utilizadores (id),
  CONSTRAINT chk_grupo_evento_estado CHECK (estado IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) treino.id_equipa passa a aceitar NULL (só se ainda for NOT NULL)
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND COLUMN_NAME = 'id_equipa'
      AND IS_NULLABLE = 'NO') = 1,
  'ALTER TABLE treino MODIFY id_equipa INT(11) NULL DEFAULT NULL COMMENT ''escalao_epoca.id (como presencas.id_equipa); NULL = evento de um grupo (id_grupo)''',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) treino.id_grupo + FK
-- =====================================================================
ALTER TABLE treino
  ADD COLUMN IF NOT EXISTS id_grupo INT(11) NULL DEFAULT NULL COMMENT 'Grupo do evento (grupo_evento.id); NULL = treino de equipa';

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'fk_treino_grupo_evento'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE treino ADD CONSTRAINT fk_treino_grupo_evento FOREIGN KEY (id_grupo) REFERENCES grupo_evento (id)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 4) treino.aprovar_ao_publicar + CHECK
-- =====================================================================
ALTER TABLE treino
  ADD COLUMN IF NOT EXISTS aprovar_ao_publicar VARCHAR(1) NOT NULL DEFAULT '0' COMMENT '1 = o evento passa a APROVADO ao publicar o micro ciclo';

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'chk_treino_aprovar_ao_publicar'
      AND CONSTRAINT_TYPE = 'CHECK') = 0,
  'ALTER TABLE treino ADD CONSTRAINT chk_treino_aprovar_ao_publicar CHECK (aprovar_ao_publicar IN (''0'',''1''))',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 5) CHECK: equipa ou grupo, exatamente um
-- =====================================================================
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'chk_treino_equipa_ou_grupo'
      AND CONSTRAINT_TYPE = 'CHECK') = 0,
  'ALTER TABLE treino ADD CONSTRAINT chk_treino_equipa_ou_grupo CHECK ((id_equipa IS NOT NULL AND id_grupo IS NULL) OR (id_equipa IS NULL AND id_grupo IS NOT NULL))',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 6) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo_evento';
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino'
--    AND COLUMN_NAME IN ('id_equipa','id_grupo','aprovar_ao_publicar');
--    -- id_equipa int(11) YES NULL; id_grupo int(11) YES NULL;
--    -- aprovar_ao_publicar varchar(1) NO '0'
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
--   FROM information_schema.TABLE_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN ('treino','grupo_evento')
--  ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;
--    -- fk_treino_escalao_epoca continua lá; cada nome novo só uma vez
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN ('treino','grupo_evento');
-- SELECT COUNT(*) AS total,
--        SUM(id_equipa IS NULL) AS sem_equipa,
--        SUM(id_grupo IS NOT NULL) AS com_grupo,
--        SUM(aprovar_ao_publicar <> '0') AS aprovar
--   FROM treino;                                   -- 0 / 0 / 0 antes do backend v5
-- Se o passo 2 falhar: SHOW ENGINE INNODB STATUS (LATEST FOREIGN KEY ERROR).
--
-- Teste do CHECK (SÓ no schema de testes; tem de FALHAR com o erro 4025
-- "CONSTRAINT chk_treino_equipa_ou_grupo failed"; não grava nada).
-- Substituir <id_micro_ciclo> e <id_utilizador> por ids existentes:
-- INSERT INTO treino (id_micro_ciclo, id_equipa, id_grupo, tipo, data, hora_inicio,
--                     estado, datacriacao, id_utilizador_criacao)
-- VALUES (<id_micro_ciclo>, NULL, NULL, 'OUTRO', 20261116, '18:00',
--         'RASCUNHO', NOW(), <id_utilizador>);
--
-- Alternativa do desenho, se o passo 2 der o erro 1832 (DECISÃO DO DONO:
-- inclui um DROP da FK; depois voltar a correr este script completo):
-- ALTER TABLE treino DROP FOREIGN KEY fk_treino_escalao_epoca;
-- ALTER TABLE treino MODIFY id_equipa INT(11) NULL DEFAULT NULL COMMENT 'escalao_epoca.id (como presencas.id_equipa); NULL = evento de um grupo (id_grupo)';
-- ALTER TABLE treino ADD CONSTRAINT fk_treino_escalao_epoca FOREIGN KEY (id_equipa) REFERENCES escalao_epoca (id);
