/** Splits narration into paragraphs on line breaks, dropping empty ones. Text is never interpreted as markup. */
export function toParagraphs(text: string): string[] {
  return text
    .split(/\r?\n\s*\r?\n|\r?\n/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}
