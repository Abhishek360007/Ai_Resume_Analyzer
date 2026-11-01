import { useEffect, useState, useCallback } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
  fetchJobs,
  createJob,
  updateJob,
  deleteJob,
  selectJob,
} from '@/store/slices/jobsSlice'
import type { Skill } from '@/store/slices/jobsSlice'
import { RootState } from '@/store'
import type { JobRequirement } from '@/store/slices/jobsSlice'
import { selectCanManageJobs, selectIsAdmin } from '@/store/selectors/authSelectors'
import SkillsInput from '@/components/SkillsInput/SkillsInput'
import RangeSlider from '@/components/RangeSlider/RangeSlider'
import FeedbackList from '@/components/FeedbackList/FeedbackList'
import FeedbackForm from '@/components/FeedbackForm/FeedbackForm'
import { EntityType } from '@/components/FeedbackForm/FeedbackForm'
import {
  graphqlClient,
  SEARCH_SKILLS,
  LOAD_SAMPLE_JOBS,
  REMOVE_SAMPLE_JOBS,
} from '@/services/graphql'
import ConfirmDialog from '@/components/ConfirmDialog/ConfirmDialog'
import styles from './JobRequirements.module.css'

const JobRequirements = () => {
  const dispatch = useDispatch()
  const { jobs, selectedJob, loading } = useSelector((state: RootState) => state.jobs)
  const canManageJobs = useSelector(selectCanManageJobs)
  const isAdmin = useSelector(selectIsAdmin)
  const [showForm, setShowForm] = useState(false)
  const [confirmAction, setConfirmAction] = useState<'load' | 'remove' | null>(null)
  const [sampleLoading, setSampleLoading] = useState(false)
  const [sampleMessage, setSampleMessage] = useState<string | null>(null)
  const [sampleError, setSampleError] = useState<string | null>(null)
  const [formData, setFormData] = useState<Partial<JobRequirement>>({
    title: '',
    requiredSkills: '',
    skills: [],
    minExperienceYears: 0,
    maxExperienceYears: 10,
    requiredEducation: '',
    domainRequirements: '',
    description: '',
    isActive: true,
  })
  const [skillSuggestions, setSkillSuggestions] = useState<Skill[]>([])
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)
  const [showFeedbackModal, setShowFeedbackModal] = useState(false)
  const [showFeedbackForm, setShowFeedbackForm] = useState(false)
  const [feedbackRefreshTrigger, setFeedbackRefreshTrigger] = useState(0)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  useEffect(() => {
    dispatch(fetchJobs())
  }, [dispatch])

  const handleSearchSkills = useCallback(async (query: string) => {
    if (!query || query.trim().length < 2) {
      setSkillSuggestions([])
      return
    }

    try {
      const data: { searchSkills: Skill[] } = await graphqlClient.request(SEARCH_SKILLS, {
        name: query.trim(),
      })
      setSkillSuggestions(data.searchSkills)
    } catch (error) {
      console.error('Error searching skills:', error)
      setSkillSuggestions([])
    }
  }, [])

  const handleCreateNew = () => {
    setFormData({
      title: '',
      requiredSkills: '',
      skills: [],
      minExperienceYears: 0,
      maxExperienceYears: 10,
      requiredEducation: '',
      domainRequirements: '',
      description: '',
      isActive: true,
    })
    dispatch(selectJob(null))
    setShowForm(true)
  }

  const handleEdit = (job: JobRequirement) => {
    setFormData({
      ...job,
      skills: job.skills || [],
    })
    dispatch(selectJob(job))
    setShowForm(true)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    
    // Extract skill IDs from selected skills
    const skillIds = formData.skills?.map((skill) => skill.id) || []
    
    const payload = {
      ...formData,
      skillIds,
    }
    
    if (selectedJob) {
      dispatch(updateJob({ ...selectedJob, ...payload } as JobRequirement))
    } else {
      dispatch(createJob(payload as Omit<JobRequirement, 'id' | 'createdAt'>))
    }
    setShowForm(false)
  }

  const handleConfirmDelete = () => {
    if (deleteTargetId) {
      dispatch(deleteJob(deleteTargetId))
    }
    setDeleteTargetId(null)
  }

  const handleLoadSampleJobs = () => {
    setConfirmAction('load')
  }

  const handleRemoveSampleJobs = () => {
    setConfirmAction('remove')
  }

  const runSampleAction = async (action: 'load' | 'remove') => {
    setConfirmAction(null)
    setSampleLoading(true)
    setSampleError(null)
    setSampleMessage(null)
    try {
      if (action === 'load') {
        const data: { loadSampleJobRequirements: JobRequirement[] } = await graphqlClient.request(
          LOAD_SAMPLE_JOBS
        )
        dispatch(fetchJobs())
        const count = data.loadSampleJobRequirements.length
        setSampleMessage(
          count > 0 ? `Loaded ${count} sample job requirement(s).` : 'Sample jobs already loaded.'
        )
      } else {
        const data: { removeSampleJobRequirements: number } = await graphqlClient.request(
          REMOVE_SAMPLE_JOBS
        )
        dispatch(fetchJobs())
        setSampleMessage(`Removed ${data.removeSampleJobRequirements} sample job requirement(s).`)
      }
    } catch (error) {
      setSampleError(
        `Failed to ${action === 'load' ? 'load' : 'remove'} sample jobs: ${errorMessage(error)}`
      )
    } finally {
      setSampleLoading(false)
    }
  }

  const errorMessage = (error: unknown) =>
    error instanceof Error ? error.message : String(error)

  const handleOpenFeedback = (jobId: string) => {
    setSelectedJobId(jobId)
    setShowFeedbackModal(true)
    setShowFeedbackForm(false)
  }

  const handleCloseFeedback = () => {
    setShowFeedbackModal(false)
    setSelectedJobId(null)
    setShowFeedbackForm(false)
  }

  const handleFeedbackSuccess = () => {
    setShowFeedbackForm(false)
    setFeedbackRefreshTrigger((prev) => prev + 1)
  }

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value, type } = e.target
    setFormData((prev) => ({
      ...prev,
      [name]:
        type === 'number'
          ? parseInt(value)
          : type === 'checkbox'
          ? (e.target as HTMLInputElement).checked
          : value,
    }))
  }

  return (
    <div className={styles.jobRequirements}>
      <div className={styles.header}>
        <h2>Job Requirements ({jobs.length})</h2>
        <div className={styles.headerActions}>
          {isAdmin && (
            <>
              <button
                onClick={handleLoadSampleJobs}
                disabled={sampleLoading}
                className={styles.sampleLoadButton}
              >
                {sampleLoading ? 'Working...' : 'Load Sample Jobs'}
              </button>
              <button
                onClick={handleRemoveSampleJobs}
                disabled={sampleLoading}
                className={styles.sampleRemoveButton}
              >
                {sampleLoading ? 'Working...' : 'Remove Sample Jobs'}
              </button>
            </>
          )}
          {canManageJobs && (
            <button onClick={handleCreateNew} className={styles.createButton}>
              + Create New Job
            </button>
          )}
        </div>
      </div>

      {sampleMessage && <p className={styles.sampleMessage}>{sampleMessage}</p>}
      {sampleError && <p className={styles.sampleError}>{sampleError}</p>}

      {showForm && (
        <div className={styles.formOverlay} onClick={() => setShowForm(false)}>
          <div className={styles.formModal} onClick={(e) => e.stopPropagation()}>
            <h3>{selectedJob ? 'Edit Job Requirement' : 'Create Job Requirement'}</h3>
            <form onSubmit={handleSubmit}>
              <div className={styles.formGroup}>
                <label htmlFor="job-title">Job Title *</label>
                <input
                  id="job-title"
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleChange}
                  required
                />
              </div>
              <div className={styles.formGroup}>
                <RangeSlider
                  min={0}
                  max={40}
                  minValue={formData.minExperienceYears || 0}
                  maxValue={formData.maxExperienceYears || 10}
                  step={1}
                  label="Experience Range (years) *"
                  unit="years"
                  onChange={(minValue, maxValue) =>
                    setFormData({
                      ...formData,
                      minExperienceYears: minValue,
                      maxExperienceYears: maxValue,
                    })
                  }
                />
              </div>
              <div className={styles.formGroup}>
                <label>Required Skills *</label>
                <SkillsInput
                  selectedSkills={formData.skills || []}
                  onChange={(skills) => setFormData({ ...formData, skills })}
                  onSearch={handleSearchSkills}
                  suggestions={skillSuggestions}
                  placeholder="Type to search and add skills (e.g., Java, Spring, React)..."
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="required-education">Required Education</label>
                <input
                  id="required-education"
                  type="text"
                  name="requiredEducation"
                  value={formData.requiredEducation}
                  onChange={handleChange}
                  placeholder="e.g., Bachelor's in Computer Science"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="domain">Domain</label>
                <input
                  id="domain"
                  type="text"
                  name="domainRequirements"
                  value={formData.domainRequirements}
                  onChange={handleChange}
                  placeholder="e.g., Fintech, Healthcare, E-commerce"
                />
              </div>
              <div className={styles.formGroup}>
                <label htmlFor="description">Job Description</label>
                <textarea
                  id="description"
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  rows={5}
                  placeholder="Detailed job description..."
                />
              </div>
              <div className={styles.formActions}>
                <button type="button" onClick={() => setShowForm(false)}>
                  Cancel
                </button>
                <button type="submit" className={styles.submitButton}>
                  {selectedJob ? 'Update' : 'Create'} Job
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {loading && <p className={styles.loading}>Loading jobs...</p>}

      {!loading && jobs.length === 0 && (
        <div className={styles.empty}>
          <p>No job requirements found. Create one to start matching candidates!</p>
        </div>
      )}

      {!loading && jobs.length > 0 && (
        <div className={styles.grid}>
          {jobs.map((job) => (
            <div key={job.id} className={styles.card}>
              <div className={styles.cardHeader}>
                <h3>{job.title}</h3>
                <span className={job.isActive ? styles.active : styles.inactive}>
                  {job.isActive ? 'Active' : 'Inactive'}
                </span>
              </div>
              <div className={styles.cardBody}>
                <div className={styles.info}>
                  <strong>Experience:</strong> {job.minExperienceYears} - {job.maxExperienceYears} years
                </div>
                <div className={styles.info}>
                  <strong>Required Skills:</strong>
                  {job.skills && job.skills.length > 0 ? (
                    <div className={styles.skillBadges}>
                      {job.skills.map((skill) => (
                        <span key={skill.id} className={styles.skillBadge}>
                          {skill.name}
                          {skill.category && (
                            <span className={styles.skillCategory}>{skill.category}</span>
                          )}
                        </span>
                      ))}
                    </div>
                  ) : job.requiredSkills ? (
                    <span>{job.requiredSkills}</span>
                  ) : (
                    <span>Not specified</span>
                  )}
                </div>
                {job.requiredEducation && (
                  <div className={styles.info}>
                    <strong>Education:</strong> {job.requiredEducation}
                  </div>
                )}
                {job.domainRequirements && (
                  <div className={styles.info}>
                    <strong>Domain:</strong> {job.domainRequirements}
                  </div>
                )}
                {job.description && (
                  <div className={styles.description}>
                    <strong>Description:</strong>
                    <p>{job.description}</p>
                  </div>
                )}
              </div>
              <div className={styles.cardFooter}>
                <button className={styles.feedbackButton} onClick={() => handleOpenFeedback(job.id)}>
                  💬 Feedback
                </button>
                {canManageJobs && (
                  <button className={styles.editButton} onClick={() => handleEdit(job)}>
                    Edit
                  </button>
                )}
                {canManageJobs && (
                  <button
                    className={styles.deleteButton}
                    onClick={() => setDeleteTargetId(job.id)}
                  >
                    Delete
                  </button>
                )}
                <span className={styles.date}>
                  {new Date(job.createdAt).toLocaleDateString()}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Sample Jobs Confirmation Modal */}
      {confirmAction && (
        <div className={styles.confirmOverlay} onClick={() => setConfirmAction(null)}>
          <div className={styles.confirmModal} onClick={(e) => e.stopPropagation()}>
            <h3>{confirmAction === 'load' ? 'Load Sample Jobs?' : 'Remove Sample Jobs?'}</h3>
            <p>
              {confirmAction === 'load'
                ? 'Load the curated sample job requirements? Existing sample titles will be skipped.'
                : 'Remove all sample job requirements? This deletes the sample jobs and their matches. This cannot be undone.'}
            </p>
            <div className={styles.confirmActions}>
              <button
                type="button"
                className={styles.cancelButton}
                onClick={() => setConfirmAction(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className={
                  confirmAction === 'remove'
                    ? styles.removeConfirmButton
                    : styles.loadConfirmButton
                }
                onClick={() => runSampleAction(confirmAction)}
              >
                {confirmAction === 'load' ? 'Load' : 'Remove'}
              </button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={deleteTargetId !== null}
        title="Delete job requirement"
        message="Are you sure you want to delete this job requirement? Its candidate matches will also be removed. This cannot be undone."
        confirmLabel="Delete"
        danger
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTargetId(null)}
      />

      {/* Feedback Modal */}
      {showFeedbackModal && selectedJobId && (
        <div className={styles.modal} onClick={handleCloseFeedback}>
          <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h2>Job Requirement Feedback</h2>
              <button onClick={handleCloseFeedback} className={styles.closeButton}>
                ✕
              </button>
            </div>

            <div className={styles.modalBody}>
              {!showFeedbackForm ? (
                <>
                  <button
                    onClick={() => setShowFeedbackForm(true)}
                    className={styles.addFeedbackButton}
                  >
                    + Add Feedback
                  </button>
                  <FeedbackList
                    entityId={selectedJobId}
                    entityType={EntityType.JOB_REQUIREMENT}
                    refreshTrigger={feedbackRefreshTrigger}
                  />
                </>
              ) : (
                <FeedbackForm
                  entityId={selectedJobId}
                  entityType={EntityType.JOB_REQUIREMENT}
                  onSuccess={handleFeedbackSuccess}
                  onCancel={() => setShowFeedbackForm(false)}
                />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default JobRequirements
