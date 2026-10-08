// Mirrors of the Stage 14/15A response records (com.leeburke.springgame.game.view). Nullable fields
// are absent information, never hidden information.

export type RunStatus = 'INITIALIZING' | 'ACTIVE' | 'DEAD' | 'VICTORIOUS';
export type Awaiting = 'ACTION' | 'DEFENSE' | 'NONE';
export type NarrationSource = 'AI' | 'FALLBACK';

export interface NarrationView {
  text: string;
  source: NarrationSource;
}

export interface ZoneView {
  alias: string;
  name: string;
}

export interface CharacterView {
  name: string;
  hp: number;
  maxHp: number;
  stats: Record<string, number>;
  fated: number;
  fatedBand: string;
  body: { part: string; severity: string }[];
  weapons: { alias: string; name: string }[];
  items: { alias: string; name: string }[];
  abilities: { alias: string; name: string }[];
  passive: string;
}

export interface SceneView {
  zones: ZoneView[];
  connections: { zoneA: string; zoneB: string }[];
  creatures: { alias: string; name: string; zone: string; condition: 'ACTIVE' | 'FALLEN' }[];
  objects: { alias: string; name: string; zone: string }[];
  hazards: { alias: string; name: string; zone: string }[];
  exits: { alias: string; zone: string }[];
}

export interface PendingAttackView {
  alias: string;
  attacker: string;
  cueText: string;
  narration: NarrationView | null;
}

export interface GameView {
  runId: string;
  status: RunStatus;
  stateVersion: number;
  awaiting: Awaiting;
  finalizing: boolean;
  introduction: NarrationView | null;
  character: CharacterView;
  location: { region: string | null; scene: string; zone: ZoneView };
  scene: SceneView;
  pendingAttack: PendingAttackView | null;
  lastTurn: { turnNumber: number; narration: NarrationView | null } | null;
}

export interface CreateRunResponse {
  runId: string;
  view: GameView;
}

export interface ApiErrorBody {
  error: { code: string; message: string; reason?: string; hint?: string };
}
