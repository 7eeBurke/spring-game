import { useState } from 'react';
import type { GameView } from '../api/types';
import { Composer, DefenseBanner } from '../story/Composer';
import { CharacterPanel, ScenePanel } from '../story/Panels';
import { EndingPassage, IncomingAttack, NarrationPassage, SceneHeading } from '../story/Passages';
import { Sheet } from '../story/Sheet';
import { StoryHeader } from '../story/StoryHeader';
import { StoryLayout } from '../story/StoryLayout';

/**
 * A real run, opened from the shelf. Stage 15B shows only what the current GameView confirms: the
 * introduction, where you are, any pending attack and the latest narration. The full chronicle
 * arrives in 15C and playing turns in 15D, so nothing here pretends to act.
 */
export function StoryScreen({ view, onBack }: { view: GameView; onBack: () => void }) {
  const [sheet, setSheet] = useState<'none' | 'character' | 'scene'>('none');
  const finished = view.status === 'DEAD' || view.status === 'VICTORIOUS';

  const header = (
    <StoryHeader region={view.location.region} scene={view.location.scene} name={view.character.name}
      hp={view.character.hp} maxHp={view.character.maxHp} onBack={onBack}
      onOpenCharacter={() => setSheet('character')} onOpenScene={() => setSheet('scene')} />
  );
  const footer = (
    <>
      {view.pendingAttack && <DefenseBanner attacker={view.pendingAttack.attacker} cueText={view.pendingAttack.cueText} />}
      <Composer disabled note={finished ? 'This tale has ended.' : 'Playing turns from this screen arrives in the next stage.'} />
    </>
  );

  return (
    <>
      <StoryLayout header={header} footer={footer}>
        {view.introduction && <NarrationPassage narration={view.introduction} intro />}
        <SceneHeading scene={view.location.scene} zone={view.location.zone.name} region={view.location.region} />
        {view.lastTurn?.narration && <NarrationPassage narration={view.lastTurn.narration} />}
        {view.pendingAttack && (
          <IncomingAttack attacker={view.pendingAttack.attacker} cueText={view.pendingAttack.cueText} narration={view.pendingAttack.narration} />
        )}
        {view.status === 'DEAD' && <EndingPassage ending="DEAD" />}
        {view.status === 'VICTORIOUS' && <EndingPassage ending="VICTORIOUS" />}
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
