import type { ReactNode, Ref } from 'react';
import styles from './StoryLayout.module.css';

export interface StoryLayoutProps {
  header: ReactNode;
  footer: ReactNode;
  children: ReactNode;
  /** The scrolling chronicle element, for scroll position management. */
  scrollRef?: Ref<HTMLElement>;
  /** Floats just above the footer, over the story (for example a "new passage" indicator). */
  overlay?: ReactNode;
}

/** Header, scrolling chronicle and anchored footer (banner and composer). */
export function StoryLayout({ header, footer, children, scrollRef, overlay }: StoryLayoutProps) {
  return (
    <div className={styles.screen}>
      {header}
      <div className={styles.middle}>
        <main ref={scrollRef} className={styles.chronicle} aria-label="Your story" tabIndex={-1}>
          <div className={styles.column}>{children}</div>
        </main>
        {overlay && <div className={styles.overlay}>{overlay}</div>}
      </div>
      <footer className={styles.footer}>
        <div className={styles.footerInner}>{footer}</div>
      </footer>
    </div>
  );
}
