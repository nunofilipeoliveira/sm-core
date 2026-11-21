-- =====================================================================
-- SM-CORE | Micro Ciclo: ligar os físicos avulsos existentes ao treino
--           principal (migração v6) - PRÉ-VISUALIZAÇÃO
-- =====================================================================
-- Alvo: MariaDB 11.8. Melhoria: MicroCiclo v6 (desenho 02-desenho-v6.md,
-- secção 3.2, tarefa BD-v6-2). Depende da V11 (o passo 0 pára o script
-- se faltar). V6-V11 não mudam.
--
-- ESTADO DESTE FICHEIRO: só tem os blocos 0 (parâmetros e pré-condição),
-- 1 (pré-visualização, só leitura) e 3 (diagnóstico). O bloco 2
-- (aplicação: INSERT no histórico, DELETE de treino_convocado e UPDATE de
-- treino) NÃO foi escrito: as regras do agente DBA proíbem UPDATE e
-- DELETE ("só estrutura"). Fica para o dono autorizar explicitamente ou
-- escrever. O esboço está no desenho §3.2, passo 2.
--
-- Objetivo:
--   Mostrar ao dono, por tenant, quantos físicos avulsos (FISICO sem
--   id_treino_principal) se podem ligar ao treino ESCALAO da mesma equipa
--   e do mesmo dia, e porque é que os restantes ficam avulsos.
--
-- O que cria: nada. Só SELECTs (e SET de variáveis de sessão).
--
-- Impacto nos dados existentes: nenhum (só leitura).
--
-- Critérios (desenho §3.2; uma só definição, repetida em 1a e 1b):
--   Físico f: tipo FISICO, id_treino_principal NULL, id_grupo NULL,
--     datacriacao < @corte.
--   Motivos para ficar avulso, o primeiro que falha, por esta ordem:
--     semana_terminada  segunda do micro ciclo + 6 dias < @hoje
--     realizado         f.id_presenca preenchido
--     cancelado         f.estado = 'CANCELADO'
--     com_ausencias     há treino_ausencia ATIVA no físico
--     sem_candidato     0 candidatos
--     varios_candidatos mais do que 1 candidato
--     estado_incompativel  ordem RASCUNHO 1 < PENDENTE 2 < APROVADO 3:
--                       liga se ordem(f) <= ordem(p); REJEITADO só com
--                       REJEITADO
--   Candidato p: mesmo micro ciclo (logo, mesmo tenant), mesma equipa,
--     mesmo dia, tipo ESCALAO, id_grupo NULL, id_treino_principal NULL,
--     estado <> 'CANCELADO'.
--
-- "Hoje" (@hoje): o backend usa Europe/Lisbon; o MariaDB do container
-- pode estar em UTC. @hoje usa CONVERT_TZ para Europe/Lisbon, que só
-- funciona com as tabelas de fusos carregadas (mysql.time_zone_name);
-- sem elas, CONVERT_TZ devolve NULL e cai para CURDATE() (a data do
-- servidor). A diferença só existe entre as 00:00 e a 01:00 de Lisboa e
-- só afeta a semana que termina nesse domingo. O dono pode fixar @hoje à
-- mão.
--
-- Ordem de aplicação:
--   DEPOIS da V11 e do backend v6 publicado (e de preferência do frontend
--   v6). Correr no schema de testes e depois em produção. Pode correr-se
--   várias vezes (só leitura).
--
-- Reversão: não há nada a reverter (só leitura).
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 0) Parâmetros e pré-condição
-- =====================================================================
-- @corte: data e hora da publicação do backend v6 ('AAAA-MM-DD HH:MM:SS').
-- Obrigatório: com NULL o script pára no passo 0b. Só entram os físicos
-- criados antes, para nunca ligar um avulso criado de propósito depois.
SET @corte = NULL;
SET @hoje  = COALESCE(DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', 'Europe/Lisbon')), CURDATE());

-- 0a) V11 aplicada neste schema (coluna e FK). Se falhar:
--     "Table '<schema>.ERRO_V12_requer_V11_aplicada' doesn't exist".
-- shortcut: tabela inexistente em vez de SIGNAL (como a V7), para não
-- mudar o DELIMITER no cliente.
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treino'
      AND CONSTRAINT_NAME = 'fk_treino_principal'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 1,
  'DO 0',
  'SELECT 1 FROM ERRO_V12_requer_V11_aplicada');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 0b) @corte preenchido. Se falhar:
--     "Table '<schema>.ERRO_V12_defina_corte' doesn't exist".
SET @sql = IF(@corte IS NOT NULL AND CAST(@corte AS DATETIME) IS NOT NULL,
  'DO 0',
  'SELECT 1 FROM ERRO_V12_defina_corte');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT @corte AS corte, @hoje AS hoje,
       CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', 'Europe/Lisbon') AS agora_lisboa,  -- NULL = sem tabelas de fusos
       NOW() AS agora_servidor;

-- =====================================================================
-- 1) Pré-visualização (só leitura)
-- =====================================================================
-- 1a) Contagens por tenant e motivo
WITH fis AS (
  SELECT mf.tenant_id, f.id AS id_fisico, f.estado,
         STR_TO_DATE(mf.data_inicio, '%Y%m%d') + INTERVAL 6 DAY < @hoje AS semana_terminada,
         f.id_presenca IS NOT NULL AS realizado,
         f.estado = 'CANCELADO' AS cancelado,
         EXISTS (SELECT 1 FROM treino_ausencia ta
                  WHERE ta.id_treino = f.id AND ta.estado = 'ATIVA') AS com_ausencias,
         (SELECT COUNT(*) FROM treino p
           WHERE p.id_micro_ciclo = f.id_micro_ciclo AND p.id_equipa = f.id_equipa
             AND p.`data` = f.`data` AND p.tipo = 'ESCALAO' AND p.id_grupo IS NULL
             AND p.id_treino_principal IS NULL AND p.estado <> 'CANCELADO') AS n_candidatos,
         (SELECT MIN(p.id) FROM treino p
           WHERE p.id_micro_ciclo = f.id_micro_ciclo AND p.id_equipa = f.id_equipa
             AND p.`data` = f.`data` AND p.tipo = 'ESCALAO' AND p.id_grupo IS NULL
             AND p.id_treino_principal IS NULL AND p.estado <> 'CANCELADO') AS id_principal
    FROM treino f
    JOIN micro_ciclo mf ON mf.id = f.id_micro_ciclo
   WHERE f.tipo = 'FISICO' AND f.id_treino_principal IS NULL AND f.id_grupo IS NULL
     AND f.datacriacao < @corte
), cls AS (
  SELECT fis.*,
         CASE
           WHEN semana_terminada THEN 'semana_terminada'
           WHEN realizado        THEN 'realizado'
           WHEN cancelado        THEN 'cancelado'
           WHEN com_ausencias    THEN 'com_ausencias'
           WHEN n_candidatos = 0 THEN 'sem_candidato'
           WHEN n_candidatos > 1 THEN 'varios_candidatos'
           WHEN (fis.estado = 'REJEITADO' AND p.estado = 'REJEITADO')
             OR (FIELD(fis.estado, 'RASCUNHO','PENDENTE','APROVADO') > 0
                 AND FIELD(fis.estado, 'RASCUNHO','PENDENTE','APROVADO')
                     <= FIELD(p.estado, 'RASCUNHO','PENDENTE','APROVADO'))
                                 THEN 'ligavel'
           ELSE 'estado_incompativel'
         END AS motivo
    FROM fis
    LEFT JOIN treino p ON p.id = fis.id_principal
)
SELECT tenant_id,
       COUNT(*)                           AS total,
       SUM(motivo = 'ligavel')            AS ligaveis,
       SUM(motivo = 'semana_terminada')   AS semana_terminada,
       SUM(motivo = 'realizado')          AS realizado,
       SUM(motivo = 'cancelado')          AS cancelado,
       SUM(motivo = 'com_ausencias')      AS com_ausencias,
       SUM(motivo = 'sem_candidato')      AS sem_candidato,
       SUM(motivo = 'varios_candidatos')  AS varios_candidatos,
       SUM(motivo = 'estado_incompativel') AS estado_incompativel
  FROM cls
 GROUP BY tenant_id
 ORDER BY tenant_id;

-- 1b) Lista dos ligáveis (os mesmos critérios de 1a, repetidos)
WITH fis AS (
  SELECT mf.tenant_id, f.id AS id_fisico, f.id_equipa, f.`data`, f.hora_inicio, f.hora_fim, f.estado,
         STR_TO_DATE(mf.data_inicio, '%Y%m%d') + INTERVAL 6 DAY < @hoje AS semana_terminada,
         f.id_presenca IS NOT NULL AS realizado,
         f.estado = 'CANCELADO' AS cancelado,
         EXISTS (SELECT 1 FROM treino_ausencia ta
                  WHERE ta.id_treino = f.id AND ta.estado = 'ATIVA') AS com_ausencias,
         (SELECT COUNT(*) FROM treino p
           WHERE p.id_micro_ciclo = f.id_micro_ciclo AND p.id_equipa = f.id_equipa
             AND p.`data` = f.`data` AND p.tipo = 'ESCALAO' AND p.id_grupo IS NULL
             AND p.id_treino_principal IS NULL AND p.estado <> 'CANCELADO') AS n_candidatos,
         (SELECT MIN(p.id) FROM treino p
           WHERE p.id_micro_ciclo = f.id_micro_ciclo AND p.id_equipa = f.id_equipa
             AND p.`data` = f.`data` AND p.tipo = 'ESCALAO' AND p.id_grupo IS NULL
             AND p.id_treino_principal IS NULL AND p.estado <> 'CANCELADO') AS id_principal
    FROM treino f
    JOIN micro_ciclo mf ON mf.id = f.id_micro_ciclo
   WHERE f.tipo = 'FISICO' AND f.id_treino_principal IS NULL AND f.id_grupo IS NULL
     AND f.datacriacao < @corte
)
SELECT fis.tenant_id, fis.id_fisico,
       COALESCE(NULLIF(ee.nome, ''), es.nome)        AS equipa,
       fis.`data`, fis.hora_inicio, fis.hora_fim,
       CONCAT(fis.estado, ' -> ', p.estado)           AS estado,
       p.id AS id_principal, p.hora_inicio AS hora_inicio_principal, p.hora_fim AS hora_fim_principal
  FROM fis
  JOIN treino p ON p.id = fis.id_principal
  LEFT JOIN escalao_epoca ee ON ee.id = fis.id_equipa
  LEFT JOIN escalao es ON es.id = ee.id_escalao
 WHERE NOT fis.semana_terminada AND NOT fis.realizado AND NOT fis.cancelado
   AND NOT fis.com_ausencias AND fis.n_candidatos = 1
   AND ((fis.estado = 'REJEITADO' AND p.estado = 'REJEITADO')
        OR (FIELD(fis.estado, 'RASCUNHO','PENDENTE','APROVADO') > 0
            AND FIELD(fis.estado, 'RASCUNHO','PENDENTE','APROVADO')
                <= FIELD(p.estado, 'RASCUNHO','PENDENTE','APROVADO')))
 ORDER BY fis.tenant_id, fis.`data`, fis.hora_inicio, fis.id_fisico;

-- 1c) Guarda: tem de dar 0 antes de aplicar (a migração corre uma só vez)
SELECT COUNT(*) AS ja_migrado
  FROM micro_ciclo_historico
 WHERE alteracao LIKE '%(migração v6)%';

-- =====================================================================
-- 2) Aplicação - NÃO ESCRITA (ver "ESTADO DESTE FICHEIRO" no topo)
-- =====================================================================

-- =====================================================================
-- 3) Diagnóstico (opcional, só leitura) - depois da aplicação
-- =====================================================================
-- SELECT m.tenant_id, COUNT(*) AS ligados
--   FROM treino f JOIN micro_ciclo m ON m.id = f.id_micro_ciclo
--  WHERE f.id_treino_principal IS NOT NULL
--  GROUP BY m.tenant_id;                            -- = ligaveis de 1a
-- SELECT COUNT(*) FROM treino_convocado tc
--   JOIN treino t ON t.id = tc.id_treino
--  WHERE t.id_treino_principal IS NOT NULL;         -- 0
-- SELECT COUNT(*) FROM treino f JOIN treino p ON p.id = f.id_treino_principal
--  WHERE f.estado <> p.estado;                      -- 0 (estado espelhado)
-- SELECT COUNT(*) FROM micro_ciclo_historico
--  WHERE alteracao LIKE '%(migração v6)%';          -- = ligados
-- Correr de novo 1a: ligaveis = 0 (os ligados já não são avulsos).
