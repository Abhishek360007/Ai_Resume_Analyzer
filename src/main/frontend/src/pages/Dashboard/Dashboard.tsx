import { useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { fetchCandidates } from '@/store/slices/candidatesSlice'
import { fetchJobs } from '@/store/slices/jobsSlice'
import { RootState } from '@/store'
import styles from './Dashboard.module.css'

const Dashboard = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const candidates = useSelector((state: RootState) => state.candidates.candidates)
  const candidatesLoading = useSelector((state: RootState) => state.candidates.loading)
  const candidatesError = useSelector((state: RootState) => state.candidates.error)
  const jobs = useSelector((state: RootState) => state.jobs.jobs)
  const jobsLoading = useSelector((state: RootState) => state.jobs.loading)
  const jobsError = useSelector((state: RootState) => state.jobs.error)

  const load = useCallback(() => {
    dispatch(fetchCandidates())
    dispatch(fetchJobs())
  }, [dispatch])

  useEffect(() => {
    load()
  }, [load])

  const activeJobs = jobs.filter((job) => job.isActive).length
  const loading = candidatesLoading || jobsLoading
  const error = candidatesError || jobsError

  return (
    <div className={styles.dashboard}>
      <div className={styles.header}>
        <h2>Dashboard</h2>
        <button className={styles.refreshButton} onClick={load} disabled={loading}>
          🔄 Refresh
        </button>
      </div>

      {loading && (
        <div className={styles.loading}>Loading dashboard data…</div>
      )}

      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}

      <div className={styles.stats}>
        <div className={styles.statCard}>
          <h3>Total Candidates</h3>
          <p>{candidates.length}</p>
        </div>
        <div className={styles.statCard}>
          <h3>Active Jobs</h3>
          <p>{activeJobs}</p>
        </div>
        <div className={styles.statCard}>
          <h3>Total Jobs</h3>
          <p>{jobs.length}</p>
        </div>
      </div>

      {!loading && candidates.length === 0 && jobs.length === 0 && (
        <div className={styles.empty}>
          <p>
            No data yet. Upload resumes to build your candidate pool, then create a job
            requirement to start AI-powered matching.
          </p>
        </div>
      )}

      <h2>Quick Actions</h2>
      <div className={styles.quickActions}>
        <div className={styles.actionCard}>
          <h3>📤 Upload Resumes</h3>
          <p>Upload single or multiple resumes for AI-powered analysis and candidate extraction.</p>
          <button className={styles.actionButton} onClick={() => navigate('/upload')}>
            Upload Now
          </button>
        </div>
        <div className={styles.actionCard}>
          <h3>👔 Create Job Posting</h3>
          <p>Create a new job requirement to match against existing candidates.</p>
          <button className={styles.actionButton} onClick={() => navigate('/jobs')}>
            Create Job
          </button>
        </div>
        <div className={styles.actionCard}>
          <h3>🎯 Match Candidates</h3>
          <p>Use AI to match candidates against job requirements with detailed scoring.</p>
          <button className={styles.actionButton} onClick={() => navigate('/matching')}>
            Start Matching
          </button>
        </div>
      </div>
    </div>
  )
}

export default Dashboard
