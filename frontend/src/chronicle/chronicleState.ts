import type { ChronicleOpening, ChronicleTurn, ChronicleView } from '../api/types';
import { betterTurn } from './freshness';

/**
 * The part of a run's story loaded so far: always one contiguous window of turns, ending at the
 * latest loaded turn, with a cursor to load the older ones. Pure and immutable.
 */
export interface ChronicleState {
  /** The introduction and starting place, once the window reaches the start of the run. */
  opening: ChronicleOpening | null;
  /** Ascending, unique by turnNumber, contiguous. */
  turns: ChronicleTurn[];
  /** Load turns below this next; null once the start is reached. */
  nextBefore: number | null;
  reachedStart: boolean;
  /** Turns whose narration should be revealed as new (set only by live play, Stage 15D). */
  fresh: ReadonlySet<number>;
}

export const EMPTY_CHRONICLE: ChronicleState = { opening: null, turns: [], nextBefore: null, reachedStart: false, fresh: new Set() };

/**
 * Union by turnNumber. On a clash the incoming copy wins (a provisional entry becomes the
 * authoritative one, a pending turn its narrated copy), but a copy never loses narration it had.
 */
export function mergeTurns(existing: readonly ChronicleTurn[], incoming: readonly ChronicleTurn[]): ChronicleTurn[] {
  const byNumber = new Map<number, ChronicleTurn>();
  for (const turn of existing) byNumber.set(turn.turnNumber, turn);
  for (const turn of incoming) {
    const known = byNumber.get(turn.turnNumber);
    byNumber.set(turn.turnNumber, known ? betterTurn(known, turn) : turn);
  }
  return [...byNumber.values()].sort((a, b) => a.turnNumber - b.turnNumber);
}

function fromPage(page: ChronicleView): ChronicleState {
  return {
    opening: page.opening,
    turns: mergeTurns([], page.turns),
    nextBefore: page.nextBefore,
    reachedStart: page.nextBefore === null,
    fresh: new Set(),
  };
}

/**
 * Applies the latest page (first load or a refresh). If it joins up with what is loaded, the turns
 * are merged and older pages stay. If the run has moved on so far that the page no longer touches
 * the loaded window, keeping both would leave a silent gap in the story, so the state resets to the
 * page: a coherent latest window whose older turns load again through the cursor.
 */
export function applyLatestPage(state: ChronicleState, page: ChronicleView): ChronicleState {
  if (state.turns.length === 0) {
    return { ...fromPage(page), fresh: state.fresh };
  }
  const loadedMax = state.turns[state.turns.length - 1]!.turnNumber;
  const pageMin = page.turns.length ? page.turns[0]!.turnNumber : page.latestTurnNumber + 1;
  if (pageMin > loadedMax + 1) {
    return fromPage(page);
  }
  return { ...state, turns: mergeTurns(state.turns, page.turns) };
}

/** Prepends an older page fetched with `before = state.nextBefore`. */
export function applyOlderPage(state: ChronicleState, page: ChronicleView): ChronicleState {
  return {
    ...state,
    opening: page.nextBefore === null ? page.opening : state.opening,
    turns: mergeTurns(page.turns, state.turns),
    nextBefore: page.nextBefore,
    reachedStart: page.nextBefore === null,
  };
}

/** Adds or replaces one turn (a newly completed turn in live play) and marks it to be revealed. */
export function receiveTurn(state: ChronicleState, turn: ChronicleTurn): ChronicleState {
  const fresh = new Set(state.fresh);
  fresh.add(turn.turnNumber);
  return { ...state, turns: mergeTurns(state.turns, [turn]), fresh };
}

/** The turn's reveal has finished or been skipped: it never animates again. */
export function settleTurn(state: ChronicleState, turnNumber: number): ChronicleState {
  if (!state.fresh.has(turnNumber)) return state;
  const fresh = new Set(state.fresh);
  fresh.delete(turnNumber);
  return { ...state, fresh };
}
