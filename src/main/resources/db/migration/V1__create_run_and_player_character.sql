-- Stage 7: run identity and the run's player character.
-- Static content is referenced by definition code only; there are no content-definition tables.
-- Generation policy (stat total/profiles, Fated distribution, HP formula, starting belt) is not
-- enforced here; only stable structural ranges and enum-name sets are.

CREATE TABLE game_run (
    id        UUID   PRIMARY KEY,
    run_seed  BIGINT NOT NULL
);

CREATE TABLE player_character (
    run_id        UUID    PRIMARY KEY REFERENCES game_run (id),  -- primary key: one character per run
    name          TEXT    NOT NULL,
    might         INTEGER NOT NULL CHECK (might      BETWEEN 3 AND 10),
    agility       INTEGER NOT NULL CHECK (agility    BETWEEN 3 AND 10),
    perception    INTEGER NOT NULL CHECK (perception BETWEEN 3 AND 10),
    arcana        INTEGER NOT NULL CHECK (arcana     BETWEEN 3 AND 10),
    resolve       INTEGER NOT NULL CHECK (resolve    BETWEEN 3 AND 10),
    fated         INTEGER NOT NULL CHECK (fated      BETWEEN 0 AND 5),
    max_hp        INTEGER NOT NULL CHECK (max_hp >= 1),
    current_hp    INTEGER NOT NULL,
    passive_code  TEXT    NOT NULL,
    ability_code  TEXT    NOT NULL,
    CONSTRAINT player_character_current_hp_range CHECK (current_hp BETWEEN 0 AND max_hp)
);

CREATE TABLE player_body_part (
    run_id     UUID NOT NULL REFERENCES player_character (run_id),
    body_part  TEXT NOT NULL CHECK (body_part IN ('HEAD', 'NECK', 'CHEST', 'ABDOMEN', 'BACK',
                                                  'LEFT_ARM', 'RIGHT_ARM', 'LEFT_LEG', 'RIGHT_LEG', 'HEART')),
    severity   TEXT NOT NULL CHECK (severity IN ('HEALTHY', 'INJURED', 'WOUNDED', 'CRIPPLED', 'DESTROYED')),
    PRIMARY KEY (run_id, body_part)
);

CREATE TABLE player_tool_belt_entry (
    run_id           UUID    NOT NULL REFERENCES player_character (run_id),
    slot_index       INTEGER NOT NULL CHECK (slot_index BETWEEN 0 AND 4),
    entry_kind       TEXT    NOT NULL CHECK (entry_kind IN ('WEAPON', 'ITEM')),
    definition_code  TEXT    NOT NULL,
    PRIMARY KEY (run_id, slot_index)
);
