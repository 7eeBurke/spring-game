import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { GameView } from '../api/types';
import { StoryScreen } from '../screens/StoryScreen';
import { generateRunToken, newUuid } from '../security/tokens';
import { addRun, type VaultEntry } from '../storage/vault';
import { fakeServer, RUN_ID, sampleView, turn } from '../test/fakeApi';
import { TurnEntry } from './TurnEntry';

function savedRun(): VaultEntry {
  const entry: VaultEntry = { localId: newUuid(), runId: RUN_ID, token: generateRunToken(), creationKey: newUuid(),
    createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null, summary: null };
  addRun(entry);
  return entry;
}

function openStory(view: GameView = sampleView()) {
  return render(<StoryScreen entry={savedRun()} view={view} onBack={() => undefined} />);
}

const HOSTILE = '<img src=x onerror=alert(1)>';

describe('a chronicle turn', () => {
  it('shows exact wording with line breaks, scene, narration, attack and ending', () => {
    render(<TurnEntry fresh={false} onRevealed={() => undefined} turn={turn(7, {
      action: { text: 'I wait.\nThen I strike.', kind: 'FREE_TEXT' },
      enteredScene: { scene: 'Ossuary', zone: 'Bone Stair' },
      enemy: { attacker: 'Bone Warden', action: 'ATTACK', cueText: 'Incoming: an overhead strike coming down from above.',
        narration: { text: 'Its hammer rises.', source: 'FALLBACK' } },
      ending: 'DEAD',
    })} />);
    expect(screen.getByText('You').parentElement?.textContent).toBe('YouI wait.\nThen I strike.');
    expect(screen.getByRole('heading', { name: 'Ossuary' })).toBeInTheDocument();
    expect(screen.getByText('Bone Stair')).toBeInTheDocument();
    expect(screen.getByText('an overhead strike coming down from above.')).toBeInTheDocument();
    expect(screen.getByText('Its hammer rises.')).toBeInTheDocument();
    expect(screen.getByText('Here your story ends')).toBeInTheDocument();
  });

  it('marks a walk to another place in the same scene with a quiet subheading, not a new scene', () => {
    const { rerender } = render(<TurnEntry fresh={false} onRevealed={() => undefined} turn={turn(3, { movedTo: 'Vestment Racks' })} />);
    expect(screen.getByLabelText('Now at Vestment Racks')).toHaveTextContent('Vestment Racks');
    expect(screen.queryByRole('heading', { name: 'Vestment Racks' })).toBeNull();

    // Crossing into a new scene gets the scene heading only; older turns without the field get neither.
    rerender(<TurnEntry fresh={false} onRevealed={() => undefined}
      turn={turn(4, { enteredScene: { scene: 'Sacristy', zone: 'Vestry Threshold' }, movedTo: 'Vestry Threshold' })} />);
    expect(screen.queryByLabelText(/^Now at/)).toBeNull();
    rerender(<TurnEntry fresh={false} onRevealed={() => undefined} turn={turn(5, {})} />);
    expect(screen.queryByLabelText(/^Now at/)).toBeNull();
  });

  it('handles older turns without recorded words, pending narration and a holding enemy', () => {
    render(<TurnEntry fresh={false} onRevealed={() => undefined} turn={turn(2, {
      action: null, narration: null, narrationPending: true,
      enemy: { attacker: 'Hollow Acolyte', action: 'HOLD', cueText: null, narration: null },
    })} />);
    expect(screen.getByText('Your words for this turn were not recorded.')).toBeInTheDocument();
    expect(screen.getByText('The telling of this turn has not arrived yet.')).toBeInTheDocument();
    expect(screen.getByText('The Hollow Acolyte holds back.')).toBeInTheDocument();
  });

  it('renders hostile text as text', () => {
    const { container } = render(<TurnEntry fresh={false} onRevealed={() => undefined} turn={turn(1, {
      action: { text: HOSTILE, kind: 'FREE_TEXT' }, narration: { text: HOSTILE, source: 'AI' },
      enemy: { attacker: HOSTILE, action: 'ATTACK', cueText: HOSTILE, narration: { text: HOSTILE, source: 'AI' } },
    })} />);
    expect(container.querySelector('img, script')).toBeNull();
  });
});

describe('the live story screen', () => {
  it('opens a run with no turns on its introduction', async () => {
    const api = fakeServer(0);
    openStory();
    expect(await screen.findByLabelText('Introduction')).toHaveTextContent('You are Wren.');
    expect(within(screen.getByRole('main')).getByText('Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'The Last Lantern' })).toBeInTheDocument();
    expect(api.calls.map((c) => c.method)).toEqual(['GET']);
    expect(api.calls[0]!.url).toContain('/chronicle?limit=20');
    expect(api.calls[0]!.url).not.toContain('before');
  });

  it('loads the latest 20 turns, then older pages with the cursor, in order and without duplicates', async () => {
    const api = fakeServer(45);
    const user = userEvent.setup();
    openStory();
    expect(await screen.findByText('Narration of turn 45.')).toBeInTheDocument();
    expect(screen.queryByText('Narration of turn 25.')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Earlier in your tale' }));
    expect(await screen.findByText('Narration of turn 6.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Earlier in your tale' }));
    expect(await screen.findByLabelText('Introduction')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Earlier in your tale' })).toBeNull();

    const turns = screen.getAllByRole('article').map((a) => Number(a.getAttribute('data-turn')));
    expect(turns).toEqual(Array.from({ length: 45 }, (_, i) => i + 1));
    expect(api.calls.map((c) => new URL(c.url, 'http://x').searchParams.get('before'))).toEqual([null, '26', '6']);
    expect(api.calls.every((c) => c.method === 'GET')).toBe(true);
  });

  it('shows the defense cue at once from the view, before any narration', async () => {
    fakeServer(1);
    openStory(sampleView({ awaiting: 'DEFENSE',
      pendingAttack: { alias: 'attack_1', attacker: 'Bone Warden', cueText: 'Incoming: a thrust aimed at your chest.', narration: null } }));
    expect(screen.getByRole('alert')).toHaveTextContent('a thrust aimed at your chest.');
    expect(screen.getByPlaceholderText('How do you defend?')).toBeInTheDocument();
  });

  it('checks a finalizing turn with one read only, and fills in its telling', async () => {
    const pendingView = sampleView({ finalizing: true, lastTurn: { turnNumber: 2, narration: null } });
    const api = fakeServer(2, pendingView, (n) => (n === 2 ? turn(2, { narration: null, narrationPending: true }) : turn(n)));
    const user = userEvent.setup();
    openStory(pendingView);
    expect(await screen.findByText('The telling of this turn has not arrived yet.')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('has not arrived yet');

    // Elsewhere, the narration completes.
    api.state.view = sampleView({ lastTurn: { turnNumber: 2, narration: { text: 'Narration of turn 2.', source: 'AI' } } });
    api.state.turnFor = (n) => turn(n);
    const before = api.calls.length;
    await user.click(screen.getByRole('button', { name: 'Check again' }));

    expect(await screen.findByText('Narration of turn 2.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Check again' })).toBeNull();
    expect(api.calls.slice(before).map((c) => c.method)).toEqual(['GET', 'GET']);
  });

  it('restores a coherent window when the run moved on by more than a page', async () => {
    const view = sampleView({ finalizing: true });
    const api = fakeServer(20, view);
    const user = userEvent.setup();
    openStory(view);
    expect(await screen.findByLabelText('Introduction')).toBeInTheDocument();

    api.state.total = 65; // 45 turns played on another device
    await user.click(screen.getByRole('button', { name: 'Check again' }));
    expect(await screen.findByText('Narration of turn 65.')).toBeInTheDocument();
    let turns = screen.getAllByRole('article').map((a) => Number(a.getAttribute('data-turn')));
    expect(turns).toEqual(Array.from({ length: 20 }, (_, i) => 46 + i));
    expect(screen.queryByLabelText('Introduction')).toBeNull();

    for (let i = 0; i < 3; i++) await user.click(await screen.findByRole('button', { name: 'Earlier in your tale' }));
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(65));
    turns = screen.getAllByRole('article').map((a) => Number(a.getAttribute('data-turn')));
    expect(turns).toEqual(Array.from({ length: 65 }, (_, i) => i + 1));
  });

  it('presents a finished tale as a completed chronicle with no action composer', async () => {
    fakeServer(3, sampleView({ status: 'VICTORIOUS', awaiting: 'NONE' }), (n) => turn(n, n === 3 ? { ending: 'VICTORIOUS' } : {}));
    openStory(sampleView({ status: 'VICTORIOUS', awaiting: 'NONE' }));
    expect(await screen.findByText('The Chapel falls still')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByPlaceholderText('What do you do?')).toBeNull();
    expect(screen.getByRole('button', { name: 'Return to your tales' })).toBeInTheDocument();
  });

  it('connects the character and scene sheets to the live view', async () => {
    fakeServer(0);
    const user = userEvent.setup();
    const view = sampleView({
      character: { ...sampleView().character, hp: 6, body: [{ part: 'LEFT_LEG', severity: 'CRIPPLED' }, { part: 'HEAD', severity: 'HEALTHY' }] },
      scene: { zones: [{ alias: 'zone_2', name: 'Lantern Hearth' }], connections: [], objects: [], hazards: [], exits: [],
        creatures: [{ alias: 'entity_1', name: 'Hollow Acolyte', zone: 'zone_2', condition: 'FALLEN' }] },
    });
    openStory(view);
    await user.click(screen.getByRole('button', { name: /Character details: Wren, 6 of 24/ }));
    const sheet = screen.getByRole('dialog', { name: 'Character' });
    expect(within(sheet).getByText('6 / 24')).toBeInTheDocument();
    expect(within(sheet).getByText('Left leg')).toBeInTheDocument();
    expect(within(sheet).queryByText('Head')).toBeNull();
    for (const title of ['Attributes', 'Equipment & Inventory', 'Ability', 'Passive Gift', 'Body']) {
      expect(within(sheet).getByRole('heading', { name: title })).toBeInTheDocument();
    }
    await user.click(screen.getByRole('button', { name: /Scene details/ }));
    expect(within(screen.getByRole('dialog', { name: 'The Last Lantern' })).getByText('fallen')).toBeInTheDocument();
  });

  it('reports a run this device can no longer open', async () => {
    const { fakeApi, apiError } = await import('../test/fakeApi');
    fakeApi(() => apiError(404, 'RUN_NOT_FOUND'));
    openStory();
    expect(await screen.findByRole('alert')).toHaveTextContent('can no longer open');
    expect(screen.getByRole('button', { name: 'Return to your tales' })).toBeInTheDocument();
  });
});
