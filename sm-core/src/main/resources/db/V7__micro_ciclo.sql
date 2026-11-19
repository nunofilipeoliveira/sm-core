-- =====================================================================
-- SM-CORE | Micro Ciclo: plano semanal de treinos, convocatória,
--           ausências indicadas pelo atleta, histórico e notificações
-- =====================================================================
-- Alvo: MariaDB 11.8 (sintaxe IF NOT EXISTS de MariaDB; não é MySQL).
-- Melhoria: MicroCiclo, Entrega B (desenho 02-desenho.md, secção 3.2,
-- tarefa DBA-2). Depende da V6 (notificacao -> funcionalidade).
--
-- Objetivo:
--   Guardar o micro ciclo (semana de treinos por clube), os treinos de
--   cada equipa, a convocatória, as ausências indicadas pelos atletas, o
--   histórico das alterações e as notificações na aplicação.
--
-- O que cria (por esta ordem; todas InnoDB, utf8mb4_uca1400_ai_ci, como
-- a V6 e performance_config; inteiros INT(11) com sinal, como as PK de
-- epoca, escalao_epoca, jogador, presencas e utilizadores):
--   micro_ciclo
--     - uq_micro_ciclo_tenant_semana  UNIQUE (tenant_id, data_inicio)
--     - fk_micro_ciclo_epoca          -> epoca(id)
--     - fk_micro_ciclo_utilizador     -> utilizadores(id)
--     - chk_micro_ciclo_estado        RASCUNHO/PUBLICADO/CANCELADO
--     Nota: tem tenant_id embora pertença a epoca (exceção deliberada à
--     convenção, para o UNIQUE por clube e semana; desenho §3.2/§11).
--   treino
--     - fk_treino_micro_ciclo         -> micro_ciclo(id)  ON DELETE CASCADE
--     - fk_treino_escalao_epoca       -> escalao_epoca(id) (sem cascade)
--     - fk_treino_presenca            -> presencas(id)    ON DELETE SET NULL
--     - fk_treino_utilizador_aprovacao, fk_treino_utilizador_criacao
--     - uq_treino_presenca            UNIQUE (id_presenca; vários NULL ok)
--     - idx_treino_equipa_data, idx_treino_micro_ciclo
--     - chk_treino_tipo, chk_treino_estado
--   treino_convocado                  PK (id_treino, id_jogador)
--     - fk_treino_convocado_treino    -> treino(id)       ON DELETE CASCADE
--     - fk_treino_convocado_jogador, fk_treino_convocado_utilizador
--     - idx_treino_convocado_jogador
--     - chk_treino_convocado_origem, chk_treino_convocado_estado
--   treino_ausencia
--     - fk_treino_ausencia_treino     -> treino(id)       ON DELETE CASCADE
--     - fk_treino_ausencia_jogador, fk_treino_ausencia_utilizador
--     - uq_treino_ausencia            UNIQUE (id_treino, id_jogador)
--     - chk_treino_ausencia_categoria, chk_treino_ausencia_estado,
--       chk_treino_ausencia_pendente
--     - motivo VARCHAR(90) (aprovado pelo dono: "Categoria: texto" cabe
--       nos 100 de presenca_jogador.motivo)
--   micro_ciclo_historico
--     - fk_micro_ciclo_historico_micro_ciclo -> micro_ciclo(id) ON DELETE CASCADE
--     - fk_micro_ciclo_historico_treino      -> treino(id) ON DELETE SET NULL
--     - fk_micro_ciclo_historico_utilizador  -> utilizadores(id)
--     - idx_micro_ciclo_historico_treino
--   notificacao
--     - fk_notificacao_utilizador     -> utilizadores(id)
--     - fk_notificacao_funcionalidade -> funcionalidade(id)  (V6)
--     - fk_notificacao_treino         -> treino(id)       ON DELETE CASCADE
--     - fk_notificacao_jogador        -> jogador(id)
--     - idx_notificacao_utilizador (id_utilizador, data_leitura, datacriacao)
--     - tipo sem CHECK (eventos futuros)
--
-- Impacto nos dados existentes:
--   Nenhum. Só cria tabelas novas e não insere linhas. presencas não muda
--   (a ligação está em treino.id_presenca). Efeitos das FKs novas sobre
--   o código atual:
--   - apagar uma presença (deletePresenca) passa a pôr treino.id_presenca
--     a NULL nos treinos ligados (ON DELETE SET NULL), sem erro;
--   - apagar uma equipa (escalao_epoca) com treinos passa a falhar pela
--     FK fk_treino_escalao_epoca (desejado, desenho §3.3).
--   Retenção: ausências, motivos e notificações ficam guardados sem
--   limpeza automática (decisão do dono).
--
-- Idempotência:
--   CREATE TABLE IF NOT EXISTS, com índices, FKs e CHECKs dentro do
--   CREATE (atómico: a tabela nasce com tudo ou não nasce). Correr duas
--   vezes não dá erro. Atenção: se alguma destas tabelas já existir com
--   outra definição, o IF NOT EXISTS ignora-a em silêncio (diagnóstico
--   no fim).
--
-- Ordem de aplicação:
--   DEPOIS da V6 (no mesmo schema; o passo 0 pára o script se faltar)
--   e ANTES do backend da Entrega B (BK-7 em diante).
--   Primeiro no schema de testes, depois em produção. Pode ir sozinho:
--   o backend atual não usa estas tabelas.
--
-- Reversão (manual, pelo dono; apaga os dados do Micro Ciclo). Ordem:
-- filhas primeiro.
--   DROP TABLE notificacao;
--   DROP TABLE micro_ciclo_historico;
--   DROP TABLE treino_ausencia;
--   DROP TABLE treino_convocado;
--   DROP TABLE treino;
--   DROP TABLE micro_ciclo;
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Pré-condição: V6 aplicada NESTE schema
-- =====================================================================
-- Sem funcionalidade (InnoDB, id int(11) com sinal), a FK de notificacao
-- falha com errno 150 depois de as outras 5 tabelas já estarem criadas.
-- Se a condição falhar, o PREPARE dá o erro
--   "Table '<schema>.ERRO_V7_requer_V6_funcionalidade_aplicada' doesn't exist"
-- e o script pára aqui (no cliente mysql; no DBeaver, se o tratamento de
-- erros do script estiver em "parar"), antes de criar qualquer tabela.
-- shortcut: tabela inexistente em vez de SIGNAL, porque SIGNAL fora de um
-- bloco BEGIN...END exige mudar o DELIMITER no cliente.
SET @sql = IF(
  (SELECT COUNT(*)
     FROM information_schema.TABLES t
     JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
    WHERE t.TABLE_SCHEMA = DATABASE()
      AND t.TABLE_NAME = 'funcionalidade'
      AND t.ENGINE = 'InnoDB'
      AND c.COLUMN_NAME = 'id'
      AND c.COLUMN_TYPE = 'int(11)'
      AND c.COLUMN_KEY = 'PRI') = 1,
  'DO 0',
  'SELECT 1 FROM ERRO_V7_requer_V6_funcionalidade_aplicada');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 1) micro_ciclo
-- =====================================================================
CREATE TABLE IF NOT EXISTS micro_ciclo (
  id                    INT(11)      NOT NULL AUTO_INCREMENT,
  tenant_id             INT(11)      NOT NULL,
  id_epoca              INT(11)      NOT NULL COMMENT 'Época ativa na criação (epoca.id)',
  data_inicio           INT(11)      NOT NULL COMMENT 'Segunda-feira da semana, yyyymmdd',
  estado                VARCHAR(20)  NOT NULL COMMENT 'RASCUNHO / PUBLICADO / CANCELADO',
  motivo_cancelamento   VARCHAR(255) NULL DEFAULT NULL,
  datacriacao           DATETIME     NOT NULL,
  id_utilizador_criacao INT(11)      NOT NULL COMMENT 'utilizadores.id',
  PRIMARY KEY (id),
  UNIQUE KEY uq_micro_ciclo_tenant_semana (tenant_id, data_inicio),
  CONSTRAINT fk_micro_ciclo_epoca FOREIGN KEY (id_epoca) REFERENCES epoca (id),
  CONSTRAINT fk_micro_ciclo_utilizador FOREIGN KEY (id_utilizador_criacao) REFERENCES utilizadores (id),
  CONSTRAINT chk_micro_ciclo_estado CHECK (estado IN ('RASCUNHO','PUBLICADO','CANCELADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 2) treino
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino (
  id                      INT(11)      NOT NULL AUTO_INCREMENT,
  id_micro_ciclo          INT(11)      NOT NULL,
  id_equipa               INT(11)      NOT NULL COMMENT 'escalao_epoca.id (como presencas.id_equipa)',
  tipo                    VARCHAR(20)  NOT NULL COMMENT 'ESCALAO / FISICO / OUTRO',
  tipo_descricao          VARCHAR(50)  NULL DEFAULT NULL COMMENT 'Obrigatório no código quando OUTRO',
  sub_grupo               VARCHAR(50)  NULL DEFAULT NULL,
  data                    INT(11)      NOT NULL COMMENT 'yyyymmdd',
  hora_inicio             VARCHAR(5)   NOT NULL COMMENT 'HH:MM',
  hora_fim                VARCHAR(5)   NULL DEFAULT NULL COMMENT 'HH:MM; obrigatória no código quando ESCALAO',
  local_treino            VARCHAR(100) NULL DEFAULT NULL,
  observacoes             VARCHAR(500) NULL DEFAULT NULL,
  estado                  VARCHAR(20)  NOT NULL COMMENT 'RASCUNHO / PENDENTE / APROVADO / REJEITADO / CANCELADO; realizado = APROVADO com id_presenca',
  motivo_estado           VARCHAR(255) NULL DEFAULT NULL COMMENT 'Motivo da rejeição ou do cancelamento',
  versao                  INT(11)      NOT NULL DEFAULT 1 COMMENT 'Controlo de concorrência otimista',
  data_aprovacao          DATETIME     NULL DEFAULT NULL COMMENT 'Última aprovação',
  id_utilizador_aprovacao INT(11)      NULL DEFAULT NULL,
  id_presenca             INT(11)      NULL DEFAULT NULL COMMENT 'Presença registada a partir deste treino (presencas.id)',
  datacriacao             DATETIME     NOT NULL,
  id_utilizador_criacao   INT(11)      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_treino_presenca (id_presenca),
  KEY idx_treino_equipa_data (id_equipa, data),
  KEY idx_treino_micro_ciclo (id_micro_ciclo, data),
  CONSTRAINT fk_treino_micro_ciclo FOREIGN KEY (id_micro_ciclo) REFERENCES micro_ciclo (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_escalao_epoca FOREIGN KEY (id_equipa) REFERENCES escalao_epoca (id),
  CONSTRAINT fk_treino_presenca FOREIGN KEY (id_presenca) REFERENCES presencas (id) ON DELETE SET NULL,
  CONSTRAINT fk_treino_utilizador_aprovacao FOREIGN KEY (id_utilizador_aprovacao) REFERENCES utilizadores (id),
  CONSTRAINT fk_treino_utilizador_criacao FOREIGN KEY (id_utilizador_criacao) REFERENCES utilizadores (id),
  CONSTRAINT chk_treino_tipo CHECK (tipo IN ('ESCALAO','FISICO','OUTRO')),
  CONSTRAINT chk_treino_estado CHECK (estado IN ('RASCUNHO','PENDENTE','APROVADO','REJEITADO','CANCELADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 3) treino_convocado (ligação treino <-> jogador)
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino_convocado (
  id_treino               INT(11)     NOT NULL,
  id_jogador              INT(11)     NOT NULL,
  origem                  VARCHAR(20) NOT NULL COMMENT 'OMISSAO / ACRESCENTADO',
  estado                  VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1 = convocado / 0 = retirado',
  data_alteracao          DATETIME    NULL DEFAULT NULL,
  id_utilizador_alteracao INT(11)     NULL DEFAULT NULL,
  PRIMARY KEY (id_treino, id_jogador),
  KEY idx_treino_convocado_jogador (id_jogador),
  CONSTRAINT fk_treino_convocado_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_convocado_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id),
  CONSTRAINT fk_treino_convocado_utilizador FOREIGN KEY (id_utilizador_alteracao) REFERENCES utilizadores (id),
  CONSTRAINT chk_treino_convocado_origem CHECK (origem IN ('OMISSAO','ACRESCENTADO')),
  CONSTRAINT chk_treino_convocado_estado CHECK (estado IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 4) treino_ausencia (uma linha por treino e jogador)
-- =====================================================================
CREATE TABLE IF NOT EXISTS treino_ausencia (
  id                   INT(11)     NOT NULL AUTO_INCREMENT,
  id_treino            INT(11)     NOT NULL,
  id_jogador           INT(11)     NOT NULL,
  categoria            VARCHAR(20) NOT NULL COMMENT 'DOENCA / LESAO / ESCOLA / FAMILIA / OUTRO',
  motivo               VARCHAR(90) NULL DEFAULT NULL COMMENT 'Texto livre (até 90, para caber em presenca_jogador.motivo)',
  estado               VARCHAR(20) NOT NULL COMMENT 'ATIVA / ANULADA / DESCARTADA',
  pendente_confirmacao VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '1 = indicada antes de uma alteração essencial do treino',
  data_indicacao       DATETIME    NOT NULL COMMENT 'Última indicação',
  data_alteracao       DATETIME    NOT NULL COMMENT 'Última mudança',
  id_utilizador        INT(11)     NOT NULL COMMENT 'Conta do atleta (utilizadores.id)',
  PRIMARY KEY (id),
  UNIQUE KEY uq_treino_ausencia (id_treino, id_jogador),
  CONSTRAINT fk_treino_ausencia_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_treino_ausencia_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id),
  CONSTRAINT fk_treino_ausencia_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT chk_treino_ausencia_categoria CHECK (categoria IN ('DOENCA','LESAO','ESCOLA','FAMILIA','OUTRO')),
  CONSTRAINT chk_treino_ausencia_estado CHECK (estado IN ('ATIVA','ANULADA','DESCARTADA')),
  CONSTRAINT chk_treino_ausencia_pendente CHECK (pendente_confirmacao IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 5) micro_ciclo_historico (no estilo de presencas_historico)
-- =====================================================================
CREATE TABLE IF NOT EXISTS micro_ciclo_historico (
  id             INT(11)      NOT NULL AUTO_INCREMENT,
  id_micro_ciclo INT(11)      NOT NULL,
  id_treino      INT(11)      NULL DEFAULT NULL COMMENT 'NULL depois de o treino ser apagado (a linha fica)',
  data           DATETIME     NOT NULL,
  id_utilizador  INT(11)      NOT NULL,
  alteracao      VARCHAR(500) NOT NULL COMMENT 'Ex.: Hora de início | 18:00 => 18:30',
  PRIMARY KEY (id),
  KEY idx_micro_ciclo_historico_treino (id_treino),
  CONSTRAINT fk_micro_ciclo_historico_micro_ciclo FOREIGN KEY (id_micro_ciclo) REFERENCES micro_ciclo (id) ON DELETE CASCADE,
  CONSTRAINT fk_micro_ciclo_historico_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE SET NULL,
  CONSTRAINT fk_micro_ciclo_historico_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 6) notificacao (genérica, por funcionalidade de origem)
-- =====================================================================
CREATE TABLE IF NOT EXISTS notificacao (
  id                INT(11)      NOT NULL AUTO_INCREMENT,
  id_utilizador     INT(11)      NOT NULL COMMENT 'Destinatário (utilizadores.id)',
  id_funcionalidade INT(11)      NOT NULL COMMENT 'Funcionalidade de origem (escondida se estiver desligada)',
  tipo              VARCHAR(30)  NOT NULL COMMENT 'Ex.: AUSENCIA_INDICADA, TREINO_ALTERADO (sem CHECK: eventos futuros)',
  id_treino         INT(11)      NULL DEFAULT NULL,
  id_jogador        INT(11)      NULL DEFAULT NULL,
  texto             VARCHAR(255) NOT NULL COMMENT 'Composto na criação, sem o motivo da ausência',
  datacriacao       DATETIME     NOT NULL,
  data_leitura      DATETIME     NULL DEFAULT NULL COMMENT 'NULL = não lida',
  PRIMARY KEY (id),
  KEY idx_notificacao_utilizador (id_utilizador, data_leitura, datacriacao),
  CONSTRAINT fk_notificacao_utilizador FOREIGN KEY (id_utilizador) REFERENCES utilizadores (id),
  CONSTRAINT fk_notificacao_funcionalidade FOREIGN KEY (id_funcionalidade) REFERENCES funcionalidade (id),
  CONSTRAINT fk_notificacao_treino FOREIGN KEY (id_treino) REFERENCES treino (id) ON DELETE CASCADE,
  CONSTRAINT fk_notificacao_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- =====================================================================
-- 7) Diagnóstico (opcional, só leitura) - correr à mão depois de aplicar
-- =====================================================================
-- SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('micro_ciclo','treino','treino_convocado','treino_ausencia',
--                       'micro_ciclo_historico','notificacao');
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('micro_ciclo','treino','treino_convocado','treino_ausencia',
--                       'micro_ciclo_historico','notificacao')
--  ORDER BY TABLE_NAME, ORDINAL_POSITION;
-- SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('micro_ciclo','treino','treino_convocado','treino_ausencia',
--                       'micro_ciclo_historico','notificacao')
--  ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
-- SELECT CONSTRAINT_NAME, TABLE_NAME, REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
--   FROM information_schema.REFERENTIAL_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('micro_ciclo','treino','treino_convocado','treino_ausencia',
--                       'micro_ciclo_historico','notificacao')
--  ORDER BY TABLE_NAME, CONSTRAINT_NAME;
-- SELECT TABLE_NAME, CONSTRAINT_NAME, CHECK_CLAUSE
--   FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('micro_ciclo','treino','treino_convocado','treino_ausencia');
--
-- Diagnóstico do errno 150 em notificacao (correr no mesmo schema):
-- SELECT DATABASE();                                -- confirmar o schema
-- SHOW TABLES LIKE 'funcionalidade%';               -- V6 aplicada aqui?
-- SHOW ENGINE INNODB STATUS;                        -- ver "LATEST FOREIGN KEY ERROR"
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id'
--    AND TABLE_NAME IN ('utilizadores','funcionalidade','treino','jogador');
--                                                   -- esperado: int(11), PRI
-- SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('utilizadores','funcionalidade','treino','jogador');
-- SELECT TABLE_NAME, CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'fk_notificacao%';
