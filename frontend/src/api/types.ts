// Mirrors of the Stage 14/15A response records (com.leeburke.springgame.game.view). Nullable fields
// are absent information, never hidden information.

export type RunStatus = 'INITIALIZING' | 'ACTIVE' | 'DEAD' | 'VICTORIOUS';
export type Awaiting = 'ACTION' | 'DEFENSE' | 'NONE';
/**
 * AI: told by the storyteller. FALLBACK: told plainly because the storyteller failed or was off.
 * DIRECT: told by the game by design (nothing for a storyteller to add, such as a look that finds
 * nothing changed); not a failure.
 */
export type NarrationSource = 'AI' | 'FALLBACK' | 'DIRECT';

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
  /**
   * container: a container's state as the player can see it ("closed", "open, holding a Bandage",
   * "open and empty"), null otherwise. reach: "here", "one step away", "two steps away", "farther",
   * "no known way". Both are absent in data stored before they existed.
   */
  objects: { alias: string; name: string; zone: string; container?: string | null; reach?: string }[];
  hazards: { alias: string; name: string; zone: string; container?: string | null; reach?: string }[];
  /** leadsTo: where the exit goes, as far as the player knows ("an unexplored way" otherwise). */
  exits: { alias: string; zone: string; leadsTo: string }[];
  /**
   * What the player knows is left to explore here: ways out not yet taken (exit aliases) and places
   * not yet stood in (zone aliases; empty when visits were not recorded). Absent in older data.
   */
  leads?: { unexploredExits: string[]; unvisitedZones: string[]; visitsRecorded: boolean };
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
  /** The run's opening direction, from the lore. */
  objective: string;
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

// Stage 15A chronicle (com.leeburke.springgame.game.view.ChronicleView).

export interface ChronicleOpening {
  introduction: NarrationView | null;
  objective: string;
  scene: string;
  zone: string;
}

export interface ChronicleAction {
  text: string;
  kind: 'FREE_TEXT' | 'COMMAND';
}

export interface ChroniclePlace {
  scene: string;
  zone: string | null;
}

export interface ChronicleEnemy {
  attacker: string;
  action: 'ATTACK' | 'HOLD';
  cueText: string | null;
  narration: NarrationView | null;
}

export interface ChronicleTurn {
  turnNumber: number;
  /** Null for turns recorded before the player's wording was kept. */
  action: ChronicleAction | null;
  enteredScene: ChroniclePlace | null;
  /** The zone moved to within the same scene (its map label), when the player walked somewhere. */
  movedTo?: string | null;
  /** Null while the turn's narration is still being finalised. */
  narration: NarrationView | null;
  narrationPending: boolean;
  enemy: ChronicleEnemy | null;
  ending: 'DEAD' | 'VICTORIOUS' | null;
}

export interface ChronicleView {
  runId: string;
  status: RunStatus;
  latestTurnNumber: number;
  /** Present only on the page that reaches the start of the run. */
  opening: ChronicleOpening | null;
  /** Oldest first. */
  turns: ChronicleTurn[];
  /** Cursor for the next older page, or null at the start. */
  nextBefore: number | null;
}

// Stage 14 turn result (com.leeburke.springgame.game.view.TurnResponse).

export interface TurnResponse {
  turnNumber: number;
  overall: string;
  narration: NarrationView;
  changes: { playerHpLost: number; enemiesDefeated: string[]; movedTo: string | null; enteredScene: string | null };
  /** Null when no enemy acted. */
  enemyTurn: { attacker: string; action: 'ATTACK' | 'HOLD' } | null;
  /** The view after the turn. */
  view: GameView;
}

/** The exact body of a turn request; retried byte-for-byte with its original key. */
export interface TurnRequestBody {
  input: string;
  stateVersion: number;
}
