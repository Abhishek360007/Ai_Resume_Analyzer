import { CandidateMatch } from '@/store/slices/matchesSlice'
import styles from './ScoreInsights.module.css'

interface ScoreInsightsProps {
  matches: CandidateMatch[]
  topCandidateName?: string
}

type Dimension = 'skillsScore' | 'experienceScore' | 'educationScore' | 'domainScore'

const DIMENSIONS: { key: Dimension; label: string }[] = [
  { key: 'skillsScore', label: 'Skills fit' },
  { key: 'experienceScore', label: 'Experience fit' },
  { key: 'educationScore', label: 'Education fit' },
  { key: 'domainScore', label: 'Domain fit' },
]

const BANDS = [
  { label: 'Excellent (80+)', min: 80, max: 101, className: 'excellent' },
  { label: 'Good (70-79)', min: 70, max: 80, className: 'good' },
  { label: 'Average (50-69)', min: 50, max: 70, className: 'average' },
  { label: 'Poor (<50)', min: 0, max: 50, className: 'poor' },
] as const

const SIZE = 280
const CENTER = SIZE / 2
const RADIUS = 90
const GRID_LEVELS = [25, 50, 75, 100]
// Extra horizontal room so side axis labels fit inside the SVG
const LABEL_GUTTER = 40

const average = (values: number[]) =>
  values.length === 0 ? 0 : values.reduce((sum, v) => sum + v, 0) / values.length

// Axes start at 12 o'clock and go clockwise
const pointAt = (index: number, value: number, radius = RADIUS) => {
  const angle = (Math.PI * 2 * index) / DIMENSIONS.length - Math.PI / 2
  const r = (Math.max(0, Math.min(100, value)) / 100) * radius
  return { x: CENTER + r * Math.cos(angle), y: CENTER + r * Math.sin(angle) }
}

const polygon = (values: number[]) =>
  values
    .map((v, i) => {
      const { x, y } = pointAt(i, v)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')

/**
 * Job-level overview of a match run: a radar chart comparing the candidate
 * pool's average against the top candidate on each scoring dimension, and the
 * distribution of overall scores across quality bands.
 */
const ScoreInsights = ({ matches, topCandidateName }: ScoreInsightsProps) => {
  if (matches.length === 0) return null

  const top = matches.reduce((best, m) => (m.matchScore > best.matchScore ? m : best), matches[0])
  const poolAverages = DIMENSIONS.map((d) => average(matches.map((m) => m[d.key] ?? 0)))
  const topScores = DIMENSIONS.map((d) => top[d.key] ?? 0)
  const weakest = DIMENSIONS[poolAverages.indexOf(Math.min(...poolAverages))]
  const bandCounts = BANDS.map((band) => ({
    ...band,
    count: matches.filter((m) => m.matchScore >= band.min && m.matchScore < band.max).length,
  }))
  const topName = topCandidateName || 'Top candidate'

  const chartSummary =
    `Pool average vs ${topName}: ` +
    DIMENSIONS.map(
      (d, i) => `${d.label} ${Math.round(poolAverages[i])}% vs ${Math.round(topScores[i])}%`
    ).join(', ')

  return (
    <section className={styles.insights} aria-labelledby="score-insights-title">
      <h3 id="score-insights-title">📊 Score Insights</h3>

      <div className={styles.grid}>
        <div className={styles.panel}>
          <h4>Score breakdown</h4>
          <svg
            viewBox={`${-LABEL_GUTTER} 0 ${SIZE + 2 * LABEL_GUTTER} ${SIZE}`}
            className={styles.radar}
            role="img"
            aria-label={chartSummary}
          >
            {GRID_LEVELS.map((level) => (
              <polygon
                key={level}
                points={polygon(DIMENSIONS.map(() => level))}
                className={styles.gridLine}
              />
            ))}
            {DIMENSIONS.map((d, i) => {
              const end = pointAt(i, 100)
              const label = pointAt(i, 100, RADIUS + 14)
              // Anchor side labels away from the chart so they never overlap a vertex
              const anchor = label.x > CENTER + 1 ? 'start' : label.x < CENTER - 1 ? 'end' : 'middle'
              return (
                <g key={d.key}>
                  <line x1={CENTER} y1={CENTER} x2={end.x} y2={end.y} className={styles.gridLine} />
                  <text x={label.x} y={label.y} className={styles.axisLabel} textAnchor={anchor} dominantBaseline="middle">
                    {d.label}
                  </text>
                </g>
              )
            })}
            <polygon points={polygon(poolAverages)} className={styles.poolShape} />
            <polygon points={polygon(topScores)} className={styles.topShape} />
            {topScores.map((v, i) => {
              const { x, y } = pointAt(i, v)
              return (
                <circle key={i} cx={x} cy={y} r={3.5} className={styles.topDot}>
                  <title>{`${topName} — ${DIMENSIONS[i].label}: ${Math.round(v)}%`}</title>
                </circle>
              )
            })}
          </svg>
          <ul className={styles.legend}>
            <li>
              <span className={`${styles.swatch} ${styles.topSwatch}`} /> {topName} ({Math.round(top.matchScore)}%)
            </li>
            <li>
              <span className={`${styles.swatch} ${styles.poolSwatch}`} /> Pool average
            </li>
          </ul>
        </div>

        <div className={styles.panel}>
          <h4>Score distribution</h4>
          <ul className={styles.bands}>
            {bandCounts.map((band) => (
              <li key={band.label} className={styles.band}>
                <span className={styles.bandLabel}>{band.label}</span>
                <span className={styles.bandTrack}>
                  <span
                    className={`${styles.bandFill} ${styles[band.className]}`}
                    style={{ width: `${(band.count / matches.length) * 100}%` }}
                  />
                </span>
                <span className={styles.bandCount}>{band.count}</span>
              </li>
            ))}
          </ul>
          <p className={styles.callout}>
            The candidate pool is weakest on <strong>{weakest.label.toLowerCase()}</strong>{' '}
            (average {Math.round(Math.min(...poolAverages))}%).
          </p>
        </div>
      </div>
    </section>
  )
}

export default ScoreInsights
