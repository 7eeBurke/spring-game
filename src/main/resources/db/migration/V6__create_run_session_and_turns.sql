-- Stage 14: run lifecycle and access, the pending enemy attack, and idempotent turn records.
--
-- All mechanical changes for one turn commit in one transaction while holding the run_session row
-- lock (SELECT ... FOR UPDATE), and state_version advances with every committed turn. No database
-- transaction stays open while a model is called; leases and fencing columns coordinate the phases
-- before and after.

CREATE TABLE run_session (
    run_id                  UUID        PRIMARY KEY REFERENCES game_run (id),
    status                  TEXT        NOT NULL CHECK (status IN ('INITIALIZING', 'ACTIVE', 'DEAD', 'VICTORIOUS')),
    -- SHA-256 (hex) of the client-generated run token. The token itself is never stored.
    access_token_hash       TEXT        NOT NULL UNIQUE CHECK (access_token_hash ~ '^[0-9a-f]{64}$'),
    creation_key            UUID        NOT NULL UNIQUE,
    state_version           BIGINT      NOT NULL CHECK (state_version >= 0),
    turn_number             INTEGER     NOT NULL CHECK (turn_number >= 0),
    -- The last enemy to act (round-robin cursor); both null before any enemy has acted.
    enemy_cursor_scene_id   UUID        REFERENCES scene_instance (id),
    enemy_cursor_entity_id  TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT run_session_cursor_pair CHECK ((enemy_cursor_scene_id IS NULL) = (enemy_cursor_entity_id IS NULL))
);

-- At most one incoming enemy attack awaiting the player's defense. Every field Stage 11 needs to
-- resolve the defense is stored, so a reload never rerolls or re-chooses the attack.
CREATE TABLE pending_attack (
    run_id                        UUID    PRIMARY KEY REFERENCES run_session (run_id),
    attack_ref                    TEXT    NOT NULL,
    scene_id                      UUID    NOT NULL,
    attacker_entity_id            TEXT    NOT NULL,
    option_code                   TEXT    NOT NULL,
    template                      TEXT    NOT NULL CHECK (template IN ('THRUST', 'HORIZONTAL_SWING', 'LOW_SWEEP',
                                          'OVERHEAD_STRIKE', 'QUICK_SLASH', 'HEAVY_SMASH', 'HOOK_AND_PULL', 'PROJECTED_ATTACK')),
    difficulty                    INTEGER NOT NULL,
    base_damage                   INTEGER NOT NULL CHECK (base_damage >= 0),
    weapon_trauma                 INTEGER NOT NULL CHECK (weapon_trauma >= 0),
    effectiveness                 TEXT    NOT NULL CHECK (effectiveness IN ('VERY_LOW', 'LOW', 'NORMAL', 'HIGH', 'VERY_HIGH')),
    attack_form_modifier          INTEGER NOT NULL,
    anatomy_interaction_modifier  INTEGER NOT NULL,
    target_body_part              TEXT    CHECK (target_body_part IN ('HEAD', 'NECK', 'CHEST', 'ABDOMEN', 'BACK',
                                          'LEFT_ARM', 'RIGHT_ARM', 'LEFT_LEG', 'RIGHT_LEG', 'HEART')),
    weapon_code                   TEXT    NOT NULL,
    created_turn                  INTEGER NOT NULL CHECK (created_turn >= 1),
    -- The mandatory Java-generated telegraph, stored so every reload shows exactly the same cue.
    cue_text                      TEXT    NOT NULL CHECK (length(btrim(cue_text)) > 0),
    narration_text                TEXT,
    narration_source              TEXT    CHECK (narration_source IN ('AI', 'FALLBACK')),
    CONSTRAINT pending_attack_narration_pair CHECK ((narration_text IS NULL) = (narration_source IS NULL)),
    CONSTRAINT pending_attack_attacker
        FOREIGN KEY (scene_id, attacker_entity_id) REFERENCES enemy_instance (scene_id, entity_local_id)
);

-- One row per state-changing turn request, keyed by the client's idempotency key.
CREATE TABLE run_turn (
    run_id                 UUID        NOT NULL REFERENCES run_session (run_id),
    request_key            UUID        NOT NULL,
    request_hash           TEXT        NOT NULL,
    base_state_version     BIGINT      NOT NULL,
    status                 TEXT        NOT NULL CHECK (status IN ('INTERPRETING', 'REJECTED', 'STALE',
                                       'MECHANICS_COMMITTED', 'COMPLETED')),
    lease_owner            UUID,
    lease_until            TIMESTAMPTZ,
    narration_owner        UUID,
    narration_lease_until  TIMESTAMPTZ,
    turn_number            INTEGER,
    enemy_scene_id         UUID,
    enemy_entity_id        TEXT,
    enemy_choice           TEXT,
    -- The confirmed mechanical result, enough to rebuild the full response after a crash.
    mechanics_summary      JSONB,
    response_status        INTEGER,
    -- The exact response body, stored as text so a replay is byte-for-byte identical.
    response               TEXT,
    created_at             TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, request_key),
    CONSTRAINT run_turn_number_unique UNIQUE (run_id, turn_number),
    CONSTRAINT run_turn_summary_when_committed
        CHECK (status NOT IN ('MECHANICS_COMMITTED', 'COMPLETED') OR (mechanics_summary IS NOT NULL AND turn_number IS NOT NULL)),
    CONSTRAINT run_turn_response_when_done
        CHECK (status NOT IN ('REJECTED', 'STALE', 'COMPLETED') OR (response IS NOT NULL AND response_status IS NOT NULL))
);

-- At most one unfinished turn per run, whatever its idempotency key.
CREATE UNIQUE INDEX run_turn_one_unfinished ON run_turn (run_id) WHERE status IN ('INTERPRETING', 'MECHANICS_COMMITTED');
