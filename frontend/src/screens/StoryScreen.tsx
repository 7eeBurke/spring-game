import { useCallback, useRef, useState } from 'react';
import type { GameView } from '../api/types';
import { ChronicleView } from '../chronicle/ChronicleView';
import styles from '../chronicle/Chronicle.module.css';
import { useChronicleScroll } from '../chronicle/useChronicleScroll';
import { useRunStory } from '../chronicle/useRunStory';
import { DefenseShortcuts, LiveAnnouncer, OutboxPassage, PlayNoticeLine, PlayStatus } from '../play/PlayUi';
import { usePlay } from '../play/usePlay';
import { Composer, DefenseBanner } from '../story/Composer';
import composerStyles from '../story/Composer.module.css';
import { EndedFooter } from '../story/EndedFooter';
import { CharacterPanel, ScenePanel } from '../story/Panels';
import { Sheet } from '../story/Sheet';
import { StoryHeader } from '../story/StoryHeader';
import { StoryLayout } from '../story/StoryLayout';
import type { VaultEntry } from '../storage/vault';

const touchDevice = () => typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;

/**
 * A real run: the chronicle restored from the server, the current state from the GameView, and
 * live play through the turn endpoint. Critical state (HP, a pending attack, the run's end) comes
 * straight from the newest view and is never delayed by narration.
 */
export function StoryScreen({ entry, view: initialView, onBack }: { entry: VaultEntry; view: GameView; onBack: () => void }) {
  const story = useRunStory(entry, initialView);
  const { view, chronicle } = story;
  const [announcement, setAnnouncement] = useState('');
  const announce = useCallback((text: string) => {
    setAnnouncement(text);
    // On a phone the keyboard would cover the new passage: put it away so the story can be read.
    if (touchDevice()) (document.activeElement as HTMLElement | null)?.blur();
  }, []);
  const play = usePlay(entry.runId!, entry.token, story, announce);

  const scrollRef = useRef<HTMLElement>(null);
  const last = chronicle.turns.at(-1);
  const outstanding = 'entry' in play.phase ? play.phase.entry : null;
  const lastKey = chronicle.opening || last
    ? `${last?.turnNumber ?? 0}:${last?.narration ? 'told' : 'pending'}:${last?.enemy?.narration ? 'attack-told' : ''}:${outstanding?.key ?? ''}`
    : '';
  const scroll = useChronicleScroll(scrollRef, chronicle.turns[0]?.turnNumber ?? null, lastKey);
  const [sheet, setSheet] = useState<'none' | 'character' | 'scene'>('none');
  const ending = view.status === 'DEAD' || view.status === 'VICTORIOUS' ? view.status : null;

  const submit = () => {
    play.submit();
    // The player acted: bring the end of the story (their own words) into view.
    requestAnimationFrame(scroll.scrollToBottom);
  };

  const header = (
    <StoryHeader region={view.location.region} scene={view.location.scene} name={view.character.name}
      hp={view.character.hp} maxHp={view.character.maxHp} onBack={onBack}
      onOpenCharacter={() => setSheet('character')} onOpenScene={() => setSheet('scene')} />
  );

  const footer = ending ? <EndedFooter ending={ending} onReturn={onBack} /> : (
    <>
      {view.finalizing && play.phase.kind === 'idle' && (
        <div className={composerStyles.status} role="status">
          <span>The telling of the last turn has not arrived yet. It will be completed when you next act.</span>
          <button type="button" className={composerStyles.statusButton} onClick={() => void story.refresh()} disabled={story.refreshing}>
            {story.refreshing ? 'Checking…' : 'Check again'}
          </button>
        </div>
      )}
      <PlayStatus play={play} onReloadStory={() => void story.refresh()} reloading={story.refreshing} />
      <PlayNoticeLine play={play} />
      {view.pendingAttack && <DefenseBanner attacker={view.pendingAttack.attacker} cueText={view.pendingAttack.cueText} />}
      {view.pendingAttack && play.canAct && play.text.trim() === '' && <DefenseShortcuts onPick={play.setText} />}
      <Composer value={play.text} onChange={play.setText} onSubmit={submit} busy={!play.canAct} disabled={story.unavailable}
        placeholder={view.awaiting === 'DEFENSE' ? 'How do you defend?' : 'What do you do?'} />
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
        <ChronicleView chronicle={chronicle} loadingOlder={story.loadingOlder} onLoadOlder={story.loadOlder} onRevealed={story.settle} />
        <OutboxPassage phase={play.phase} />
        {story.error && (
          <div className={styles.notice} role="alert">
            <p>{story.error}</p>
            {story.unavailable
              ? <button type="button" className={styles.retry} onClick={onBack}>Return to your tales</button>
              : <button type="button" className={styles.retry} onClick={story.retry}>Try again</button>}
          </div>
        )}
      </StoryLayout>
      <LiveAnnouncer message={announcement} />
      <Sheet title="Character" open={sheet === 'character'} onClose={() => setSheet('none')}>
        <CharacterPanel character={view.character} />
      </Sheet>
      <Sheet title={view.location.scene} open={sheet === 'scene'} onClose={() => setSheet('none')}>
        <ScenePanel scene={view.scene} currentZone={view.location.zone} objective={view.objective} />
      </Sheet>
    </>
  );
}
