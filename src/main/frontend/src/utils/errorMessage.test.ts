import { describe, it, expect } from 'vitest'
import { getErrorMessage } from './errorMessage'

describe('getErrorMessage', () => {
  it('returns the first GraphQL error message instead of the JSON dump', () => {
    const error = Object.assign(new Error('Skill already exists: Java: {"response":{"errors":[]}}'), {
      response: { errors: [{ message: 'Skill already exists: Java' }] },
    })
    expect(getErrorMessage(error)).toBe('Skill already exists: Java')
  })

  it('hides Spring generic INTERNAL_ERROR messages behind the fallback', () => {
    const error = { response: { errors: [{ message: 'INTERNAL_ERROR for 1f2e-77' }] } }
    expect(getErrorMessage(error, 'Failed to load users')).toBe('Failed to load users')
  })

  it('returns the REST error body for axios errors', () => {
    const error = Object.assign(new Error('Request failed with status code 401'), {
      response: { data: { error: 'Invalid username or password' } },
    })
    expect(getErrorMessage(error)).toBe('Invalid username or password')
  })

  it('does not show raw axios status messages', () => {
    expect(getErrorMessage(new Error('Request failed with status code 500'), 'Upload failed')).toBe('Upload failed')
  })

  it('falls back to a plain error message, then to the fallback', () => {
    expect(getErrorMessage(new Error('Network Error'))).toBe('Network Error')
    expect(getErrorMessage(undefined, 'Failed')).toBe('Failed')
    expect(getErrorMessage('oops', 'Failed')).toBe('Failed')
  })
})
