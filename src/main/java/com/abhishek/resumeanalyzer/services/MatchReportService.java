package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.Candidate;
import com.abhishek.resumeanalyzer.entities.CandidateMatch;
import com.abhishek.resumeanalyzer.entities.JobRequirement;
import com.abhishek.resumeanalyzer.repos.CandidateMatchRepository;
import com.abhishek.resumeanalyzer.repos.JobRequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for exporting candidate match results of a job requirement as a PDF report.
 * The report contains the job details, summary statistics, a ranked score table
 * and the AI explanation for every candidate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchReportService {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 50;
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;
    private static final Color BRAND = new Color(37, 99, 235);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color ROW_SHADE = new Color(241, 245, 249);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // Column layout of the ranking table: #, Candidate, Overall, Skills, Exp, Edu, Domain, Status
    private static final String[] COLUMN_HEADERS = {"#", "Candidate", "Overall", "Skills", "Exp.", "Edu.", "Domain", "Status"};
    private static final float[] COLUMN_WIDTHS = {22, 163, 50, 45, 40, 40, 45, 90};

    private final CandidateMatchRepository candidateMatchRepository;
    private final JobRequirementRepository jobRequirementRepository;

    /**
     * Generate a PDF report of all candidate matches for a job requirement.
     *
     * @param jobRequirementId Job requirement UUID
     * @return PDF document bytes
     * @throws IllegalArgumentException if the job requirement does not exist
     */
    @Transactional(readOnly = true)
    public byte[] generateJobMatchReport(UUID jobRequirementId) {
        JobRequirement job = jobRequirementRepository.findById(jobRequirementId)
                .orElseThrow(() -> new IllegalArgumentException("Job requirement not found: " + jobRequirementId));
        List<CandidateMatch> matches = candidateMatchRepository.findByJobRequirementIdOrderByMatchScoreDesc(jobRequirementId);

        log.info("Generating match report for job '{}' with {} matches", job.getTitle(), matches.size());

        try (PDDocument document = new PDDocument()) {
            ReportWriter writer = new ReportWriter(document);
            writeHeader(writer, job);
            writeSummary(writer, matches);
            if (matches.isEmpty()) {
                writer.paragraph("No candidates have been matched to this job yet.", writer.regular, 11, MUTED);
            } else {
                writeRankingTable(writer, matches);
                writeExplanations(writer, matches);
            }
            writer.close();
            writer.addFooters();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate match report", e);
        }
    }

    private void writeHeader(ReportWriter w, JobRequirement job) throws IOException {
        w.paragraph("Candidate Match Report", w.bold, 20, BRAND);
        w.paragraph(job.getTitle(), w.bold, 14, Color.BLACK);
        w.paragraph("Generated " + LocalDateTime.now().format(DATE_FORMAT), w.regular, 9, MUTED);
        w.space(8);

        w.labelValue("Experience", formatExperience(job));
        w.labelValue("Required skills", job.getRequiredSkills());
        w.labelValue("Education", job.getRequiredEducation());
        w.labelValue("Domain", job.getDomainRequirements());
        w.labelValue("Location", job.getLocation());
        w.space(10);
    }

    private void writeSummary(ReportWriter w, List<CandidateMatch> matches) throws IOException {
        double average = matches.stream().map(CandidateMatch::getMatchScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        double top = matches.stream().map(CandidateMatch::getMatchScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0);
        long shortlisted = matches.stream().filter(m -> Boolean.TRUE.equals(m.getIsShortlisted())).count();
        long selected = matches.stream().filter(m -> Boolean.TRUE.equals(m.getIsSelected())).count();

        w.paragraph("Summary", w.bold, 13, BRAND);
        w.paragraph(String.format("Candidates matched: %d  |  Average score: %.0f%%  |  Top score: %.0f%%  |  "
                        + "Shortlisted: %d  |  Selected: %d",
                matches.size(), average, top, shortlisted, selected), w.regular, 10, Color.BLACK);
        w.space(12);
    }

    private void writeRankingTable(ReportWriter w, List<CandidateMatch> matches) throws IOException {
        w.paragraph("Ranking", w.bold, 13, BRAND);
        w.space(6);
        w.tableRow(COLUMN_HEADERS, w.bold, BRAND, Color.WHITE);
        for (int i = 0; i < matches.size(); i++) {
            CandidateMatch m = matches.get(i);
            String[] cells = {
                    String.valueOf(i + 1),
                    candidateName(m.getCandidate()),
                    formatScore(m.getMatchScore()),
                    formatScore(m.getSkillsScore()),
                    formatScore(m.getExperienceScore()),
                    formatScore(m.getEducationScore()),
                    formatScore(m.getDomainScore()),
                    statusLabel(m)
            };
            w.tableRow(cells, w.regular, i % 2 == 1 ? ROW_SHADE : null, Color.BLACK);
        }
        w.space(14);
    }

    private void writeExplanations(ReportWriter w, List<CandidateMatch> matches) throws IOException {
        w.paragraph("AI Analysis", w.bold, 13, BRAND);
        for (int i = 0; i < matches.size(); i++) {
            CandidateMatch m = matches.get(i);
            Candidate c = m.getCandidate();
            w.paragraph((i + 1) + ". " + candidateName(c) + "  -  " + formatScore(m.getMatchScore()),
                    w.bold, 11, Color.BLACK);
            if (c != null && c.getEmail() != null) {
                w.paragraph(c.getEmail(), w.regular, 9, MUTED);
            }
            String explanation = m.getMatchExplanation();
            w.paragraph(explanation == null || explanation.isBlank() ? "No explanation provided." : explanation,
                    w.regular, 10, Color.BLACK);
            if (m.getRecruiterNotes() != null && !m.getRecruiterNotes().isBlank()) {
                w.paragraph("Recruiter notes: " + m.getRecruiterNotes(), w.regular, 10, MUTED);
            }
            w.space(8);
        }
    }

    private static String formatExperience(JobRequirement job) {
        Integer min = job.getMinExperienceYears();
        Integer max = job.getMaxExperienceYears();
        if (min == null && max == null) return null;
        return (min != null ? min : 0) + " - " + (max != null ? max + " years" : "+ years");
    }

    private static String formatScore(Double score) {
        return score == null ? "-" : String.format("%.0f%%", score);
    }

    private static String candidateName(Candidate candidate) {
        return candidate == null || candidate.getName() == null ? "Unknown Candidate" : candidate.getName();
    }

    private static String statusLabel(CandidateMatch m) {
        if (Boolean.TRUE.equals(m.getIsSelected())) return "Selected";
        if (Boolean.TRUE.equals(m.getIsShortlisted())) return "Shortlisted";
        return "-";
    }

    /**
     * Minimal top-to-bottom PDF layout helper that handles text wrapping and page breaks.
     */
    private static class ReportWriter {

        private final PDDocument document;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        ReportWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN + 20) {
                newPage();
            }
        }

        void space(float height) {
            y -= height;
        }

        void paragraph(String text, PDFont font, float size, Color color) throws IOException {
            float leading = size * 1.35f;
            for (String line : wrap(sanitize(text, font), font, size, CONTENT_WIDTH)) {
                ensureSpace(leading);
                y -= leading;
                drawText(line, font, size, color, MARGIN, y);
            }
        }

        void labelValue(String label, String value) throws IOException {
            if (value == null || value.isBlank()) return;
            String labelText = label + ": ";
            float size = 10;
            float leading = size * 1.35f;
            float labelWidth = bold.getStringWidth(labelText) / 1000 * size;
            List<String> lines = wrap(sanitize(value, regular), regular, size, CONTENT_WIDTH - labelWidth);
            for (int i = 0; i < lines.size(); i++) {
                ensureSpace(leading);
                y -= leading;
                if (i == 0) {
                    drawText(labelText, bold, size, Color.BLACK, MARGIN, y);
                }
                drawText(lines.get(i), regular, size, Color.BLACK, MARGIN + labelWidth, y);
            }
        }

        void tableRow(String[] cells, PDFont font, Color background, Color textColor) throws IOException {
            float size = 9;
            float rowHeight = 16;
            ensureSpace(rowHeight);
            y -= rowHeight;
            if (background != null) {
                stream.setNonStrokingColor(background);
                stream.addRect(MARGIN, y, CONTENT_WIDTH, rowHeight);
                stream.fill();
            }
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                String cell = truncate(sanitize(cells[i], font), font, size, COLUMN_WIDTHS[i] - 6);
                drawText(cell, font, size, textColor, x + 3, y + 5);
                x += COLUMN_WIDTHS[i];
            }
        }

        void addFooters() throws IOException {
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                    String text = "AI Resume Analyzer  |  Page " + (i + 1) + " of " + total;
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(MARGIN, MARGIN / 2);
                    footer.showText(text);
                    footer.endText();
                }
            }
        }

        void close() throws IOException {
            stream.close();
        }

        private void drawText(String text, PDFont font, float size, Color color, float x, float baseline)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(text);
            stream.endText();
        }

        /**
         * Replace characters the standard Type 1 fonts cannot encode (emoji, non-Latin
         * scripts, tabs) so that AI-generated text never breaks report generation.
         */
        private static String sanitize(String text, PDFont font) {
            if (text == null) return "";
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (cp == '\n') {
                    sb.append('\n');
                    return;
                }
                if (Character.isWhitespace(cp)) {
                    sb.append(' ');
                    return;
                }
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    sb.append(ch);
                } catch (IOException | IllegalArgumentException e) {
                    sb.append('?');
                }
            });
            return sb.toString();
        }

        private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : text.split("\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.trim().split(" +")) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (width(candidate, font, size) <= maxWidth) {
                        line.setLength(0);
                        line.append(candidate);
                    } else {
                        if (!line.isEmpty()) {
                            lines.add(line.toString());
                        }
                        line.setLength(0);
                        line.append(truncate(word, font, size, maxWidth));
                    }
                }
                lines.add(line.toString());
            }
            return lines;
        }

        private static String truncate(String text, PDFont font, float size, float maxWidth) throws IOException {
            if (width(text, font, size) <= maxWidth) return text;
            String result = text;
            while (!result.isEmpty() && width(result + "...", font, size) > maxWidth) {
                result = result.substring(0, result.length() - 1);
            }
            return result + "...";
        }

        private static float width(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }
    }
}
