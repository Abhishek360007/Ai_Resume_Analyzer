package com.abhishek.resumeanalyzer.models;

/**
 * Count of feedback per type (used in FeedbackStatistics)
 */
public record FeedbackTypeCount(
        FeedbackType feedbackType,
        long count
) {}
