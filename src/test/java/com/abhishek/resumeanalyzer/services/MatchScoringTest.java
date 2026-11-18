package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.models.CandidateMatchResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScoringTest {

    @Test
    void overallScoreIsTheWeightedAverageNotTheModelsArithmetic() {
        CandidateMatchResponse response = CandidateMatchResponse.builder()
                .matchScore(92.0) // what the model said
                .skillsScore(95.0).experienceScore(90.0).educationScore(100.0).domainScore(85.0)
                .build();

        MatchScoring.apply(response);

        // 0.40*95 + 0.25*90 + 0.20*100 + 0.15*85 = 93.25
        assertThat(response.getMatchScore()).isEqualTo(93.3);
    }

    @Test
    void outOfRangeScoresAreClamped() {
        CandidateMatchResponse response = CandidateMatchResponse.builder()
                .skillsScore(140.0).experienceScore(-20.0).educationScore(100.0).domainScore(100.0)
                .build();

        MatchScoring.apply(response);

        assertThat(response.getSkillsScore()).isEqualTo(100.0);
        assertThat(response.getExperienceScore()).isEqualTo(0.0);
        assertThat(response.getMatchScore()).isEqualTo(75.0); // 40 + 0 + 20 + 15
    }

    @Test
    void keepsTheModelsOverallScoreWhenASubScoreIsMissing() {
        CandidateMatchResponse response = CandidateMatchResponse.builder()
                .matchScore(120.0).skillsScore(80.0).build();

        MatchScoring.apply(response);

        assertThat(response.getMatchScore()).isEqualTo(100.0);
    }

    @Test
    void shortlistsFromSeventy() {
        assertThat(MatchScoring.shortlisted(70.0)).isTrue();
        assertThat(MatchScoring.shortlisted(69.9)).isFalse();
        assertThat(MatchScoring.shortlisted(null)).isFalse();
    }
}
