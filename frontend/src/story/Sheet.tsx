import { useEffect, useRef, type ReactNode } from 'react';
import styles from './Sheet.module.css';

/**
 * A bottom sheet on the native <dialog> element: modal focus handling, Escape to close and an
 * inert backdrop come from the browser.
 */
export function Sheet({ title, open, onClose, children }: { title: string; open: boolean; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal();
      else dialog.setAttribute('open', '');
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close();
      else dialog.removeAttribute('open');
    }
  }, [open]);

  return (
    <dialog ref={ref} className={styles.sheet} aria-label={title} onClose={onClose}
      onCancel={(e) => { e.preventDefault(); onClose(); }}
      onClick={(e) => { if (e.target === ref.current) onClose(); }}>
      <div className={styles.bar}>
        <h2 className={styles.title}>{title}</h2>
        <button type="button" className={styles.close} onClick={onClose} aria-label="Close">×</button>
      </div>
      <div className={styles.body}>{children}</div>
    </dialog>
  );
}

export const sheetStyles = styles;
