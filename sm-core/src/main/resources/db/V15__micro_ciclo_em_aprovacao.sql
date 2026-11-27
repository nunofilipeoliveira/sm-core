-- =====================================================================
-- SM-CORE | Micro Ciclo: estado EM_APROVACAO e estado antes do
--           cancelamento
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo v9 (desenho 02-desenho-v9.md, secção 3, tarefa
-- BD-v9-1). Depende da V14 (o passo 0 pára o script se faltar). V6-V14
-- não mudam.
--
-- Objetivo:
--   - Novo estado do micro ciclo, EM_APROVACAO (submetido aos
--     treinadores, ainda invisível ao atleta), entre RASCUNHO e PUBLICADO.
--   - Guardar de que estado foi cancelado um micro ciclo, para saber se o
--     atleta o chegou a ver (R257).
--
-- O que cria / altera:
--   micro_ciclo
--     - coluna estado_antes_cancelamento VARCHAR(20) NULL (herda
--       utf8mb4_uca1400_ai_ci da tabela, V7). Sem CHECK: o desenho não o
--       pede (valores PUBLICADO / EM_APROVACAO, escritos pelo código).
--     - chk_micro_ciclo_estado recriado com
--       ('RASCUNHO','EM_APROVACAO','PUBLICADO','CANCELADO')
--       >>> PASSO 2, COMENTADO: DECISÃO DO DONO (ver abaixo) <<<
--
-- PASSO 2 COMENTADO (decisão do dono):
--   Recriar o CHECK exige DROP CONSTRAINT. Não apaga dados, mas as regras
--   do agente DBA proíbem escrever DROP ativo num script. O passo 2 está
--   escrito por inteiro em comentário: o dono só tem de o descomentar (ou
--   correr à mão) depois de o aprovar. O DROP e o ADD estão no MESMO
--   ALTER, por isso não há intervalo sem CHECK; é idempotente (só corre se
--   o CHECK_CLAUSE ainda não tiver EM_APROVACAO).
--   SEM O PASSO 2, O BACKEND v9 NÃO FUNCIONA: "Submeter" grava
--   estado='EM_APROVACAO' e o CHECK atual recusa-o (erro 4025). O passo 3
--   mostra se o CHECK já está pronto.
--
-- Impacto nos dados existentes:
--   Nenhum UPDATE. As linhas existentes ficam com estado_antes_cancelamento
--   NULL, que se lê como "cancelado a partir de PUBLICADO" (até à v8 só se
--   cancelava a partir de PUBLICADO). Os três estados atuais continuam
--   válidos no CHECK alargado, por isso a validação das linhas existentes
--   (feita no ADD CONSTRAINT) passa. O backend v8 continua a funcionar com
--   a V15: nunca escreve EM_APROVACAO e ignora a coluna.
--   Locks: ADD COLUMN no fim é instantâneo; o ALTER do CHECK pode copiar
--   micro_ciclo (tabela pequena: uma linha por semana e clube). Cada ALTER
--   espera por transações abertas em micro_ciclo (metadata lock).
--
-- Idempotência:
--   - coluna: ADD COLUMN IF NOT EXISTS;
--   - CHECK (passo 2): SQL dinâmico, só se o CHECK_CLAUSE em
--     information_schema.CHECK_CONSTRAINTS ainda não tiver EM_APROVACAO.
--   Correr duas vezes não dá erro. Atenção: se a coluna já existir com
--   outra definição, o IF NOT EXISTS ignora-a em silêncio (diagnóstico).
--
-- Ordem de aplicação:
--   DEPOIS da V14 e ANTES do backend v9 (que lê a coluna e grava
--   EM_APROVACAO): os passos 1 E 2. Primeiro no schema de testes, depois
--   em produção. Pode ir sozinho: é inerte para o backend v8.
--
-- Reversão (manual, só pelo dono, e só sem micro ciclos EM_APROVACAO; o
-- ADD CONSTRAINT falha com 4025 se houver algum):
--   ALTER TABLE micro_ciclo
--     DROP CONSTRAINT chk_micro_ciclo_estado,
--     ADD CONSTRAINT chk_micro_ciclo_estado CHECK (estado IN ('RASCUNHO','PUBLICADO','CANCELADO'));
--   ALTER TABLE micro_ciclo DROP COLUMN estado_antes_cancelamento;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V14 aplicada NESTE schema e o CHECK da V7 existe
-- =====================================================================
-- Se falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V15_requer_V14' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de qualquer ALTER.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente (como a V7).
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treino_confirmacao') = 1
  AND
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'micro_ciclo'
      AND CONSTRAINT_NAME = 'chk_micro_ciclo_estado'
      AND CONSTRAINT_TYPE = 'CHECK') = 1,
  'DO 0',
  'SELECT 1 FROM ERRO_V15_requer_V14');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) micro_ciclo.estado_antes_cancelamento
-- =====================================================================
ALTER TABLE micro_ciclo
  ADD COLUMN IF NOT EXISTS estado_antes_cancelamento VARCHAR(20) NULL DEFAULT NULL COMMENT 'PUBLICADO ou EM_APROVACAO; NULL = cancelado antes da v9 (a partir de PUBLICADO)';

-- =====================================================================
-- 2) chk_micro_ciclo_estado com EM_APROVACAO
--    >>> DECISÃO DO DONO: descomentar as 10 linhas abaixo depois de
--    aprovar o DROP CONSTRAINT (estrutural, não apaga dados). <<<
-- =====================================================================
 SET @sql = IF(
   (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'micro_ciclo'
       AND CONSTRAINT_NAME = 'chk_micro_ciclo_estado'
       AND CHECK_CLAUSE LIKE '%EM_APROVACAO%') = 0,
   'ALTER TABLE micro_ciclo DROP CONSTRAINT chk_micro_ciclo_estado, ADD CONSTRAINT chk_micro_ciclo_estado CHECK (estado IN (''RASCUNHO'',''EM_APROVACAO'',''PUBLICADO'',''CANCELADO''))',
   'DO 0');
 PREPARE stmt FROM @sql;
 EXECUTE stmt;
 DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) Estado do CHECK (só leitura): ck_pronto = 1 depois do passo 2
-- =====================================================================
SELECT CONSTRAINT_NAME, CHECK_CLAUSE,
       CHECK_CLAUSE LIKE '%EM_APROVACAO%' AS ck_pronto   -- 0 = falta o passo 2; o backend v9 não pode ir
  FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND TABLE_NAME = 'micro_ciclo'
   AND CONSTRAINT_NAME = 'chk_micro_ciclo_estado';

-- =====================================================================
-- 4) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'micro_ciclo'
--    AND COLUMN_NAME IN ('estado','estado_antes_cancelamento');
--    -- estado varchar(20) NO; estado_antes_cancelamento varchar(20) YES NULL
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'micro_ciclo';
--    -- um só chk_micro_ciclo_estado, com os 4 valores
-- SELECT estado, COUNT(*) AS total, COUNT(estado_antes_cancelamento) AS com_antes
--   FROM micro_ciclo GROUP BY estado;               -- sem EM_APROVACAO; com_antes = 0 antes do backend v9
--
-- Teste do CHECK (SÓ no schema de testes, numa transação com ROLLBACK).
-- Substituir <id_epoca> e <id_utilizador> por ids do mesmo tenant e
-- <tenant>/<segunda> por um par (tenant_id, data_inicio) sem uso:
-- START TRANSACTION;
-- INSERT INTO micro_ciclo (tenant_id, id_epoca, data_inicio, estado, datacriacao, id_utilizador_criacao)
-- VALUES (<tenant>, <id_epoca>, <segunda>, 'EM_APROVACAO', NOW(), <id_utilizador>);  -- tem de passar
-- INSERT INTO micro_ciclo (tenant_id, id_epoca, data_inicio, estado, datacriacao, id_utilizador_criacao)
-- VALUES (<tenant>, <id_epoca>, <segunda> + 7, 'XPTO', NOW(), <id_utilizador>);      -- tem de falhar (4025)
-- ROLLBACK;
