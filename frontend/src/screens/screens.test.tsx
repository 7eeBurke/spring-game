import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PreviewApp } from '../preview/PreviewApp';
import { generateRunToken, newUuid } from '../security/tokens';
import { addRun, listRuns } from '../storage/vault';
import { apiError, fakeApi, json, networkFailure, RUN_ID, sampleView } from '../test/fakeApi';
import { App } from '../App';

describe('the shelf', () => {
  it('starts a new tale with the invite code and opens it', async () => {
    const api = fakeApi(() => json(201, { runId: RUN_ID, view: sampleView() }));
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByText('No tales on this device yet.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Begin a new tale' }));
    await user.type(screen.getByLabelText('Invite code'), 'my-invite');
    await user.click(screen.getByRole('button', { name: 'Begin' }));

    expect(await screen.findByRole('button', { name: /Character details: Wren/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Introduction')).toHaveTextContent('You are Wren.');
    expect(api.calls).toHaveLength(1);
    expect(listRuns()[0]!.runId).toBe(RUN_ID);
  });

  it('retries a lost creation from the same form with the same credentials, never a second run', async () => {
    const api = fakeApi((_, i) => (i === 0 ? networkFailure() : json(201, { runId: RUN_ID, view: sampleView() })));
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Begin a new tale' }));
    await user.type(screen.getByLabelText('Invite code'), 'my-invite');
    await user.click(screen.getByRole('button', { name: 'Begin' }));
    expect(await screen.findByText(/Could not reach the game/)).toBeInTheDocument();

    const form = screen.getByRole('form', { name: 'Begin a new tale' });
    await user.click(within(form).getByRole('button', { name: 'Finish creating' }));

    expect(await screen.findByRole('button', { name: /Character details: Wren/ })).toBeInTheDocument();
    expect(api.calls).toHaveLength(2);
    expect(api.calls[1]!.headers.Authorization).toBe(api.calls[0]!.headers.Authorization);
    expect(api.calls[1]!.headers['Idempotency-Key']).toBe(api.calls[0]!.headers['Idempotency-Key']);
    expect(listRuns()).toHaveLength(1);
  });

  it('explains a refused invite in plain words', async () => {
    fakeApi(() => apiError(401, 'UNAUTHORIZED', 'The invite code was not accepted.'));
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Begin a new tale' }));
    await user.type(screen.getByLabelText('Invite code'), 'nope');
    await user.click(screen.getByRole('button', { name: 'Begin' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('That invite code was not accepted.');
    expect(listRuns()).toEqual([]);
  });

  it('shows the working game title, not the region name', () => {
    render(<App />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('The Dying Flame');
    expect(document.title).toBe('The Dying Flame');
  });

  it('forgets a tale only on this device, after warning that the server keeps it', async () => {
    addRun({ localId: newUuid(), runId: RUN_ID, token: generateRunToken(), creationKey: newUuid(),
      createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null,
      summary: { characterName: 'Wren', status: 'ACTIVE', region: null, scene: 'The Last Lantern', hp: 24, maxHp: 24 } });
    const api = fakeApi(() => json(200, {}));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Forget' }));
    expect(confirm.mock.calls[0]![0]).toMatch(/not deleted from the game/);
    expect(confirm.mock.calls[0]![0]).toMatch(/recovery code/);
    expect(listRuns()).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Forget' }));
    expect(listRuns()).toEqual([]);
    expect(api.calls).toHaveLength(0);
    confirm.mockRestore();
  });

  it('lists saved tales with their status and resumes one', async () => {
    addRun({ localId: newUuid(), runId: RUN_ID, token: generateRunToken(), creationKey: newUuid(),
      createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null,
      summary: { characterName: 'Wren', status: 'DEAD', region: 'Hollow Chapel', scene: 'Ossuary', hp: 0, maxHp: 24 } });
    fakeApi(() => json(200, sampleView({ status: 'DEAD' })));
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByText('Fallen')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Read' }));
    expect(await screen.findByText('Here your story ends')).toBeInTheDocument();
  });

  it('reveals a recovery code only when asked', async () => {
    const token = generateRunToken();
    addRun({ localId: newUuid(), runId: RUN_ID, token, creationKey: newUuid(), createdAt: '2026-10-08T00:00:00.000Z',
      lastOpenedAt: null, summary: { characterName: 'Wren', status: 'ACTIVE', region: null, scene: 'The Last Lantern', hp: 24, maxHp: 24 } });
    const user = userEvent.setup();
    render(<App />);

    expect(document.body.innerHTML).not.toContain(token);
    await user.click(screen.getByRole('button', { name: 'Recovery code' }));
    expect(screen.getByText(/Anyone who has it can play this run/)).toBeInTheDocument();
    expect(document.body.innerHTML).not.toContain(token);
    await user.click(screen.getByRole('button', { name: 'Reveal recovery code' }));
    const exportDialog = screen.getByRole('dialog', { name: 'Recovery code' });
    await waitFor(() => expect((within(exportDialog).getByRole('textbox') as HTMLTextAreaElement).value).toContain(token));
    expect(window.location.href).not.toContain(token);
  });

  it('restores a tale from a recovery code', async () => {
    const token = generateRunToken();
    const { encodeRecoveryCode } = await import('../security/recovery');
    const code = await encodeRecoveryCode(RUN_ID, token);
    fakeApi(() => json(200, sampleView()));
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Restore from a recovery code' }));
    await user.type(within(screen.getByRole('dialog', { name: 'Restore a tale' })).getByRole('textbox'), code);
    await user.click(screen.getByRole('button', { name: 'Restore tale' }));

    expect(await screen.findByRole('button', { name: /Character details: Wren/ })).toBeInTheDocument();
    expect(listRuns()[0]).toMatchObject({ runId: RUN_ID, token });
  });
});

describe('the design preview', () => {
  it('is clearly labelled sample data and never touches the network or storage', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    render(<PreviewApp />);

    expect(screen.getByText('Design preview · sample text')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send action' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('an overhead strike');
    await user.click(screen.getByRole('button', { name: 'Fallen' }));
    expect(screen.getByText('Here your story ends')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Plain telling' }));
    expect(screen.getByText(/told plainly/i)).toBeInTheDocument();

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    fetchSpy.mockRestore();
  });

  it('keeps every sample state consistent with the game rules', async () => {
    const user = userEvent.setup();
    render(<PreviewApp />);
    const attacks = () => screen.queryAllByLabelText(/^Incoming attack/);

    // Defending only: the attack is consumed and nothing new is pending.
    await user.click(screen.getByRole('button', { name: 'Your move' }));
    expect(screen.queryByRole('alert')).toBeNull();
    const passages = Array.from(document.querySelectorAll('main section, main blockquote'));
    expect(passages.at(-1)?.getAttribute('aria-label')).toBe('Narration');
    expect(attacks()).toHaveLength(1);

    // A plain telling replaces the AI telling of the same outcome; it is not a second outcome.
    await user.click(screen.getByRole('button', { name: 'Plain telling' }));
    expect(screen.getAllByText(/told plainly/i)).toHaveLength(1);
    expect(screen.queryByText(/clips your shoulder/)).toBeNull();

    // Death follows a new attack that was answered with a defense.
    await user.click(screen.getByRole('button', { name: 'Fallen' }));
    expect(attacks()).toHaveLength(2);

    // No injuries are presented as mechanics: only HP is lasting harm.
    await user.click(screen.getByRole('button', { name: /Character details/ }));
    expect(screen.getByText('Unhurt.')).toBeInTheDocument();
  });
});
