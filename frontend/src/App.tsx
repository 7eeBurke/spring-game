import { useEffect, useState } from 'react';
import type { GameView } from './api/types';
import { GAME_TITLE } from './config';
import { ShelfScreen } from './screens/ShelfScreen';
import { StoryScreen } from './screens/StoryScreen';
import { requestPersistentStorage, type VaultEntry } from './storage/vault';

type Screen = { kind: 'shelf' } | { kind: 'story'; entry: VaultEntry; view: GameView };

/** Two screens chosen by state; no router, so no token or run ID ever appears in the URL. */
export function App() {
  const [screen, setScreen] = useState<Screen>({ kind: 'shelf' });

  useEffect(() => {
    document.title = GAME_TITLE;
    void requestPersistentStorage();
  }, []);

  if (screen.kind === 'story') {
    return <StoryScreen key={screen.entry.localId} entry={screen.entry} view={screen.view} onBack={() => setScreen({ kind: 'shelf' })} />;
  }
  return <ShelfScreen onOpen={(entry, view) => setScreen({ kind: 'story', entry, view })} />;
}
