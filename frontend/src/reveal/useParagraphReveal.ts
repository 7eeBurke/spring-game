import { useCallback, useEffect, useRef, useState } from 'react';
import { revealConfig } from './revealConfig';
import { useReducedMotion } from './useReducedMotion';

export interface ParagraphReveal {
  /** How many paragraphs are visible. */
  visible: number;
  /** Still revealing: the passage can be tapped to show it all. */
  revealing: boolean;
  revealAll: () => void;
}

/**
 * Reveals `count` paragraphs one after another, `stepMs` apart, when `animate` is set. Without
 * `animate` (recovered or already shown narration) or with reduced motion, everything is visible
 * at once and no timer runs. Calls `onDone` once when the passage is fully shown.
 */
export function useParagraphReveal(count: number, animate: boolean, onDone?: () => void,
  stepMs: number = revealConfig.stepMs): ParagraphReveal {
  const reducedMotion = useReducedMotion();
  const active = animate && !reducedMotion && count > 1;
  const [visible, setVisible] = useState(active ? 1 : count);
  // onDone fires exactly once, including when nothing animates (recovered text, reduced motion).
  const done = useRef(false);
  const onDoneRef = useRef(onDone);
  onDoneRef.current = onDone;

  const finish = useCallback(() => {
    setVisible(count);
    if (!done.current) {
      done.current = true;
      onDoneRef.current?.();
    }
  }, [count]);

  useEffect(() => {
    if (!active) {
      finish();
      return;
    }
    if (visible >= count) {
      finish();
      return;
    }
    const timer = setTimeout(() => setVisible((v) => Math.min(count, v + 1)), stepMs);
    return () => clearTimeout(timer);
  }, [active, visible, count, stepMs, finish]);

  return { visible: active ? visible : count, revealing: active && visible < count, revealAll: finish };
}
