-- Stage 15A: the player's exact wording of each accepted action, for the run's chronicle.
--
-- Written when a turn request is first recorded (the only moment the exact text is known) and
-- cleared when the request ends REJECTED or STALE, so only accepted actions are kept. It is player
-- data, readable only with the run's token, and never logged. Rows from before V7 keep NULL.
ALTER TABLE run_turn ADD COLUMN player_input TEXT
    CONSTRAINT run_turn_player_input_length CHECK (player_input IS NULL OR length(player_input) BETWEEN 1 AND 500);
