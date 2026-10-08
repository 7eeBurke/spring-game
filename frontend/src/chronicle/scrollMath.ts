/** How close to the bottom still counts as "reading the latest passage". */
export const NEAR_BOTTOM_PX = 120;

export function isNearBottom(el: { scrollTop: number; scrollHeight: number; clientHeight: number }, threshold = NEAR_BOTTOM_PX): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold;
}

/**
 * The scroll position that keeps the same passage under the reader's eyes after older content was
 * inserted above it: everything below moved down by the growth in height.
 */
export function prependScrollTop(oldTop: number, oldHeight: number, newHeight: number): number {
  return Math.max(0, oldTop + (newHeight - oldHeight));
}
