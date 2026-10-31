-- Migration 2 : description des salles et unicite des adresses email (dialecte H2).
ALTER TABLE salles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);
