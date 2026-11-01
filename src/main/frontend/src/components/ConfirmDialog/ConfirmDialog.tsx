/**
 * ConfirmDialog Component
 * Reusable confirmation modal — replaces native window.confirm()/alert()
 * with an in-app dialog so destructive actions are clear and styled consistently.
 */

import { useEffect } from 'react'
import styles from './ConfirmDialog.module.css'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  busy?: boolean
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  busy = false,
  danger = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, busy, onCancel])

  if (!open) return null

  return (
    <div className={styles.overlay} onClick={busy ? undefined : onCancel}>
      <div className={styles.dialog} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.title}>{title}</h3>
        <p className={styles.message}>{message}</p>
        <div className={styles.actions}>
          <button onClick={onCancel} disabled={busy} className={styles.cancelButton}>
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            disabled={busy}
            className={`${styles.confirmButton} ${danger ? styles.dangerButton : ''}`}
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
