import { createSlice, PayloadAction } from '@reduxjs/toolkit'

export type CompanyVerdict = 'GENUINE' | 'SUSPICIOUS' | 'UNKNOWN'

export interface CompanyImpression {
  id: string
  companyName: string
  industry: string | null
  verdict: CompanyVerdict
  summary: string | null
  confidenceScore: number | null
  evidence: string | null
}

export interface PendingCandidate {
  id: string
  name: string
  email: string | null
  mobile: string | null
  skills: string | null
  yearsOfExperience: number | null
  academicBackground: string | null
  experienceSummary: string | null
  workHistory: string | null
  linkedInUrl: string | null
  githubUrl: string | null
  twitterUrl: string | null
  status: string
  resumeFilename: string | null
  createdAt: string
  companyImpressions?: CompanyImpression[]
}

export interface ConfirmCandidatePayload {
  id: string
  name?: string
  email?: string
  mobile?: string
  skills?: string
  yearsOfExperience?: number
  experienceSummary?: string
  linkedInUrl?: string
  githubUrl?: string
  twitterUrl?: string
}

interface ConfirmationState {
  pendingCandidates: PendingCandidate[]
  loading: boolean
  confirmingId: string | null
  discardingId: string | null
  error: string | null
}

const initialState: ConfirmationState = {
  pendingCandidates: [],
  loading: false,
  confirmingId: null,
  discardingId: null,
  error: null,
}

const confirmationSlice = createSlice({
  name: 'confirmation',
  initialState,
  reducers: {
    fetchPendingCandidates: (state) => {
      state.loading = true
      state.error = null
    },
    fetchPendingCandidatesSuccess: (state, action: PayloadAction<PendingCandidate[]>) => {
      state.pendingCandidates = action.payload
      state.loading = false
    },
    fetchPendingCandidatesFailure: (state, action: PayloadAction<string>) => {
      state.loading = false
      state.error = action.payload
    },
    confirmCandidate: (state, action: PayloadAction<ConfirmCandidatePayload>) => {
      state.confirmingId = action.payload.id
      state.error = null
    },
    confirmCandidateSuccess: (state, action: PayloadAction<{ id: string }>) => {
      state.pendingCandidates = state.pendingCandidates.filter(
        (c) => c.id !== action.payload.id
      )
      state.confirmingId = null
    },
    confirmCandidateFailure: (state, action: PayloadAction<string>) => {
      state.confirmingId = null
      state.error = action.payload
    },
    discardCandidate: (state, action: PayloadAction<string>) => {
      state.discardingId = action.payload
      state.error = null
    },
    discardCandidateSuccess: (state, action: PayloadAction<{ id: string }>) => {
      state.pendingCandidates = state.pendingCandidates.filter(
        (c) => c.id !== action.payload.id
      )
      state.discardingId = null
    },
    discardCandidateFailure: (state, action: PayloadAction<string>) => {
      state.discardingId = null
      state.error = action.payload
    },
  },
})

export const {
  fetchPendingCandidates,
  fetchPendingCandidatesSuccess,
  fetchPendingCandidatesFailure,
  confirmCandidate,
  confirmCandidateSuccess,
  confirmCandidateFailure,
  discardCandidate,
  discardCandidateSuccess,
  discardCandidateFailure,
} = confirmationSlice.actions

export default confirmationSlice.reducer
