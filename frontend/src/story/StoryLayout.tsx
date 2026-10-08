import type { ReactNode } from 'react';
import styles from './StoryLayout.module.css';

/** Header, scrolling chronicle and anchored footer (banner and composer). */
export function StoryLayout({ header, footer, children }: { header: ReactNode; footer: ReactNode; children: ReactNode }) {
  return (
    <div className={styles.screen}>
      {header}
      <main className={styles.chronicle} aria-label="Your story" tabIndex={-1}>
        <div className={styles.column}>{children}</div>
      </main>
      <footer className={styles.footer}>
        <div className={styles.footerInner}>{footer}</div>
      </footer>
    </div>
  );
}
