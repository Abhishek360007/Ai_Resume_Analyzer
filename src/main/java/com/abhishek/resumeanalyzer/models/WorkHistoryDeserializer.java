package com.abhishek.resumeanalyzer.models;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lenient deserializer for the LLM-extracted {@code workHistory} field.
 *
 * <p>The LLM sometimes emits {@code null}, a bare string, or entries with
 * missing fields.  This deserializer tolerates those shapes and returns a
 * clean list (possibly empty) so the rest of the pipeline never crashes on
 * an unexpected work-history payload.</p>
 */
public class WorkHistoryDeserializer extends JsonDeserializer<List<EmploymentEntry>> {

    @Override
    public List<EmploymentEntry> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        try {
            List<EmploymentEntry> entries = mapper.readValue(
                    p, new TypeReference<List<EmploymentEntry>>() {});
            if (entries == null) {
                return new ArrayList<>();
            }
            List<EmploymentEntry> clean = new ArrayList<>();
            for (EmploymentEntry entry : entries) {
                if (entry != null && entry.getCompany() != null && !entry.getCompany().isBlank()) {
                    clean.add(entry);
                }
            }
            return clean;
        } catch (Exception e) {
            // Fallback: attempt to parse as string, otherwise empty list
            try {
                String raw = mapper.readValue(p, String.class);
                if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
                    return new ArrayList<>();
                }
            } catch (Exception ignored) {
                // ignore
            }
            return new ArrayList<>();
        }
    }
}
