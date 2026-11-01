import { useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { RootState } from '@/store'
import type { ProcessTracker } from '@/store/slices/uploadSlice'
import styles from './ProcessingBanner.module.css'

const isActive = (tracker: ProcessTracker) =>
  tracker.status !== 'COMPLETED' && tracker.status !== 'FAILED'

const getProgress = (tracker: ProcessTracker) => {
  if (tracker.status === 'COMPLETED') return 100
  if (!tracker.totalFiles || tracker.totalFiles === 0) return 0
  return Math.min(100, Math.max(0, Math.round((tracker.processedFiles / tracker.totalFiles) * 100)))
}

/**
 * Slim, always-visible banner showing the most recent in-progress upload batch.
 *
 * Lives in the layout so recruiters see live processing status from any page —
 * updates arrive via SSE regardless of where they are in the app, and the
 * backend snapshot restores it after a page reload.
 */
const ProcessingBanner = () => {
  const { tracker, trackers } = useSelector((state: RootState) => state.upload)

  const activeTrackers = trackers.filter(isActive)
  const current = isActive(tracker ?? ({ status: 'COMPLETED' } as ProcessTracker))
    ? tracker
    : activeTrackers[0]

  if (!current) {
    return null
  }

  const progress = getProgress(current)

  return (
    <Link to="/upload" className={styles.banner}>
      <div className={styles.info}>
        <span className={styles.label}>Processing</span>
        <span className={styles.filename}>
          {current.uploadedFilename || 'Resume batch'}
        </span>
        <span className={styles.status}>{current.status.replace(/_/g, ' ')}</span>
      </div>
      <div className={styles.progressWrap}>
        <div className={styles.progressBar}>
          <div className={styles.progressFill} style={{ width: `${progress}%` }} />
        </div>
        <span className={styles.progressText}>
          {current.processedFiles}/{current.totalFiles} files ({progress}%)
        </span>
      </div>
    </Link>
  )
}

export default ProcessingBanner
