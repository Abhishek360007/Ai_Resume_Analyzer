import { createSlice, PayloadAction } from '@reduxjs/toolkit'

export interface ProcessTracker {
  id: string
  status: 'INITIATED' | 'EMBED_GENERATED' | 'VECTOR_DB_UPDATED' | 'RESUME_ANALYZED' | 'COMPLETED' | 'FAILED'
  totalFiles: number
  processedFiles: number
  failedFiles: number
  message?: string
  uploadedFilename?: string
  createdAt: string
  updatedAt: string
  completedAt?: string
  // Legacy fields for backward compatibility with API
  startTime?: string
  endTime?: string
}

export type SseStatus = 'disconnected' | 'connecting' | 'open' | 'error'

interface UploadState {
  uploading: boolean
  tracker: ProcessTracker | null
  trackers: ProcessTracker[]
  fetchingTrackers: boolean
  sseStatus: SseStatus
  error: string | null
}

const initialState: UploadState = {
  uploading: false,
  tracker: null,
  trackers: [],
  fetchingTrackers: false,
  sseStatus: 'disconnected',
  error: null,
}

const uploadSlice = createSlice({
  name: 'upload',
  initialState,
  reducers: {
    uploadFiles: (state, _action: PayloadAction<File[]>) => {
      state.uploading = true
      state.error = null
    },
    uploadSuccess: (state, action: PayloadAction<ProcessTracker>) => {
      state.tracker = action.payload
      state.uploading = false
    },
    uploadFailure: (state, action: PayloadAction<string>) => {
      state.error = action.payload
      state.uploading = false
    },
    fetchProcessStatus: (_state, _action: PayloadAction<string>) => {
      // Initiates saga to fetch status
    },
    updateProcessStatus: (state, action: PayloadAction<ProcessTracker>) => {
      state.tracker = action.payload
    },
    // Merge a tracker state (from SSE events or a status fetch) into the store.
    // Upserts into the history list and keeps the "current" tracker in sync.
    processEvent: (state, action: PayloadAction<ProcessTracker>) => {
      const tracker = action.payload
      const index = state.trackers.findIndex((t) => t.id === tracker.id)
      if (index >= 0) {
        state.trackers[index] = tracker
      } else {
        state.trackers.unshift(tracker)
      }
      if (state.tracker?.id === tracker.id) {
        state.tracker = tracker
      } else if (!state.tracker && tracker.status !== 'COMPLETED' && tracker.status !== 'FAILED') {
        state.tracker = tracker
      }
    },
    setSseStatus: (state, action: PayloadAction<SseStatus>) => {
      state.sseStatus = action.payload
    },
    clearTracker: (state) => {
      state.tracker = null
      state.error = null
    },
    fetchRecentTrackers: (state, _action: PayloadAction<number>) => {
      state.fetchingTrackers = true
    },
    fetchRecentTrackersSuccess: (state, action: PayloadAction<ProcessTracker[]>) => {
      state.trackers = action.payload
      state.fetchingTrackers = false
    },
    fetchRecentTrackersFailure: (state, action: PayloadAction<string>) => {
      state.error = action.payload
      state.fetchingTrackers = false
    },
  },
})

export const {
  uploadFiles,
  uploadSuccess,
  uploadFailure,
  fetchProcessStatus,
  updateProcessStatus,
  processEvent,
  setSseStatus,
  clearTracker,
  fetchRecentTrackers,
  fetchRecentTrackersSuccess,
  fetchRecentTrackersFailure,
} = uploadSlice.actions

export default uploadSlice.reducer
