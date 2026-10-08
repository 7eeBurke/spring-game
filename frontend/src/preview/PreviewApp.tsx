import { useState } from 'react';
import { Composer, DefenseBanner } from '../story/Composer';
import { CharacterPanel, ScenePanel } from '../story/Panels';
import { EndingPassage, IncomingAttack, NarrationPassage, PendingNarration, PlayerPassage, SceneHeading } from '../story/Passages';
import { Sheet } from '../story/Sheet';
import { StoryHeader } from '../story/StoryHeader';
import { StoryLayout } from '../story/StoryLayout';
import styles from './Preview.module.css';
import {
  SAMPLE_ATTACK, SAMPLE_CHARACTER, SAMPLE_ENDING, SAMPLE_FALLBACK, SAMPLE_PLACE, SAMPLE_SCENE, SAMPLE_STORY, type SampleEntry,
} from './sampleStory';

type Mode = 'defense' | 'action' | 'pending' | 'fallback' | 'ending';

const MODES: { mode: Mode; label: string }[] = [
  { mode: 'defense', label: 'Defense pending' },
  { mode: 'action', label: 'Your move' },
  { mode: 'pending', label: 'Still writing' },
  { mode: 'fallback', label: 'Plain telling' },
  { mode: 'ending', label: 'Fallen' },
];

function Entry({ entry }: { entry: SampleEntry }) {
  switch (entry.kind) {
    case 'introduction': return <NarrationPassage narration={entry.narration} intro />;
    case 'scene': return <SceneHeading scene={entry.scene} zone={entry.zone} region={entry.region} />;
    case 'player': return <PlayerPassage text={entry.text} kind={entry.command ? 'COMMAND' : 'FREE_TEXT'} />;
    case 'narration': return <NarrationPassage narration={entry.narration} />;
    case 'attack': return <IncomingAttack attacker={entry.attacker} cueText={entry.cueText} narration={entry.narration} />;
  }
}

/**
 * A presentation-only preview of the story screen, built from the real story components and
 * clearly labelled sample data. Nothing here talks to the server, plays turns or saves anything.
 */
export function PreviewApp() {
  const [mode, setMode] = useState<Mode>('defense');
  const [sheet, setSheet] = useState<'none' | 'character' | 'scene'>('none');
  const ended = mode === 'ending';
  const hp = ended ? 0 : mode === 'defense' ? SAMPLE_CHARACTER.hp : 10;

  const header = (
    <StoryHeader region={SAMPLE_PLACE.region} scene={SAMPLE_PLACE.scene} name={SAMPLE_CHARACTER.name}
      hp={hp} maxHp={SAMPLE_CHARACTER.maxHp}
      onOpenCharacter={() => setSheet('character')} onOpenScene={() => setSheet('scene')} />
  );
  const footer = (
    <>
      {mode === 'defense' && <DefenseBanner attacker={SAMPLE_ATTACK.attacker} cueText={SAMPLE_ATTACK.cueText} />}
      <Composer disabled={ended} placeholder={mode === 'defense' ? 'How do you defend?' : 'What do you do?'}
        note={ended ? 'This tale has ended.' : 'Design preview: sending is not available here.'} />
    </>
  );

  // Each state is one consistent moment of the same sample tale.
  const story: SampleEntry[] = mode === 'defense'
    ? SAMPLE_STORY.slice(0, -2) // the attack is pending; nothing after it yet
    : mode === 'fallback'
      ? [...SAMPLE_STORY.slice(0, -1), { kind: 'narration', narration: SAMPLE_FALLBACK }] // same outcome, plainly told
      : mode === 'ending' ? [...SAMPLE_STORY, ...SAMPLE_ENDING] : SAMPLE_STORY;

  return (
    <>
      <StoryLayout header={header} footer={footer}>
        <aside className={styles.banner} aria-label="About this preview">
          <span className={styles.bannerTitle}>Design preview · sample text</span>
          Hand-written sample story for reviewing the look of the storybook. Nothing is sent to the game or saved.
          <div className={styles.controls} role="group" aria-label="Preview state">
            {MODES.map((m) => (
              <button key={m.mode} type="button" className={styles.control} aria-pressed={mode === m.mode}
                onClick={() => setMode(m.mode)}>{m.label}</button>
            ))}
          </div>
        </aside>
        {story.map((entry, i) => <Entry key={i} entry={entry} />)}
        {mode === 'pending' && (
          <>
            <PlayerPassage text="I duck under the next swing and drive my blade up beneath its jaw." kind="FREE_TEXT" />
            <PendingNarration />
          </>
        )}
        {ended && <EndingPassage ending="DEAD" />}
      </StoryLayout>
      <Sheet title="Character" open={sheet === 'character'} onClose={() => setSheet('none')}>
        <CharacterPanel character={{ ...SAMPLE_CHARACTER, hp }} />
      </Sheet>
      <Sheet title={SAMPLE_PLACE.scene} open={sheet === 'scene'} onClose={() => setSheet('none')}>
        <ScenePanel scene={SAMPLE_SCENE} currentZone={SAMPLE_PLACE.zone} />
      </Sheet>
    </>
  );
}
