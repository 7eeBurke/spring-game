import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NarrationPassage } from '../story/Passages';
import { revealConfig } from './revealConfig';

const THREE = { text: 'One.\n\nTwo.\n\nThree.', source: 'AI' as const };

function hidden(container: HTMLElement) {
  return container.querySelectorAll('p[data-hidden]').length;
}

function setReducedMotion(reduced: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduced && query.includes('reduce'), media: query,
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

describe('paragraph reveal', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setReducedMotion(false);
  });
  afterEach(() => vi.useRealTimers());

  it('shows recovered narration at once, with no timers', () => {
    const { container } = render(<NarrationPassage narration={THREE} />);
    expect(hidden(container)).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('reveals new narration one paragraph every 250ms', () => {
    const done = vi.fn();
    const { container } = render(<NarrationPassage narration={THREE} reveal onRevealed={done} />);
    expect(revealConfig.stepMs).toBe(250);
    expect(hidden(container)).toBe(2);
    act(() => vi.advanceTimersByTime(249));
    expect(hidden(container)).toBe(2);
    act(() => vi.advanceTimersByTime(1));
    expect(hidden(container)).toBe(1);
    act(() => vi.advanceTimersByTime(250));
    expect(hidden(container)).toBe(0);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('keeps every paragraph in the document for screen readers while revealing', () => {
    render(<NarrationPassage narration={THREE} reveal />);
    expect(screen.getByText('Three.')).toBeInTheDocument();
    expect(document.querySelector('[aria-live]')).toBeNull();
  });

  it('shows the whole passage at once on a tap', () => {
    const done = vi.fn();
    const { container } = render(<NarrationPassage narration={THREE} reveal onRevealed={done} />);
    fireEvent.click(screen.getByLabelText('Narration'));
    expect(hidden(container)).toBe(0);
    expect(done).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(1000));
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('offers a keyboard way to skip the reveal', () => {
    const { container } = render(<NarrationPassage narration={THREE} reveal />);
    const skip = screen.getByRole('button', { name: 'Show the whole passage' });
    skip.focus();
    fireEvent.click(skip);
    expect(hidden(container)).toBe(0);
    expect(screen.queryByRole('button', { name: 'Show the whole passage' })).toBeNull();
  });

  it('respects reduced motion', () => {
    setReducedMotion(true);
    const done = vi.fn();
    const { container } = render(<NarrationPassage narration={THREE} reveal onRevealed={done} />);
    expect(hidden(container)).toBe(0);
    expect(done).toHaveBeenCalledTimes(1);
  });
});
