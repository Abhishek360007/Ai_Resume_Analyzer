package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.models.CandidateMatchResponse;

/**
 * Turns the AI's four sub-scores into the overall match score, in code.
 *
 * <p>The prompt asks the model for skills, experience, education and domain scores and
 * their weighted average, but language models are unreliable at arithmetic and don't
 * always respect the weights (a model can return sub-scores of 95/90/100/85 and an
 * overall 92, when the weighted average is 93.25). The overall score decides who is
 * shortlisted, so it is recalculated here: the model judges, the code counts.</p>
 */
public final class MatchScoring {

    public static final double SKILLS_WEIGHT = 0.40;
    public static final double EXPERIENCE_WEIGHT = 0.25;
    public static final double EDUCATION_WEIGHT = 0.20;
    public static final double DOMAIN_WEIGHT = 0.15;

    /** Candidates scoring at least this are shortlisted automatically. */
    public static final double SHORTLIST_THRESHOLD = 70.0;

    private MatchScoring() {
    }

    /**
     * Clamps every score to 0-100 and, when all four sub-scores are present, replaces the
     * overall score with their weighted average (one decimal). If a sub-score is missing,
     * the model's overall score is kept (clamped).
     */
    public static void apply(CandidateMatchResponse response) {
        response.setSkillsScore(clamp(response.getSkillsScore()));
        response.setExperienceScore(clamp(response.getExperienceScore()));
        response.setEducationScore(clamp(response.getEducationScore()));
        response.setDomainScore(clamp(response.getDomainScore()));

        if (response.getSkillsScore() != null && response.getExperienceScore() != null
                && response.getEducationScore() != null && response.getDomainScore() != null) {
            double weighted = SKILLS_WEIGHT * response.getSkillsScore()
                    + EXPERIENCE_WEIGHT * response.getExperienceScore()
                    + EDUCATION_WEIGHT * response.getEducationScore()
                    + DOMAIN_WEIGHT * response.getDomainScore();
            response.setMatchScore(Math.round(weighted * 10) / 10.0);
        } else {
            response.setMatchScore(clamp(response.getMatchScore()));
        }
    }

    public static boolean shortlisted(Double matchScore) {
        return matchScore != null && matchScore >= SHORTLIST_THRESHOLD;
    }

    private static Double clamp(Double score) {
        if (score == null || score.isNaN()) {
            return null;
        }
        return Math.max(0.0, Math.min(100.0, score));
    }
}
