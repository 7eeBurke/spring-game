import { describe, expect, it } from 'vitest';
import { chroniclePage, turn } from '../test/fakeApi';
import { applyLatestPage, applyOlderPage, EMPTY_CHRONICLE, mergeTurns, receiveTurn, settleTurn } from './chronicleState';
import { isNearBottom, prependScrollTop } from './scrollMath';

const numbers = (state: { turns: { turnNumber: number }[] }) => state.turns.map((t) => t.turnNumber);

describe('merging turns', () => {
  it('keeps one copy per turn, in order, newest copy winning', () => {
    const pending = turn(3, { narration: null, narrationPending: true });
    const told = turn(3);
    const merged = mergeTurns([turn(1), pending, turn(2)], [told, turn(4)]);
    expect(merged.map((t) => t.turnNumber)).toEqual([1, 2, 3, 4]);
    expect(merged[2]).toEqual(told);
  });
});

describe('chronicle pages', () => {
  it('starts from the latest page and walks back to the opening', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(26, 45, { latestTurnNumber: 45 }));
    expect(numbers(s)).toEqual(Array.from({ length: 20 }, (_, i) => 26 + i));
    expect(s).toMatchObject({ nextBefore: 26, reachedStart: false, opening: null });

    s = applyOlderPage(s, chroniclePage(6, 25));
    expect(s.nextBefore).toBe(6);
    expect(s.opening).toBeNull();
    s = applyOlderPage(s, chroniclePage(1, 5));
    expect(numbers(s)).toEqual(Array.from({ length: 45 }, (_, i) => i + 1));
    expect(s).toMatchObject({ nextBefore: null, reachedStart: true });
    expect(s.opening?.introduction?.text).toBe('You are Wren.');
  });

  it('shows the opening of a run with no turns', () => {
    const s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(1, 0));
    expect(s.turns).toEqual([]);
    expect(s.reachedStart).toBe(true);
    expect(s.opening?.scene).toBe('The Last Lantern');
  });

  it('merges an overlapping refresh and keeps older pages and the opening', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(1, 20));
    s = applyLatestPage(s, { ...chroniclePage(4, 23), latestTurnNumber: 23 });
    expect(numbers(s)).toEqual(Array.from({ length: 23 }, (_, i) => i + 1));
    expect(s.opening).not.toBeNull();
    expect(s.reachedStart).toBe(true);
  });

  it('fills a pending narration when a refresh brings its telling', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, { ...chroniclePage(1, 2), turns: [turn(1), turn(2, { narration: null, narrationPending: true })] });
    s = applyLatestPage(s, chroniclePage(1, 2));
    expect(s.turns[1]!.narrationPending).toBe(false);
    expect(s.turns[1]!.narration?.text).toBe('Narration of turn 2.');
  });

  it('never leaves a silent gap when the run moved on by more than a page', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(1, 20));
    // 45 more turns were played elsewhere; the latest page (46..65) does not touch 1..20.
    s = applyLatestPage(s, chroniclePage(46, 65, { latestTurnNumber: 65 }));
    expect(numbers(s)).toEqual(Array.from({ length: 20 }, (_, i) => 46 + i));
    expect(s).toMatchObject({ nextBefore: 46, reachedStart: false, opening: null });
    // ...and walking back fills everything in, contiguously.
    s = applyOlderPage(s, chroniclePage(26, 45));
    s = applyOlderPage(s, chroniclePage(6, 25));
    s = applyOlderPage(s, chroniclePage(1, 5));
    expect(numbers(s)).toEqual(Array.from({ length: 65 }, (_, i) => i + 1));
  });

  it('joins a refresh that starts exactly after the loaded window', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(1, 20));
    s = applyLatestPage(s, chroniclePage(21, 40, { latestTurnNumber: 40 }));
    expect(numbers(s)).toEqual(Array.from({ length: 40 }, (_, i) => i + 1));
    expect(s.opening).not.toBeNull();
  });
});

describe('fresh turns (live play)', () => {
  it('marks a received turn for reveal until it settles', () => {
    let s = applyLatestPage(EMPTY_CHRONICLE, chroniclePage(1, 2));
    s = receiveTurn(s, turn(3));
    expect(s.fresh.has(3)).toBe(true);
    expect(numbers(s)).toEqual([1, 2, 3]);
    s = settleTurn(s, 3);
    expect(s.fresh.has(3)).toBe(false);
  });

  it('never marks loaded history as fresh', () => {
    const s = applyOlderPage(applyLatestPage(EMPTY_CHRONICLE, chroniclePage(21, 40)), chroniclePage(1, 20));
    expect(s.fresh.size).toBe(0);
  });
});

describe('scroll maths', () => {
  it('keeps the read passage still when older content is inserted above', () => {
    expect(prependScrollTop(40, 1000, 1800)).toBe(840);
    expect(prependScrollTop(0, 1000, 1000)).toBe(0);
  });

  it('treats the last 120px as the bottom', () => {
    expect(isNearBottom({ scrollTop: 880, scrollHeight: 1600, clientHeight: 600 })).toBe(true);
    expect(isNearBottom({ scrollTop: 870, scrollHeight: 1600, clientHeight: 600 })).toBe(false);
  });
});
