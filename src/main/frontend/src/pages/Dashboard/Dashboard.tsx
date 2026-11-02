import { useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import {
  ArrowRight,
  Briefcase,
  Clock,
  Layers,
  RefreshCw,
  Target,
  Upload,
  Users,
} from 'lucide-react'
import { fetchCandidates } from '@/store/slices/candidatesSlice'
import { fetchJobs } from '@/store/slices/jobsSlice'
import { selectUserFullName } from '@store/selectors/authSelectors'
import { RootState } from '@/store'
import styles from './Dashboard.module.css'

const greeting = () => {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

const byNewest = (a: { createdAt: string }, b: { createdAt: string }) =>
  new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()

const Dashboard = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const fullName = useSelector(selectUserFullName)
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

  const activeJobs = jobs.filter((job) => job.isActive)
  const loading = candidatesLoading || jobsLoading
  const error = candidatesError || jobsError

  const withExperience = candidates.filter((c) => typeof c.yearsOfExperience === 'number')
  const avgExperience = withExperience.length
    ? withExperience.reduce((sum, c) => sum + (c.yearsOfExperience ?? 0), 0) / withExperience.length
    : null

  const recentCandidates = [...candidates].sort(byNewest).slice(0, 5)
  const openRoles = [...activeJobs].sort(byNewest).slice(0, 5)
  const firstName = fullName?.split(' ')[0]

  const stats = [
    { label: 'Total Candidates', value: candidates.length, icon: Users, tone: styles.toneIndigo },
    { label: 'Active Jobs', value: activeJobs.length, icon: Briefcase, tone: styles.toneEmerald },
    { label: 'Total Jobs', value: jobs.length, icon: Layers, tone: styles.toneViolet },
    {
      label: 'Avg. Experience',
      value: avgExperience === null ? '—' : `${avgExperience.toFixed(1)} yrs`,
      icon: Clock,
      tone: styles.toneAmber,
    },
  ]

  return (
    <div className={styles.dashboard}>
      <div className={styles.header}>
        <div>
          <h2>Dashboard</h2>
          <p className={styles.subtitle}>
            {greeting()}{firstName ? `, ${firstName}` : ''}. Here is your hiring pipeline at a glance.
          </p>
        </div>
        <button className={styles.refreshButton} onClick={load} disabled={loading}>
          <RefreshCw size={16} className={loading ? styles.spinning : undefined} />
          Refresh
        </button>
      </div>

      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}

      <div className={styles.stats}>
        {stats.map(({ label, value, icon: Icon, tone }) => (
          <div key={label} className={styles.statCard}>
            <div className={`${styles.statIcon} ${tone}`}>
              <Icon size={20} />
            </div>
            <div>
              <h3>{label}</h3>
              <p className={loading ? styles.statLoading : undefined}>{value}</p>
            </div>
          </div>
        ))}
      </div>

      {!loading && candidates.length === 0 && jobs.length === 0 ? (
        <div className={styles.empty}>
          <div className={styles.emptyIcon}><Upload size={24} /></div>
          <h3>Start building your talent pool</h3>
          <p>
            Upload resumes to build your candidate pool, then create a job
            requirement to start AI-powered matching.
          </p>
        </div>
      ) : (
        <div className={styles.panels}>
          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <h3>Recent candidates</h3>
              <button className={styles.linkButton} onClick={() => navigate('/candidates')}>
                View all <ArrowRight size={14} />
              </button>
            </div>
            {recentCandidates.length === 0 ? (
              <p className={styles.panelEmpty}>No candidates yet.</p>
            ) : (
              <ul className={styles.list}>
                {recentCandidates.map((candidate) => (
                  <li key={candidate.id}>
                    <span className={styles.avatar}>{candidate.name?.charAt(0).toUpperCase() || '?'}</span>
                    <div className={styles.listMain}>
                      <span className={styles.listTitle}>{candidate.name}</span>
                      <span className={styles.listMeta}>{candidate.email}</span>
                    </div>
                    {typeof candidate.yearsOfExperience === 'number' && (
                      <span className={styles.pill}>{candidate.yearsOfExperience} yrs</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <h3>Open roles</h3>
              <button className={styles.linkButton} onClick={() => navigate('/matching')}>
                Match <ArrowRight size={14} />
              </button>
            </div>
            {openRoles.length === 0 ? (
              <p className={styles.panelEmpty}>No active job requirements.</p>
            ) : (
              <ul className={styles.list}>
                {openRoles.map((job) => (
                  <li key={job.id}>
                    <span className={`${styles.avatar} ${styles.avatarJob}`}><Briefcase size={15} /></span>
                    <div className={styles.listMain}>
                      <span className={styles.listTitle}>{job.title}</span>
                      <span className={styles.listMeta}>
                        {job.minExperienceYears}–{job.maxExperienceYears} yrs experience
                      </span>
                    </div>
                    <span className={`${styles.pill} ${styles.pillActive}`}>Active</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}

      <h2 className={styles.sectionTitle}>Quick Actions</h2>
      <div className={styles.quickActions}>
        <div className={styles.actionCard}>
          <div className={`${styles.actionIcon} ${styles.toneIndigo}`}><Upload size={20} /></div>
          <h3>Upload Resumes</h3>
          <p>Upload single or multiple resumes for AI-powered analysis and candidate extraction.</p>
          <button className={styles.actionButton} onClick={() => navigate('/upload')}>
            Upload Now <ArrowRight size={15} />
          </button>
        </div>
        <div className={styles.actionCard}>
          <div className={`${styles.actionIcon} ${styles.toneEmerald}`}><Briefcase size={20} /></div>
          <h3>Create Job Posting</h3>
          <p>Create a new job requirement to match against existing candidates.</p>
          <button className={styles.actionButton} onClick={() => navigate('/jobs')}>
            Create Job <ArrowRight size={15} />
          </button>
        </div>
        <div className={styles.actionCard}>
          <div className={`${styles.actionIcon} ${styles.toneViolet}`}><Target size={20} /></div>
          <h3>Match Candidates</h3>
          <p>Use AI to match candidates against job requirements with detailed scoring.</p>
          <button className={styles.actionButton} onClick={() => navigate('/matching')}>
            Start Matching <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </div>
  )
}

export default Dashboard
