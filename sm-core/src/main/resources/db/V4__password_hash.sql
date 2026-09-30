-- =====================================================================
-- SM-CORE | Passwords encriptadas (BCrypt) + migracao lazy
-- =====================================================================
-- Alvos: MySQL 8.x / MariaDB 10.x
-- Este script e a migracao de base de dados UMA-ONLY (aplicar uma vez
-- por base de dados: dev / docker / prod).
--
-- Contexto:
--   Ate a versao anterior a coluna utilizadores.password guardava a
--   password em TEXTO SIMPLES. A partir desta versao o backend guarda
--   apenas hashes BCrypt (60 caracteres).
--
--   As passwords antigas continuam a funcionar: no primeiro login com
--   sucesso o backend deteta que o valor guardado nao e um hash BCrypt,
--   valida-o em texto simples e reescreve a coluna ja encriptada
--   (migracao lazy - ver LoginHelper.Dologin e PasswordUtils).
--
-- Aplicar ANTES do deploy do sm-core.
-- =====================================================================

-- =====================================================================
-- 1) Alargar a coluna password para caber o hash BCrypt (60 caracteres)
-- ---------------------------------------------------------------------
-- varchar(45)/varchar(50) e insuficiente: trunca o hash e o login falha.
-- =====================================================================
ALTER TABLE utilizadores MODIFY COLUMN password VARCHAR(255) NULL;

-- NOTA: se preferires manter a coluna obrigatoria, usa em vez da linha
-- anterior (so falha se existirem registos com password NULL):
-- ALTER TABLE utilizadores MODIFY COLUMN password VARCHAR(255) NOT NULL;

-- =====================================================================
-- 2) Diagnostico (opcional): passwords ainda em texto simples
-- ---------------------------------------------------------------------
-- Ja encriptadas -> comeca por $2a$/$2b$/$2y$
-- =====================================================================
-- SELECT id, user, tenant_id,
--        (password LIKE '$2%') AS ja_encriptada
-- FROM utilizadores;
