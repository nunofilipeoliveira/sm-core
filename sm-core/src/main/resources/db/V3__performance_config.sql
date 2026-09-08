  -- =====================================================================
  -- SM-CORE | Configuração de performance por equipa
  -- =====================================================================
  -- Alvos: MySQL 8.x / MariaDB 10.x
  -- Este script é a migração de base de dados UMA-ONLY (aplicar uma vez
  -- por base de dados: dev / docker / prod).
  --
  -- NOTA: o backend (PerformanceWS, PerformanceHelper, PresencaHelper)
  -- espera esta tabela a partir desta versão. Aplicar a migração antes de
  -- fazer deploy do sm-core.

  -- =====================================================================
  -- 1) Tabela de configuração de performance por equipa
  -- ---------------------------------------------------------------------
  -- Uma linha por equipa (escalao_epoca.id). Se a equipa não tiver linha,
  -- o backend assume a configuração por omissão (tudo permitido), mantendo
  -- o comportamento das versões anteriores.
  -- =====================================================================
  CREATE TABLE IF NOT EXISTS performance_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_equipa INT NOT NULL COMMENT 'Equipa a que a configuração se aplica (escalao_epoca.id)',
    permitir_registo TINYINT NOT NULL DEFAULT 1 COMMENT '1 = permite avaliar os atletas (classificação 1 a 5) ao marcar presenças; 0 = as classificações recebidas são ignoradas',
    permitir_visualizacao TINYINT NOT NULL DEFAULT 1 COMMENT '1 = permite consultar os indicadores de performance da equipa; 0 = os endpoints devolvem dados vazios',
    UNIQUE KEY uq_performance_config_equipa (id_equipa)
  );