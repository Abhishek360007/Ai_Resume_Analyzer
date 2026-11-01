import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
  fetchPendingCandidates,
  confirmCandidate,
  discardCandidate,
} from '@/store/slices/confirmationSlice'
import type { PendingCandidate } from '@/store/slices/confirmationSlice'
import { RootState } from '@/store'
import ConfirmDialog from '@/components/ConfirmDialog/ConfirmDialog'
import styles from './PendingConfirmations.module.css'

interface WorkHistoryEntry {
  company?: string
  title?: string
  startYear?: number | null
  endYear?: number | null
}

const parseWorkHistory = (raw: string | null): WorkHistoryEntry[] => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const isValidEmail = (email: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)

const PendingConfirmations = () => {
  const dispatch = useDispatch()
  const { pendingCandidates, loading, confirmingId, discardingId, error } = useSelector(
    (state: RootState) => state.confirmation
  )
  const [edits, setEdits] = useState<Record<string, Partial<PendingCandidate>>>({})
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({})
  const [discardTargetId, setDiscardTargetId] = useState<string | null>(null)

  useEffect(() => {
    dispatch(fetchPendingCandidates())
  }, [dispatch])

  const setField = (candidateId: string, field: string, value: string) => {
    setEdits((prev) => ({
      ...prev,
      [candidateId]: { ...prev[candidateId], [field]: value },
    }))
    // Clear the per-card validation error once the user fixes the offending field
    setValidationErrors((prev) => {
      if (!prev[candidateId]) return prev
      const next = { ...prev }
      delete next[candidateId]
      return next
    })
  }

  const validate = (candidate: PendingCandidate): string | null => {
    const edit = edits[candidate.id] ?? {}
    const name = (edit.name ?? candidate.name ?? '').trim()
    const email = (edit.email ?? candidate.email ?? '').trim()
    if (!name) return 'Name is required.'
    if (!email) return 'Email is required.'
    if (!isValidEmail(email)) return 'Enter a valid email address.'
    return null
  }

  const handleConfirm = (candidate: PendingCandidate) => {
    const error = validate(candidate)
    if (error) {
      setValidationErrors((prev) => ({ ...prev, [candidate.id]: error }))
      return
    }
    setValidationErrors((prev) => {
      if (!prev[candidate.id]) return prev
      const next = { ...prev }
      delete next[candidate.id]
      return next
    })

    const edit = edits[candidate.id] ?? {}
    dispatch(
      confirmCandidate({
        id: candidate.id,
        name: (edit.name ?? candidate.name) || undefined,
        email: edit.email ?? candidate.email ?? undefined,
        mobile: edit.mobile ?? candidate.mobile ?? undefined,
        skills: edit.skills ?? candidate.skills ?? undefined,
        yearsOfExperience: edit.yearsOfExperience
          ? Number(edit.yearsOfExperience)
          : candidate.yearsOfExperience ?? undefined,
        experienceSummary: edit.experienceSummary ?? candidate.experienceSummary ?? undefined,
        linkedInUrl: edit.linkedInUrl ?? candidate.linkedInUrl ?? undefined,
        githubUrl: edit.githubUrl ?? candidate.githubUrl ?? undefined,
        twitterUrl: edit.twitterUrl ?? candidate.twitterUrl ?? undefined,
      })
    )
  }

  const handleConfirmDiscard = () => {
    if (discardTargetId) {
      dispatch(discardCandidate(discardTargetId))
    }
    setDiscardTargetId(null)
  }

  const verdictClass = (verdict?: string) => {
    switch (verdict) {
      case 'GENUINE':
        return styles.verdictGenuine
      case 'SUSPICIOUS':
        return styles.verdictSuspicious
      default:
        return styles.verdictUnknown
    }
  }

  if (loading && pendingCandidates.length === 0) {
    return <div className={styles.page}><h2>Confirmations</h2><p>Loading pending candidates…</p></div>
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h2>Confirmations</h2>
        <button className={styles.refreshButton} onClick={() => dispatch(fetchPendingCandidates())}>
          Refresh
        </button>
      </div>

      {error && <p style={{ color: '#e53e3e', marginBottom: '1rem' }}>{error}</p>}

      {pendingCandidates.length === 0 ? (
        <div className={styles.empty}>
          <p>No candidates awaiting confirmation. Uploaded resumes are listed here once the
          AI analysis has extracted their details.</p>
        </div>
      ) : (
        <div className={styles.grid}>
          {pendingCandidates.map((candidate) => {
            const edit = edits[candidate.id] ?? {}
            const workHistory = parseWorkHistory(candidate.workHistory)
            return (
              <div key={candidate.id} className={styles.card}>
                <div className={styles.cardHeader}>
                  <div>
                    <h3 className={styles.candidateName}>{candidate.name}</h3>
                    <p className={styles.filename}>{candidate.resumeFilename ?? 'resume'}</p>
                  </div>
                  <span className={`${styles.badge} ${candidate.status === 'PENDING_CONFIRMATION' ? styles.badgePending : ''}`}>
                    {candidate.status.replace(/_/g, ' ')}
                  </span>
                </div>

                <div className={styles.section}>
                  <h4>Extracted Details</h4>
                  {validationErrors[candidate.id] && (
                    <div className={styles.validationError} role="alert">
                      {validationErrors[candidate.id]}
                    </div>
                  )}
                  <div className={styles.fieldRow}>
                    <label>Name</label>
                    <input
                      value={edit.name ?? candidate.name}
                      onChange={(e) => setField(candidate.id, 'name', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Email</label>
                    <input
                      value={edit.email ?? candidate.email ?? ''}
                      onChange={(e) => setField(candidate.id, 'email', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Mobile</label>
                    <input
                      value={edit.mobile ?? candidate.mobile ?? ''}
                      onChange={(e) => setField(candidate.id, 'mobile', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Years of Experience</label>
                    <input
                      type="number"
                      value={edit.yearsOfExperience ?? candidate.yearsOfExperience ?? ''}
                      onChange={(e) => setField(candidate.id, 'yearsOfExperience', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Skills</label>
                    <textarea
                      rows={2}
                      value={edit.skills ?? candidate.skills ?? ''}
                      onChange={(e) => setField(candidate.id, 'skills', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Experience Summary</label>
                    <textarea
                      rows={3}
                      value={edit.experienceSummary ?? candidate.experienceSummary ?? ''}
                      onChange={(e) => setField(candidate.id, 'experienceSummary', e.target.value)}
                    />
                  </div>
                </div>

                {workHistory.length > 0 && (
                  <div className={styles.section}>
                    <h4>Employment History</h4>
                    <ul className={styles.workList}>
                      {workHistory.map((w, i) => (
                        <li key={i}>
                          <strong>{w.company}</strong>
                          {w.title ? ` — ${w.title}` : ''}
                          {w.startYear ? ` (${w.startYear}${w.endYear ? `–${w.endYear}` : '–present'})` : ''}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className={styles.section}>
                  <h4>Social Profiles</h4>
                  <div className={styles.fieldRow}>
                    <label>LinkedIn URL</label>
                    <input
                      placeholder="https://www.linkedin.com/in/…"
                      value={edit.linkedInUrl ?? candidate.linkedInUrl ?? ''}
                      onChange={(e) => setField(candidate.id, 'linkedInUrl', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>GitHub URL</label>
                    <input
                      placeholder="https://github.com/…"
                      value={edit.githubUrl ?? candidate.githubUrl ?? ''}
                      onChange={(e) => setField(candidate.id, 'githubUrl', e.target.value)}
                    />
                  </div>
                  <div className={styles.fieldRow}>
                    <label>Twitter / X URL</label>
                    <input
                      placeholder="https://x.com/…"
                      value={edit.twitterUrl ?? candidate.twitterUrl ?? ''}
                      onChange={(e) => setField(candidate.id, 'twitterUrl', e.target.value)}
                    />
                  </div>
                </div>

                {candidate.companyImpressions && candidate.companyImpressions.length > 0 && (
                  <div className={styles.section}>
                    <h4>Company Impressions</h4>
                    <ul className={styles.impressionList}>
                      {candidate.companyImpressions.map((imp) => (
                        <li key={imp.id}>
                          <span className={`${styles.verdict} ${verdictClass(imp.verdict)}`}>
                            {imp.verdict}
                          </span>
                          <strong>{imp.companyName}</strong>
                          <p>{imp.summary}</p>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className={styles.actions}>
                  <button
                    className={styles.discardButton}
                    onClick={() => setDiscardTargetId(candidate.id)}
                    disabled={confirmingId === candidate.id || discardingId === candidate.id}
                  >
                    Discard
                  </button>
                  <button
                    className={styles.confirmButton}
                    onClick={() => handleConfirm(candidate)}
                    disabled={confirmingId === candidate.id || discardingId === candidate.id}
                  >
                    {confirmingId === candidate.id ? 'Confirming…' : 'Confirm Candidate'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <ConfirmDialog
        open={discardTargetId !== null}
        title="Discard candidate"
        message="Are you sure you want to discard this extracted candidate? It will be permanently removed without saving. This cannot be undone."
        confirmLabel="Discard"
        danger
        busy={discardingId === discardTargetId}
        onConfirm={handleConfirmDiscard}
        onCancel={() => setDiscardTargetId(null)}
      />
    </div>
  )
}

export default PendingConfirmations
