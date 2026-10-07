-- Stage 8: region instances, scene instances (relational metadata + JSONB dynamic state) and the
-- run's current player location.
--
-- Same-run integrity uses composite foreign keys on (id, run_id): a scene can only reference a
-- region of its own run, and the location can only reference a scene of its own run. That needs
-- UNIQUE (id, run_id) on the referenced tables, alongside their primary keys.
--
-- Left to the application: the location's zone (zones live inside the JSONB state), exit
-- destinations (validated when a complete region is generated) and definition-code format.

CREATE TABLE region_instance (
    id               UUID PRIMARY KEY,
    run_id           UUID NOT NULL REFERENCES game_run (id),
    definition_code  TEXT NOT NULL,
    CONSTRAINT region_instance_id_run_key UNIQUE (id, run_id)
);

CREATE TABLE scene_instance (
    id                    UUID    PRIMARY KEY,
    run_id                UUID    NOT NULL REFERENCES game_run (id),
    scene_kind            TEXT    NOT NULL CHECK (scene_kind IN ('HUB', 'REGION')),
    region_id             UUID,
    definition_code       TEXT    NOT NULL,
    scene_seed            BIGINT,
    discovered            BOOLEAN NOT NULL,
    revision              BIGINT  NOT NULL CHECK (revision >= 0),
    state_schema_version  INTEGER NOT NULL CHECK (state_schema_version >= 1),
    state                 JSONB   NOT NULL CHECK (jsonb_typeof(state) = 'object'),
    CONSTRAINT scene_instance_id_run_key UNIQUE (id, run_id),
    CONSTRAINT scene_instance_region_same_run
        FOREIGN KEY (region_id, run_id) REFERENCES region_instance (id, run_id),
    CONSTRAINT scene_instance_kind_shape CHECK (
        (scene_kind = 'HUB'    AND region_id IS NULL     AND scene_seed IS NULL) OR
        (scene_kind = 'REGION' AND region_id IS NOT NULL AND scene_seed IS NOT NULL))
);

CREATE TABLE run_world_state (
    run_id            UUID PRIMARY KEY REFERENCES game_run (id),
    current_scene_id  UUID NOT NULL,
    current_zone_id   TEXT NOT NULL,
    CONSTRAINT run_world_state_scene_same_run
        FOREIGN KEY (current_scene_id, run_id) REFERENCES scene_instance (id, run_id)
);
