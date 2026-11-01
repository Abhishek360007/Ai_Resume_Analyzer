import { configureStore } from '@reduxjs/toolkit'
import createSagaMiddleware from 'redux-saga'
import authReducer from './slices/authSlice'
import candidatesReducer from './slices/candidatesSlice'
import jobsReducer from './slices/jobsSlice'
import matchesReducer from './slices/matchesSlice'
import uploadReducer from './slices/uploadSlice'
import enrichmentReducer from './slices/enrichmentSlice'
import confirmationReducer from './slices/confirmationSlice'
import rootSaga from './sagas'

const sagaMiddleware = createSagaMiddleware()

export const store = configureStore({
  reducer: {
    auth: authReducer,
    candidates: candidatesReducer,
    jobs: jobsReducer,
    matches: matchesReducer,
    upload: uploadReducer,
    enrichment: enrichmentReducer,
    confirmation: confirmationReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({ thunk: false }).concat(sagaMiddleware),
})

sagaMiddleware.run(rootSaga)

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
