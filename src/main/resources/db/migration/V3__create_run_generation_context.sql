-- Stage 9: the immutable generation-context snapshot used to generate a run's world.
-- One row per run; its existence also marks the run's world as initialized.
-- The JSONB document is read and written only by GenerationContextCodec.

CREATE TABLE run_generation_context (
    run_id          UUID    PRIMARY KEY REFERENCES game_run (id),
    schema_version  INTEGER NOT NULL CHECK (schema_version >= 1),
    snapshot        JSONB   NOT NULL CHECK (jsonb_typeof(snapshot) = 'object')
);
