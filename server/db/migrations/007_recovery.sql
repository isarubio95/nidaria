-- Código de recuperación de la cuenta. Se enseña una sola vez y aquí solo
-- se guarda su hash (scrypt, como la contraseña). Las cuentas creadas
-- antes no tienen: el jugador puede crearlo desde su ficha.
ALTER TABLE users ADD COLUMN recovery_hash TEXT;
