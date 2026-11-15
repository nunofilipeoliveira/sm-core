-- =====================================================================
-- SM-CORE | Login de atleta: ligação conta <-> jogador
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: teste1 (desenho 02-desenho.md, secção 3, tarefa DBA-1).
--
-- Objetivo:
--   Permitir contas com perfil 'ATLETA' ligadas a um único jogador.
--
-- O que cria:
--   utilizadores
--     - coluna  id_jogador INT(11) NULL      (jogador ligado à conta)
--     - índice  uq_utilizadores_id_jogador   UNIQUE (uma conta por jogador;
--               vários NULL são permitidos)
--     - FK      fk_utilizadores_jogador      id_jogador -> jogador(id),
--               sem cascade
--   utilizadores_ativar
--     - coluna  id_jogador INT(11) NULL      (jogador do convite ATLETA)
--     - índice  idx_utilizadores_ativar_id_jogador (não único: o resetPWD
--               cria linhas pendentes repetidas para o mesmo user)
--     - FK      fk_utilizadores_ativar_jogador id_jogador -> jogador(id)
--   Coluna perfil: sem alteração (varchar(100); 'ATLETA' cabe; sem CHECK).
--
-- Impacto nos dados existentes:
--   Nenhum. Só estrutura: as colunas novas ficam NULL em todas as linhas,
--   sem UPDATE/DELETE. As FKs e o UNIQUE não falham porque a coluna é
--   nova (tudo NULL). Tabelas pequenas: ALTER rápido. Os inserts
--   existentes em utilizadores/utilizadores_ativar usam lista de colunas,
--   por isso não são afetados. O código atual ignora as colunas novas.
--
-- Idempotência:
--   Colunas e índices com IF NOT EXISTS. As FKs são criadas só se não
--   existirem em information_schema.TABLE_CONSTRAINTS (SQL dinâmico),
--   para não depender do comportamento de "FOREIGN KEY IF NOT EXISTS"
--   com constraint nomeada. Correr duas vezes não dá erro.
--   Atenção: se a coluna id_jogador já existir com outra definição, o
--   IF NOT EXISTS ignora-a em silêncio (ver diagnóstico no fim).
--
-- Ordem de aplicação:
--   ANTES do backend B1 (fase F0). Primeiro no schema de testes, depois
--   em produção. Pode ir sozinho: o backend atual continua a funcionar.
--
-- Reversão (manual, pelo dono; normalmente não é necessária, porque as
-- colunas são NULL e o código antigo as ignora). Ordem: FK, índice, coluna.
--   ALTER TABLE utilizadores DROP FOREIGN KEY fk_utilizadores_jogador;
--   ALTER TABLE utilizadores DROP INDEX uq_utilizadores_id_jogador;
--   ALTER TABLE utilizadores DROP COLUMN id_jogador;
--   ALTER TABLE utilizadores_ativar DROP FOREIGN KEY fk_utilizadores_ativar_jogador;
--   ALTER TABLE utilizadores_ativar DROP INDEX idx_utilizadores_ativar_id_jogador;
--   ALTER TABLE utilizadores_ativar DROP COLUMN id_jogador;
--
-- Nota de convenção: as FKs existentes seguem o padrão
-- <tabela>_<referencia>_FK (ex.: jogador_historico_jogador_FK). Aqui
-- usam-se os nomes definidos no desenho aprovado (fk_*/uq_*/idx_*).
-- =====================================================================

-- =====================================================================
-- 1) utilizadores.id_jogador
-- =====================================================================
ALTER TABLE utilizadores
  ADD COLUMN IF NOT EXISTS id_jogador INT(11) NULL DEFAULT NULL COMMENT 'Jogador ligado à conta (só perfil ATLETA)';

ALTER TABLE utilizadores
  ADD UNIQUE INDEX IF NOT EXISTS uq_utilizadores_id_jogador (id_jogador);

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'utilizadores'
      AND CONSTRAINT_NAME = 'fk_utilizadores_jogador'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE utilizadores ADD CONSTRAINT fk_utilizadores_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 2) utilizadores_ativar.id_jogador
-- =====================================================================
ALTER TABLE utilizadores_ativar
  ADD COLUMN IF NOT EXISTS id_jogador INT(11) NULL DEFAULT NULL COMMENT 'Jogador a ligar quando o convite é ATLETA';

ALTER TABLE utilizadores_ativar
  ADD INDEX IF NOT EXISTS idx_utilizadores_ativar_id_jogador (id_jogador);

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'utilizadores_ativar'
      AND CONSTRAINT_NAME = 'fk_utilizadores_ativar_jogador'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE utilizadores_ativar ADD CONSTRAINT fk_utilizadores_ativar_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id)',
  'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 3) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('utilizadores','utilizadores_ativar')
--    AND COLUMN_NAME = 'id_jogador';
-- SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('utilizadores','utilizadores_ativar')
--    AND COLUMN_NAME = 'id_jogador';
-- SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND CONSTRAINT_NAME IN ('fk_utilizadores_jogador','fk_utilizadores_ativar_jogador');
