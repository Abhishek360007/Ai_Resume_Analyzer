import { useId } from 'react'

interface BrandMarkProps {
  size?: number
  className?: string
}

/** App logo: a resume sheet with an AI spark, drawn inline so it scales crisply. */
const BrandMark = ({ size = 32, className }: BrandMarkProps) => {
  // Unique per instance: a hidden copy's gradient would otherwise blank out visible ones
  const gradientId = `brandMark${useId().replace(/:/g, '')}`
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop stopColor="#4f46e5" />
          <stop offset="1" stopColor="#7c3aed" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill={`url(#${gradientId})`} />
      <rect x="17" y="13" width="26" height="34" rx="4" fill="#fff" fillOpacity="0.95" />
      <rect x="22" y="20" width="16" height="3" rx="1.5" fill="#4f46e5" />
      <rect x="22" y="27" width="12" height="2.5" rx="1.25" fill="#a5b4fc" />
      <rect x="22" y="32.5" width="14" height="2.5" rx="1.25" fill="#a5b4fc" />
      <circle cx="44" cy="44" r="9" fill="#0b1020" />
      <path d="M44 38.5l1.6 3.9 3.9 1.6-3.9 1.6-1.6 3.9-1.6-3.9-3.9-1.6 3.9-1.6z" fill="#c7d2fe" />
    </svg>
  )
}

export default BrandMark
