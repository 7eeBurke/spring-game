/**
 * How new narration appears: paragraph by paragraph, with a short stagger and a soft fade. Tune
 * here after playtesting (the fade's CSS duration is --reveal-fade in styles/tokens.css).
 * There is deliberately no character-by-character typewriter mode.
 */
export const revealConfig = {
  /** Delay before each following paragraph appears. */
  stepMs: 250,
};
