-- Stage 13: the run's finalised character introduction, written once and then loaded as stored.
-- Introductions written by the AI and by the deterministic fallback are stored the same way. No
-- model metadata, provider request, reasoning or secret is stored.

CREATE TABLE character_introduction (
    run_id          UUID    PRIMARY KEY REFERENCES player_character (run_id),
    intro_text      TEXT    NOT NULL CHECK (length(btrim(intro_text)) > 0),
    source          TEXT    NOT NULL CHECK (source IN ('AI', 'FALLBACK')),
    prompt_version  INTEGER NOT NULL CHECK (prompt_version >= 1)
);
