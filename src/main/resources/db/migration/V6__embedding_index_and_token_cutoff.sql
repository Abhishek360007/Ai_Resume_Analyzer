-- RAG retrieval filters resume chunks by candidate (WHERE candidate_id = ? ORDER BY embedding <=> ?);
-- index it so a search reads one candidate's chunks instead of scanning the table.
CREATE INDEX IF NOT EXISTS idx_resume_embeddings_candidate ON resume_embeddings (candidate_id);

-- Tokens issued before this moment are rejected. Set on logout, password change or reset,
-- and deactivation, so those sign every session of the user out (JWTs can't be revoked otherwise).
ALTER TABLE users ADD COLUMN IF NOT EXISTS tokens_valid_after timestamp(6) with time zone;
