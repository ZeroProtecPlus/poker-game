-- V2__add_machine_id.sql
-- Adds machine identity columns to support machine-scoped game saves.
-- Applied inside DatabaseBootstrapper transaction (no explicit BEGIN/COMMIT).

ALTER TABLE game_state ADD COLUMN machine_id TEXT;
ALTER TABLE game_state ADD COLUMN human_chips INTEGER NOT NULL DEFAULT 0;
ALTER TABLE game_players ADD COLUMN ai INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_game_state_machine_id ON game_state(machine_id);
