-- Migration demandée : description des salles et unicité des emails.
ALTER TABLE salles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);
