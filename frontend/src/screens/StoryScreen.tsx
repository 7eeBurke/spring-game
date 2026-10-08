import { useRef, useState } from 'react';
import type { GameView } from '../api/types';
import { ChronicleView } from '../chronicle/ChronicleView';
import styles from '../chronicle/Chronicle.module.css';
import { useChronicleScroll } from '../chronicle/useChronicleScroll';
import { useRunStory } from '../chronicle/useRunStory';
import { Composer, DefenseBanner } from '../story/Composer';
import composerStyles from '../story/Composer.module.css';
import { EndedFooter } from '../story/EndedFooter';
import { CharacterPanel, ScenePanel } from '../story/Panels';
import { Sheet } from '../story/Sheet';
import { StoryHeader } from '../story/StoryHeader';
import { StoryLayout } from '../story/StoryLayout';
import type { VaultEntry } from '../storage/vault';

/**
 * A real run: the whole chronicle restored from the server, with the current state from the
 * GameView. Critical state (HP, a pending attack, the run's end) comes straight from the view and
 * is never delayed by narration. Playing turns arrives in Stage 15D, so the composer is shown but
 * cannot send yet.
 */
export function StoryScreen({ entry, view: initialView, onBack }: { entry: VaultEntry; view: GameView; onBack: () => void }) {
  const story = useRunStory(entry, initialView);
  const { view, chronicle } = story;
  const scrollRef = useRef<HTMLElement>(null);
  const last = chronicle.turns.at(-1);
  const lastKey = chronicle.opening || last
    ? `${last?.turnNumber ?? 0}:${last?.narration ? 'told' : 'pending'}:${last?.enemy?.narration ? 'attack-told' : ''}`
    : '';
  const scroll = useChronicleScroll(scrollRef, chronicle.turns[0]?.turnNumber ?? null, lastKey);
  const [sheet, setSheet] = useState<'none' | 'character' | 'scene'>('none');
  const ending = view.status === 'DEAD' || view.status === 'VICTORIOUS' ? view.status : null;

  const header = (
    <StoryHeader region={view.location.region} scene={view.location.scene} name={view.character.name}
      hp={view.character.hp} maxHp={view.character.maxHp} onBack={onBack}
      onOpenCharacter={() => setSheet('character')} onOpenScene={() => setSheet('scene')} />
  );

  const footer = ending ? <EndedFooter ending={ending} onReturn={onBack} /> : (
    <>
      {view.finalizing && (
        <div className={composerStyles.status} role="status">
          <span>The telling of the last turn has not arrived yet. Its outcome is already settled.</span>
          <button type="button" className={composerStyles.statusButton} onClick={() => void story.refresh()} disabled={story.refreshing}>
            {story.refreshing ? 'Checking…' : 'Check again'}
          </button>
        </div>
      )}
      {view.pendingAttack && <DefenseBanner attacker={view.pendingAttack.attacker} cueText={view.pendingAttack.cueText} />}
      <Composer disabled placeholder={view.awaiting === 'DEFENSE' ? 'How do you defend?' : 'What do you do?'}
        note="Playing turns from this screen arrives in the next stage." />
    </>
  );

  const overlay = scroll.newBelow
    ? <button type="button" className={styles.pill} onClick={scroll.scrollToBottom}>New passage ↓</button>
    : null;

  return (
    <>
      <StoryLayout header={header} footer={footer} scrollRef={scrollRef} overlay={overlay}>
        {story.loading && chronicle.turns.length === 0 && !chronicle.opening && (
          <p className={styles.loading} role="status">Opening your chronicle…</p>
        )}
        <ChronicleView chronicle={chronicle} loadingOlder={story.loadingOlder} onLoadOlder={story.loadOlder}
          onRevealed={story.settle} />
        {story.error && (
          <div className={styles.notice} role="alert">
            <p>{story.error}</p>
            {story.unavailable
              ? <button type="button" className={styles.retry} onClick={onBack}>Return to your tales</button>
              : <button type="button" className={styles.retry} onClick={story.retry}>Try again</button>}
          </div>
        )}
      </StoryLayout>
      <Sheet title="Character" open={sheet === 'character'} onClose={() => setSheet('none')}>
        <CharacterPanel character={view.character} />
      </Sheet>
      <Sheet title={view.location.scene} open={sheet === 'scene'} onClose={() => setSheet('none')}>
        <ScenePanel scene={view.scene} currentZone={view.location.zone} />
      </Sheet>
    </>
  );
}
