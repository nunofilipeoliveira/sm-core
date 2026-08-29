  -- =====================================================================
  -- SM-CORE | Jogo: Modo Cronómetro, Timeline de Eventos, Substituições,
  -- Tempo de Jogo e Exclusão por Cartão Azul (2 minutos)
  -- =====================================================================
  -- Alvos: MySQL 8.x / MariaDB 10.x
  -- Este script é a migração de base de dados UMA-ONLY (aplicar uma vez
  -- por base de dados: dev / docker / prod).
  --
  -- NOTA: os campos novos em jogo_jogador são esperados pelo backend a
  -- partir desta versão. Aplicar a migração antes de fazer deploy do sm-core.

  -- =====================================================================
  -- 1) Novas colunas em jogo_jogador
  -- ---------------------------------------------------------------------
  -- titular             -> jogador pertence ao 5 inicial da equipa
  -- em_campo            -> está em campo neste momento (controlado por eventos)
  -- excluido_ate_segundos -> tempo absoluto de jogo (seg) até ao qual o
  --                          jogador está excluído (cartão azul = 2 min)
  -- tempo_jogo_segundos -> tempo de jogo corrigido manualmente (segundos)
  -- tempo_manual        -> 1 se tempo_jogo_segundos foi definido à mão
  -- =====================================================================
  ALTER TABLE jogo_jogador
    ADD COLUMN titular TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Jogador no cinco inicial',
    ADD COLUMN em_campo TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Está em campo neste momento',
    ADD COLUMN excluido_ate_segundos INT NULL COMMENT 'Tempo absoluto de jogo (seg) até ao qual está excluído (cartão azul = 2 min)',
    ADD COLUMN tempo_jogo_segundos INT NOT NULL DEFAULT 0 COMMENT 'Tempo de jogo corrigido manualmente (segundos)',
    ADD COLUMN tempo_manual TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 quando tempo_jogo_segundos foi corrigido manualmente';

  -- =====================================================================
  -- 2) Configuração do jogo (modo normal / cronómetro)
  -- ---------------------------------------------------------------------
  -- modo_registo: NORMAL | CRONOMETRO
  -- duracao_parte_minutos: minutos de cada parte
  -- numero_partes: número de partes (2 por defeito)
  -- num_jogadores_iniciais: jogadores que começam em campo (5 por defeito)
  -- duracao_exclusao_azul_segundos: exclusão do cartão azul (120 s)
  -- tempo_atual_segundos: posição do cronómetro persistida (para retomar)
  -- =====================================================================
  CREATE TABLE IF NOT EXISTS jogo_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_jogo INT NOT NULL,
    modo_registo VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    duracao_parte_minutos INT NOT NULL DEFAULT 25,
    numero_partes INT NOT NULL DEFAULT 2,
    num_jogadores_iniciais INT NOT NULL DEFAULT 5,
    duracao_exclusao_azul_segundos INT NOT NULL DEFAULT 120,
    tempo_atual_segundos INT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_jogo_config_id_jogo UNIQUE (id_jogo),
    CONSTRAINT fk_config_jogo FOREIGN KEY (id_jogo) REFERENCES jogo (id) ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

  -- =====================================================================
  -- 3) Timeline de eventos do jogo
  -- ---------------------------------------------------------------------
  -- tipo_evento:
  --   GOLO / GOLO_SOFRIDO  -> detalhe: normal | p | ld | pp | up
  --   AMARELO / AZUL / VERMELHO
  --   FALTA / ASSISTENCIA / RECUPERACAO_BOLA / PERDA_BOLA / REMATE
  --   PENALTY_FALHADO / PENALTY_DEFESA / LD_FALHADO / LD_DEFESA
  --   SUBSTITUICAO          -> id_jogador = quem ENTRA; id_jogador_secundario = quem SAI
  --   INICIO_JOGO / INICIO_PARTE / FIM_PARTE / FIM_JOGO
  --   CORRECAO_TEMPO        -> detalhe guarda o tempo corrigido (MM:SS)
  -- tempo_evento : valor exibido no cronómetro (MM:SS) no momento do evento
  -- tempo_segundos: tempo absoluto de jogo em segundos (acumulado entre partes),
  --                 usado para ordenar a timeline e calcular tempos de jogo.
  -- =====================================================================
  CREATE TABLE IF NOT EXISTS jogo_evento (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_jogo INT NOT NULL,
    id_parte INT NOT NULL DEFAULT 1,
    tempo_evento VARCHAR(8) NOT NULL DEFAULT '00:00',
    tempo_segundos INT NOT NULL DEFAULT 0,
    tipo_evento VARCHAR(40) NOT NULL,
    id_jogador INT NULL,
    id_jogador_secundario INT NULL,
    detalhe VARCHAR(60) NULL,
    obs VARCHAR(255) NULL,
    id_equipa INT NULL COMMENT '0 = equipa nossa, 1 = equipa adversária (determina golos_equipa vs golos_equipa_adv)',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evento_jogo FOREIGN KEY (id_jogo) REFERENCES jogo (id) ON DELETE CASCADE,
    CONSTRAINT fk_evento_jogador FOREIGN KEY (id_jogador) REFERENCES jogador (id) ON DELETE SET NULL,
    CONSTRAINT fk_evento_jogador_sec FOREIGN KEY (id_jogador_secundario) REFERENCES jogador (id) ON DELETE SET NULL,
    INDEX idx_evento_jogo_tempo (id_jogo, tempo_segundos, id)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;