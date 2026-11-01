import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import ScoreInsights from './ScoreInsights'
import { CandidateMatch } from '@/store/slices/matchesSlice'

const makeMatch = (id: string, overall: number, dims: [number, number, number, number]): CandidateMatch => ({
  id,
  candidateId: `cand-${id}`,
  jobRequirementId: 'job-1',
  matchScore: overall,
  skillsScore: dims[0],
  experienceScore: dims[1],
  educationScore: dims[2],
  domainScore: dims[3],
  isShortlisted: false,
  isSelected: false,
  createdAt: '2026-01-01T00:00:00Z',
})

describe('ScoreInsights', () => {
  const matches = [
    makeMatch('1', 92, [95, 90, 80, 70]),
    makeMatch('2', 74, [70, 80, 40, 60]),
    makeMatch('3', 55, [60, 50, 30, 50]),
    makeMatch('4', 31, [20, 30, 10, 40]),
  ]

  it('renders nothing when there are no matches', () => {
    const { container } = render(<ScoreInsights matches={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('describes the radar chart for assistive technology', () => {
    render(<ScoreInsights matches={matches} topCandidateName="Priya Sharma" />)
    const chart = screen.getByRole('img')
    // Pool skills average = (95 + 70 + 60 + 20) / 4 = 61.25
    expect(chart).toHaveAccessibleName(/Pool average vs Priya Sharma: Skills fit 61% vs 95%/)
    expect(chart).toHaveAccessibleName(/Education fit 40% vs 80%/)
  })

  it('shows the top candidate in the legend', () => {
    render(<ScoreInsights matches={matches} topCandidateName="Priya Sharma" />)
    expect(screen.getByText(/Priya Sharma \(92%\)/)).toBeInTheDocument()
  })

  it('counts candidates per score band', () => {
    render(<ScoreInsights matches={matches} />)
    for (const label of ['Excellent (80+)', 'Good (70-79)', 'Average (50-69)', 'Poor (<50)']) {
      const row = screen.getByText(label).closest('li')!
      expect(within(row).getByText('1')).toBeInTheDocument()
    }
  })

  it('calls out the weakest dimension of the pool', () => {
    render(<ScoreInsights matches={matches} />)
    expect(screen.getByText(/weakest on/)).toHaveTextContent('weakest on education fit (average 40%)')
  })
})
