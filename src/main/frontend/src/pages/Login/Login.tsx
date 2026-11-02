/**
 * Login Page Component
 * Handles user authentication with username/password
 */

import { useState, useEffect, FormEvent } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, useLocation } from 'react-router-dom'
import { loginRequest, clearError } from '@store/slices/authSlice'
import {
  selectIsAuthenticated,
  selectIsLoading,
  selectAuthError,
} from '@store/selectors/authSelectors'
import { SESSION_EXPIRED_MESSAGE } from '@/utils/tokenUtils'
import BrandMark from '@/components/BrandMark/BrandMark'
import { Eye, EyeOff, FileText, Sparkles, Target } from 'lucide-react'
import styles from './Login.module.css'

const HIGHLIGHTS = [
  {
    icon: Sparkles,
    title: 'AI resume parsing',
    text: 'Skills, experience and education extracted from PDF, Word or text in seconds.',
  },
  {
    icon: Target,
    title: 'Explainable match scores',
    text: 'Every candidate ranked against your job with clear strengths and gaps.',
  },
  {
    icon: FileText,
    title: 'Shareable reports',
    text: 'Export polished PDF match reports for hiring managers.',
  },
]

interface LocationState {
  from?: {
    pathname: string
  }
}

export default function Login() {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState

  const isAuthenticated = useSelector(selectIsAuthenticated)
  const isLoading = useSelector(selectIsLoading)
  const error = useSelector(selectAuthError)

  // When the user was redirected here because their session expired, show a
  // friendly notice instead of a raw 401 error.
  const sessionExpired =
    new URLSearchParams(location.search).get('session') === 'expired'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  // Redirect to intended destination after successful login
  useEffect(() => {
    if (isAuthenticated) {
      const from = state?.from?.pathname || '/'
      navigate(from, { replace: true })
    }
  }, [isAuthenticated, navigate, state])

  // Clear error when component unmounts
  useEffect(() => {
    return () => {
      dispatch(clearError())
    }
  }, [dispatch])

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()

    if (!username.trim() || !password.trim()) {
      return
    }

    dispatch(loginRequest({ username, password }))
  }

  return (
    <div className={styles.loginContainer}>
      <section className={styles.showcase} aria-hidden="true">
        <div className={styles.showcaseInner}>
          <div className={styles.showcaseBrand}>
            <BrandMark size={40} />
            <span>AI Resume Analyzer</span>
          </div>
          <h2 className={styles.showcaseTitle}>
            Hire smarter with <span>AI‑powered</span> resume intelligence.
          </h2>
          <p className={styles.showcaseLead}>
            Turn a stack of resumes into a ranked, explainable shortlist, in minutes instead of days.
          </p>
          <ul className={styles.highlights}>
            {HIGHLIGHTS.map(({ icon: Icon, title, text }) => (
              <li key={title}>
                <span className={styles.highlightIcon}><Icon size={18} /></span>
                <div>
                  <strong>{title}</strong>
                  <p>{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className={styles.showcaseFooter}>Built by Abhishek Raj © 2026</p>
      </section>

      <section className={styles.formPanel}>
        <div className={styles.loginBox}>
          <div className={styles.loginHeader}>
            <BrandMark size={44} className={styles.mobileMark} />
            <h1>Welcome back</h1>
            <p>Sign in to AI Resume Analyzer</p>
          </div>

          <form onSubmit={handleSubmit} className={styles.loginForm}>
            {sessionExpired && !error && (
              <div className={styles.sessionMessage} role="alert">
                {SESSION_EXPIRED_MESSAGE}
              </div>
            )}
            {error && (
              <div className={styles.errorMessage} role="alert">
                {error}
              </div>
            )}

            <div className={styles.formGroup}>
              <label htmlFor="username">Username</label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter your username"
                disabled={isLoading}
                required
                autoComplete="username"
                autoFocus
              />
            </div>

            <div className={styles.formGroup}>
              <label htmlFor="password">Password</label>
              <div className={styles.passwordField}>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  disabled={isLoading}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className={styles.revealButton}
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  title={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className={styles.loginButton}
              disabled={isLoading || !username.trim() || !password.trim()}
            >
              {isLoading && <span className={styles.spinner} aria-hidden="true" />}
              {isLoading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <p className={styles.loginFooter}>
            Accounts are created by your administrator.
          </p>
        </div>
      </section>
    </div>
  )
}
