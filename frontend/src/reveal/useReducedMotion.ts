import { useEffect, useState } from 'react';

const QUERY = '(prefers-reduced-motion: reduce)';

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(QUERY).matches;
}

/** Whether the player asked their device for reduced motion; follows changes live. */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(prefersReducedMotion);
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return;
    const media = window.matchMedia(QUERY);
    const onChange = () => setReduced(media.matches);
    media.addEventListener?.('change', onChange);
    return () => media.removeEventListener?.('change', onChange);
  }, []);
  return reduced;
}
