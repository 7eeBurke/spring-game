import { useCallback, useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react';
import { isNearBottom, prependScrollTop } from './scrollMath';

export interface ChronicleScroll {
  /** New content arrived below while the reader was further up. */
  newBelow: boolean;
  scrollToBottom: () => void;
}

/**
 * Keeps the reader in place:
 * - on first content, jump to the latest passage;
 * - when older passages are prepended (`firstKey` decreases), keep the passage being read still;
 * - when content grows at the end (`lastKey` changes), follow it only if the reader was already
 *   near the bottom; otherwise flag `newBelow` instead of pulling them away.
 */
export function useChronicleScroll(ref: RefObject<HTMLElement | null>, firstKey: number | null, lastKey: string): ChronicleScroll {
  const [newBelow, setNewBelow] = useState(false);
  // Metrics as of the last commit or scroll, i.e. before the next DOM change.
  const metrics = useRef({ top: 0, height: 0, nearBottom: true });
  const seen = useRef<{ first: number | null; last: string; initialised: boolean }>({ first: null, last: '', initialised: false });

  const measure = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    metrics.current = { top: el.scrollTop, height: el.scrollHeight, nearBottom: isNearBottom(el) };
  }, [ref]);

  const scrollToBottom = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    setNewBelow(false);
    measure();
  }, [ref, measure]);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const before = metrics.current;
    const prev = seen.current;
    if (!prev.initialised) {
      if (lastKey) {
        el.scrollTop = el.scrollHeight;
        seen.current = { first: firstKey, last: lastKey, initialised: true };
      }
    } else {
      if (firstKey !== null && prev.first !== null && firstKey < prev.first) {
        el.scrollTop = prependScrollTop(before.top, before.height, el.scrollHeight);
      }
      if (lastKey !== prev.last) {
        if (before.nearBottom) el.scrollTop = el.scrollHeight;
        else setNewBelow(true);
      }
      seen.current = { first: firstKey, last: lastKey, initialised: true };
    }
    measure();
  }, [ref, firstKey, lastKey, measure]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        measure();
        if (metrics.current.nearBottom) setNewBelow(false);
      });
    };
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      el.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(frame);
    };
  }, [ref, measure]);

  return { newBelow, scrollToBottom };
}
