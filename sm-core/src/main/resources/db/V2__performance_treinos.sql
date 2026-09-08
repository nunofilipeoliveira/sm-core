  -- =====================================================================
  -- SM-CORE | Performance dos atletas nos treinos (classificação 1 a 5)
  -- =====================================================================
  -- Alvos: MySQL 8.x / MariaDB 10.x
  -- Este script é a migração de base de dados UMA-ONLY (aplicar uma vez
  -- por base de dados: dev / docker / prod).
  --
  -- NOTA: o backend (PresencaHelper, PerformanceWS) espera a coluna
  -- classificacao em presenca_jogador a partir desta versão. Aplicar a
  -- migração antes de fazer deploy do sm-core.

  -- =====================================================================
  -- 1) Nova coluna em presenca_jogador
  -- ---------------------------------------------------------------------
  -- classificacao -> classificação de desempenho do atleta no treino
  --                  (1 a 5). NULL enquanto o atleta ainda não foi avaliado.
  -- =====================================================================
  ALTER TABLE presenca_jogador
    ADD COLUMN classificacao INT NULL COMMENT 'Classificação de desempenho do atleta no treino (1 a 5); NULL se ainda não avaliado';

  -- =====================================================================
  -- 2) Índice de suporte às consultas de performance
  -- ---------------------------------------------------------------------
  -- Agregações por equipa e intervalo de datas (presencas.id_equipa +
  -- presencas.`data`). Nota: presenca_jogador.id_jogador já fica indexado
  -- pela FK existente (presenca_jogador_jogador_FK). Se já existir um
  -- índice equivalente em presencas, remover esta instrução antes de
  -- aplicar.
  -- =====================================================================
  CREATE INDEX idx_presencas_equipa_data ON presencas (id_equipa, `data`);
