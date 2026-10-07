-- Stage 12: the mechanical state of each placed enemy, generated with the world before play.
--
-- Identity is (scene_id, entity_local_id): the scene plus the scene-local ID of its SceneEntity,
-- whose placement (zone, visibility) stays in the scene-state JSON. That the entity exists in the
-- scene's state is checked by the application, since entities live inside the JSONB document.
-- Static enemy definitions (anatomy, traits, behaviour, attack options) are not stored: rows refer
-- to them by definition code, like every other piece of run state.

CREATE TABLE enemy_instance (
    scene_id         UUID    NOT NULL REFERENCES scene_instance (id),
    entity_local_id  TEXT    NOT NULL,
    definition_code  TEXT    NOT NULL,
    might            INTEGER NOT NULL CHECK (might      BETWEEN 3 AND 10),
    agility          INTEGER NOT NULL CHECK (agility    BETWEEN 3 AND 10),
    perception       INTEGER NOT NULL CHECK (perception BETWEEN 3 AND 10),
    arcana           INTEGER NOT NULL CHECK (arcana     BETWEEN 3 AND 10),
    resolve          INTEGER NOT NULL CHECK (resolve    BETWEEN 3 AND 10),
    max_hp           INTEGER NOT NULL CHECK (max_hp >= 1),
    current_hp       INTEGER NOT NULL,
    weapon_code      TEXT    NOT NULL,
    PRIMARY KEY (scene_id, entity_local_id),
    CONSTRAINT enemy_instance_current_hp_range CHECK (current_hp BETWEEN 0 AND max_hp)
);

CREATE TABLE enemy_body_part (
    scene_id         UUID NOT NULL,
    entity_local_id  TEXT NOT NULL,
    body_part        TEXT NOT NULL CHECK (body_part IN ('HEAD', 'NECK', 'CHEST', 'ABDOMEN', 'BACK',
                                                        'LEFT_ARM', 'RIGHT_ARM', 'LEFT_LEG', 'RIGHT_LEG', 'HEART')),
    severity         TEXT NOT NULL CHECK (severity IN ('HEALTHY', 'INJURED', 'WOUNDED', 'CRIPPLED', 'DESTROYED')),
    PRIMARY KEY (scene_id, entity_local_id, body_part),
    CONSTRAINT enemy_body_part_enemy
        FOREIGN KEY (scene_id, entity_local_id) REFERENCES enemy_instance (scene_id, entity_local_id)
);
