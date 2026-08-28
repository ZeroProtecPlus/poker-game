-- V3__cleanup_stale_machine_id.sql
-- Removes orphaned game_state rows from V1 schema (machine_id IS NULL)
-- and any game_players rows referencing them.
-- Applied inside DatabaseBootstrapper transaction (no explicit BEGIN/COMMIT).

DELETE FROM game_state WHERE machine_id IS NULL;
DELETE FROM game_players WHERE game_state_id NOT IN (SELECT id FROM game_state);