-- V1__init.sql
-- Initial schema for poker game persistence

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS players (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL UNIQUE,
    chips INTEGER NOT NULL DEFAULT 10000,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS game_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL UNIQUE,
    pot INTEGER NOT NULL DEFAULT 0,
    community_cards_json TEXT NOT NULL DEFAULT '[]',
    remaining_deck_json TEXT NOT NULL DEFAULT '[]',
    dealer_index INTEGER NOT NULL DEFAULT 0,
    current_phase TEXT NOT NULL DEFAULT 'PREFLOP',
    timestamp TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS game_players (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_state_id INTEGER NOT NULL,
    player_id TEXT NOT NULL,
    name TEXT NOT NULL,
    hand_json TEXT NOT NULL DEFAULT '[]',
    chips INTEGER NOT NULL DEFAULT 0,
    current_bet INTEGER NOT NULL DEFAULT 0,
    folded INTEGER NOT NULL DEFAULT 0,
    all_in INTEGER NOT NULL DEFAULT 0,
    role TEXT NOT NULL DEFAULT 'NONE',
    FOREIGN KEY (game_state_id) REFERENCES game_state(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS actions_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_state_id INTEGER NOT NULL,
    player_id TEXT NOT NULL,
    action TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    phase TEXT NOT NULL,
    timestamp TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (game_state_id) REFERENCES game_state(id) ON DELETE CASCADE
);

-- Insert initial schema version
INSERT OR IGNORE INTO schema_version (version) VALUES (1);
