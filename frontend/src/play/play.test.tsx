import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { GameView } from '../api/types';
import { StoryScreen } from '../screens/StoryScreen';
import { generateRunToken, newUuid } from '../security/tokens';
import { outboxKey, readOutbox } from '../storage/outbox';
import { addRun, findByRunId, type VaultEntry } from '../storage/vault';
import { RUN_ID } from '../test/fakeApi';
import { error, FakeGame } from '../test/fakeGame';
import { retryTiming } from './turnOutcome';

const original = { ...retryTiming, inProgressMs: [...retryTiming.inProgressMs] };

beforeEach(() => {
  // Same logic, shorter waits.
  retryTiming.inProgressMs = [30, 30, 30, 30];
  retryTiming.rateLimitFallbackMs = 300;
  retryTiming.uncertainAutoRetryMs = 60_000; // tests trigger the manual check unless stated
});

afterEach(() => {
  Object.assign(retryTiming, original);
});

function savedRun(): VaultEntry {
  const existing = findByRunId(RUN_ID);
  if (existing) return existing;
  const entry: VaultEntry = { localId: newUuid(), runId: RUN_ID, token: generateRunToken(), creationKey: newUuid(),
    createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null, summary: null };
  addRun(entry);
  return entry;
}

function open(game: FakeGame, view: GameView = game.view) {
  const entry = savedRun();
  const utils = render(<StoryScreen entry={entry} view={view} onBack={() => undefined} />);
  return { entry, ...utils };
}

const field = () => screen.getByLabelText('Your action') as HTMLTextAreaElement;
const send = () => screen.getByRole('button', { name: /Send action|Sending action/ });
const turnsShown = () => screen.queryAllByRole('article').map((a) => Number(a.getAttribute('data-turn')));
const postBodies = (game: FakeGame) => game.posts.map((p) => ({ key: p.headers['Idempotency-Key'], body: JSON.parse(p.body!) }));

describe('playing a turn', () => {
  it('sends the exact words once and shows the confirmed result', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');

    await user.type(field(), 'I slash at the Warden.{Shift>}{Enter}{/Shift}Then I step back.');
    await user.keyboard('{Enter}');

    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    expect(game.posts).toHaveLength(1);
    const { key, body } = postBodies(game)[0]!;
    expect(body).toEqual({ input: 'I slash at the Warden.\nThen I step back.', stateVersion: 0 });
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.getByText('You').parentElement?.textContent).toBe('YouI slash at the Warden.\nThen I step back.');
    expect(readOutbox(RUN_ID)).toEqual({ kind: 'none' });
    expect(field().value).toBe('');
    expect(document.querySelector('[aria-live="polite"]')?.textContent).toContain('Narration of turn 1.');
  });

  it('persists the request before the POST leaves', async () => {
    const game = new FakeGame();
    let atSend: ReturnType<typeof readOutbox> | null = null;
    game.install();
    const original = game.calls.push.bind(game.calls);
    game.calls.push = (...calls) => {
      if (calls.some((c) => c.method === 'POST')) atSend = readOutbox(RUN_ID);
      return original(...calls);
    };
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    await screen.findByText('Narration of turn 1.');
    expect(atSend).toMatchObject({ kind: 'pending', entry: { body: { input: 'I wait.', stateVersion: 0 } } });
  });

  it('reveals new narration paragraph by paragraph, and recovered narration instantly', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    const { unmount } = open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I listen.{Enter}');
    const passage = await screen.findByText('Narration of turn 1.');
    expect(passage.closest('section')!.querySelectorAll('p[data-hidden]').length).toBe(1);
    await waitFor(() => expect(passage.closest('section')!.querySelectorAll('p[data-hidden]').length).toBe(0));

    unmount();
    open(game);
    const recovered = await screen.findByText('Narration of turn 1.');
    expect(recovered.closest('section')!.querySelectorAll('p[data-hidden]').length).toBe(0);
  });

  it('marks a deterministic fallback narration', async () => {
    const game = new FakeGame().install();
    game.next.push({ narration: { text: 'You hold your ground.', source: 'FALLBACK' } });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), '/hold{Enter}');
    expect(await screen.findByText(/told plainly/i)).toBeInTheDocument();
    expect(screen.getByText('Command')).toBeInTheDocument();
  });

  it('sends one request for a double tap', async () => {
    const game = new FakeGame().install();
    game.turnDelay = 50;
    open(game);
    await screen.findByLabelText('Introduction');
    fireEvent.change(field(), { target: { value: 'I strike twice?' } });
    fireEvent.click(send());
    fireEvent.click(send());
    fireEvent.submit(field().form!);
    await screen.findByText('Narration of turn 1.');
    expect(game.posts).toHaveLength(1);
  });

  it('on a touch screen, Enter makes a new line and the button sends', async () => {
    window.matchMedia = vi.fn().mockImplementation((q: string) => ({ matches: q.includes('coarse'), addEventListener: vi.fn(), removeEventListener: vi.fn() })) as never;
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'line one{Enter}line two');
    expect(game.posts).toHaveLength(0);
    expect(field().value).toBe('line one\nline two');
    await user.click(send());
    await screen.findByText('Narration of turn 1.');
    expect(postBodies(game)[0]!.body.input).toBe('line one\nline two');
    Reflect.deleteProperty(window, 'matchMedia');
  });

  it('does not send while an IME composition is in progress', async () => {
    const game = new FakeGame().install();
    open(game);
    await screen.findByLabelText('Introduction');
    fireEvent.change(field(), { target: { value: 'かたな' } });
    fireEvent.keyDown(field(), { key: 'Enter', isComposing: true });
    fireEvent.keyDown(field(), { key: 'Enter', keyCode: 229 });
    expect(game.posts).toHaveLength(0);
  });
});

describe('enemy attacks and defense', () => {
  it('shows the cue at once, offers discreet shortcuts, and a defense alone brings no new attack', async () => {
    const game = new FakeGame().install();
    game.next.push({ enemy: { attacker: 'Bone Warden', action: 'ATTACK', cueText: 'Incoming: an overhead strike coming down from above.',
      narration: { text: 'Its hammer rises.', source: 'AI' } } });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I slash at the Warden.{Enter}');

    expect(await screen.findByRole('alert')).toHaveTextContent('an overhead strike coming down from above.');
    expect(field().placeholder).toBe('How do you defend?');
    const shortcuts = screen.getByRole('group', { name: 'Quick defenses' });
    await user.click(within(shortcuts).getByRole('button', { name: /^Dodge/ }));
    expect(field().value).toBe('/defend evade');
    expect(game.posts).toHaveLength(1); // a shortcut only writes the command
    expect(screen.queryByRole('group', { name: 'Quick defenses' })).toBeNull(); // hidden once there is text

    await user.clear(field());
    await user.type(field(), 'I raise my sword to parry the blow.{Enter}');
    await screen.findByText('Narration of turn 2.');
    expect(screen.queryByText('an overhead strike coming down from above.', { selector: '[role=alert] *' })).toBeNull();
    expect(screen.queryByRole('group', { name: 'Quick defenses' })).toBeNull();
    expect(field().placeholder).toBe('What do you do?');
  });

  it('a defense with a counterattack can draw a new attack', async () => {
    const game = new FakeGame().install();
    game.next.push({ enemy: { attacker: 'Bone Warden', action: 'ATTACK' } });
    game.next.push({ enemy: { attacker: 'Bone Warden', action: 'ATTACK', cueText: 'Incoming: a wide swing coming from the side.' }, hpLost: 5 });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I strike.{Enter}');
    await screen.findByText('Narration of turn 1.');
    await user.type(field(), 'I parry, then cut at its ribs.{Enter}');
    await screen.findByText('Narration of turn 2.');
    expect(screen.getByRole('alert')).toHaveTextContent('a wide swing coming from the side.');
    expect(screen.getByRole('button', { name: /Wren, 19 of 24/ })).toBeInTheDocument();
  });
});

describe('when the answer is lost or deferred', () => {
  it('recovers a lost response with the same key and shows the turn once', async () => {
    const game = new FakeGame().install();
    game.next.push({ lost: true });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');

    expect(await screen.findByText(/could not confirm|faltered/)).toBeInTheDocument();
    expect(screen.getByLabelText('Your action, not yet confirmed')).toHaveTextContent('I wait.');
    expect(field().value).toBe('');
    await user.click(screen.getByRole('button', { name: 'Check again' }));

    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    const [first, second] = postBodies(game);
    expect(second).toEqual(first);
    expect(turnsShown()).toEqual([1]);
    expect(game.turns).toHaveLength(1);
  });

  it('retries an uncertain request once by itself, with the same key', async () => {
    retryTiming.uncertainAutoRetryMs = 30;
    const game = new FakeGame().install();
    game.next.push({ answer: { status: 503, json: { error: { code: 'SERVICE_UNAVAILABLE', message: 'x' } } } });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    expect(new Set(postBodies(game).map((p) => p.key)).size).toBe(1);
    expect(game.posts).toHaveLength(2);
  });

  it('after the single automatic retry, waits for the player', async () => {
    retryTiming.uncertainAutoRetryMs = 20;
    const game = new FakeGame().install();
    game.next.push({ answer: { status: 500, json: {} } }, { answer: { status: 502, json: {} } });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByText(/could not confirm/)).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 100));
    expect(game.posts).toHaveLength(2);
    expect(send()).toBeDisabled();
  });

  it('recovers an outstanding action after the browser was closed, with its own key', async () => {
    const game = new FakeGame().install();
    const key = newUuid();
    localStorage.setItem(outboxKey(RUN_ID), JSON.stringify({ key, body: { input: 'I listen.', stateVersion: 0 }, createdAt: 'x' }));
    open(game);
    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    expect(postBodies(game)).toEqual([{ key, body: { input: 'I listen.', stateVersion: 0 } }]);
    expect(readOutbox(RUN_ID).kind).toBe('none');
  });

  it('keeps retrying REQUEST_IN_PROGRESS with the same key, within bounds', async () => {
    const game = new FakeGame().install();
    const busy = error(409, 'REQUEST_IN_PROGRESS');
    game.next.push({ answer: busy }, { answer: busy });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    expect(new Set(postBodies(game).map((p) => JSON.stringify(p))).size).toBe(1);
    expect(game.posts).toHaveLength(3);
  });

  it('gives up retrying REQUEST_IN_PROGRESS after four tries and asks the player', async () => {
    const game = new FakeGame().install();
    const busy = error(409, 'REQUEST_IN_PROGRESS');
    game.next.push({ answer: busy }, { answer: busy }, { answer: busy }, { answer: busy }, { answer: busy });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByText(/could not confirm/)).toBeInTheDocument();
    expect(game.posts).toHaveLength(5);
  });

  it('honours Retry-After on a rate limit, then retries only when asked, with the same key', async () => {
    const game = new FakeGame().install();
    game.next.push({ answer: error(429, 'RATE_LIMITED', {}, { 'Retry-After': '1' }) });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByText(/needs a moment \(1s\)/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Try again' })).toBeEnabled(), { timeout: 2500 });
    expect(game.posts).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Narration of turn 1.')).toBeInTheDocument();
    expect(postBodies(game)[1]).toEqual(postBodies(game)[0]);
  });

  it('a reconnect, a timer and a tap together still send one retry at a time', async () => {
    const game = new FakeGame().install();
    game.next.push({ lost: true });
    game.turnDelay = 80;
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I wait.{Enter}');
    await screen.findByRole('button', { name: 'Check again' });
    const before = game.posts.length;
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Check again' }));
      window.dispatchEvent(new Event('online'));
      fireEvent.click(screen.queryByRole('button', { name: 'Check again' }) ?? document.body);
    });
    await screen.findByText('Narration of turn 1.');
    expect(game.posts.length - before).toBe(1);
  });
});

describe('refused actions', () => {
  it.each([
    ['INVALID_COMMAND', 'That command could not be understood.'],
    ['ACTION_NOT_SUPPORTED', 'That is not something you can attempt here.'],
    ['DEFENSE_REQUIRED', 'An attack is coming'],
    ['DEFENSE_NOT_RESOLVED', 'That defense could not be carried out'],
  ])('%s restores the words, explains, and adds no turn', async (code, message) => {
    const game = new FakeGame().install();
    game.next.push({ answer: { ...error(422, code, { hint: 'Try /defend parry.' }), store: true } });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), '/fly{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('alert')).toHaveTextContent('Try /defend parry.');
    expect(field().value).toBe('/fly');
    expect(turnsShown()).toEqual([]);
    expect(readOutbox(RUN_ID).kind).toBe('none');
  });

  it('AI unavailable keeps the words and suggests commands', async () => {
    const game = new FakeGame().install();
    game.next.push({ answer: error(422, 'INTERPRETATION_FAILED', { reason: 'AI_UNAVAILABLE', hint: 'Slash commands always work.' }) });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I look around.{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('Free-text actions are unavailable right now');
    expect(field().value).toBe('I look around.');
    expect(turnsShown()).toEqual([]);
  });

  it('a stale view restores the words and re-reads the story', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game, { ...game.view });
    await screen.findByLabelText('Introduction');
    game.turns.push({ turnNumber: 1, action: { text: '/hold', kind: 'COMMAND' }, enteredScene: null,
      narration: { text: 'Played elsewhere.', source: 'AI' }, narrationPending: false, enemy: null, ending: null });
    game.view = { ...game.view, stateVersion: 1 };
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('The story moved on');
    expect(await screen.findByText('Played elsewhere.')).toBeInTheDocument();
    expect(field().value).toBe('I wait.');
  });

  it('a run that finished meanwhile closes the composer', async () => {
    const game = new FakeGame().install();
    game.next.push({ answer: error(409, 'RUN_FINISHED') });
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    game.view = { ...game.view, status: 'DEAD', awaiting: 'NONE' };
    await user.type(field(), 'I wait.{Enter}');
    expect(await screen.findByRole('button', { name: 'Return to your tales' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).toBeNull();
  });
});

describe('safeguards', () => {
  it('a damaged outbox record blocks new actions until the player reconciles', async () => {
    const game = new FakeGame().install();
    localStorage.setItem(outboxKey(RUN_ID), '{"key":');
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    open(game);
    expect(await screen.findByText(/record of your last action is damaged/)).toBeInTheDocument();
    fireEvent.change(field(), { target: { value: 'I act.' } });
    expect(send()).toBeDisabled();
    expect(localStorage.getItem(outboxKey(RUN_ID))).toBe('{"key":');

    await user.click(screen.getByRole('button', { name: 'Clear it' }));
    expect(confirm).toHaveBeenCalled();
    await waitFor(() => expect(send()).toBeEnabled());
    expect(game.posts).toHaveLength(0);
    confirm.mockRestore();
  });

  it('does not send when the request cannot be saved first', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I act.');
    const realSetItem = Storage.prototype.setItem;
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key: string, value: string) {
      if (key.startsWith('sg.outbox')) throw new DOMException('quota', 'QuotaExceededError');
      return realSetItem.call(this, key, value);
    });
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved on this device');
    expect(game.posts).toHaveLength(0);
    expect(field().value).toBe('I act.');
    setItem.mockRestore();
  });

  it('defers to another tab that owns this run, and never overwrites its action', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    const theirs = { key: newUuid(), body: { input: 'Their action.', stateVersion: 0 }, createdAt: 'x' };
    localStorage.setItem(outboxKey(RUN_ID), JSON.stringify(theirs));
    act(() => { window.dispatchEvent(new StorageEvent('storage', { key: outboxKey(RUN_ID) })); });
    expect(await screen.findByText(/another tab or window/)).toBeInTheDocument();

    fireEvent.change(field(), { target: { value: 'Mine.' } });
    expect(send()).toBeDisabled();
    expect(JSON.parse(localStorage.getItem(outboxKey(RUN_ID))!)).toEqual(theirs);
    expect(game.posts).toHaveLength(0);
    void user;
  });

  it('does not send while another tab holds the run\'s turn lock', async () => {
    const game = new FakeGame().install();
    localStorage.setItem(`sg.turnlock.v1.${RUN_ID}`, JSON.stringify({ owner: 'other-tab', until: Date.now() + 60_000 }));
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I act.{Enter}');
    expect(await screen.findByText(/another tab or window/)).toBeInTheDocument();
    expect(game.posts).toHaveLength(0);
    expect(readOutbox(RUN_ID).kind).toBe('none');
    expect(field().value).toBe('I act.');
  });

  it('a late, older view never rolls back HP or the pending defense', async () => {
    const game = new FakeGame().install();
    game.next.push({ enemy: { attacker: 'Bone Warden', action: 'ATTACK' }, hpLost: 4 });
    const old = { ...game.view };
    game.staleViews.push(old, old); // the reconciling GET answers with the pre-turn view
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I strike.{Enter}');
    await screen.findByText('Narration of turn 1.');
    await waitFor(() => expect(game.calls.filter((c) => c.method === 'GET' && !c.url.includes('chronicle')).length).toBeGreaterThan(0));
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.getByRole('button', { name: /Wren, 20 of 24/ })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('a thrust aimed at your chest.');
  });

  it('reconciles with the chronicle without duplicating the turn or restarting its reveal', async () => {
    const game = new FakeGame().install();
    const user = userEvent.setup();
    open(game);
    await screen.findByLabelText('Introduction');
    await user.type(field(), 'I listen.{Enter}');
    const passage = (await screen.findByText('Narration of turn 1.')).closest('section')!;
    await waitFor(() => expect(game.calls.some((c) => c.url.includes('/chronicle') && game.calls.indexOf(c) > 1)).toBe(true));
    await waitFor(() => expect(passage.querySelectorAll('p[data-hidden]').length).toBe(0));
    expect(passage.isConnected).toBe(true);
    expect(turnsShown()).toEqual([1]);
    expect(screen.getAllByText('Narration of turn 1.')).toHaveLength(1);
  });
});
