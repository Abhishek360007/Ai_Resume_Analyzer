-- =====================================================================
-- Resume Analyzer - Flyway V4: store candidate resume binary as bytea
--
-- Historically resume_file was a PG large-object OID bound with @Lob on
-- the entity. Large objects can only be read while a transaction is open
-- (auto-commit reads are rejected by PostgreSQL), which broke derived
-- repository queries such as findByStatus that do not run inside a
-- transaction. This mirrors the JobQueue.file_data fix (commit 385a225):
-- store the bytes inline as bytea instead of a large-object OID.
-- =====================================================================

-- Capture the large-object OIDs before the column is rewritten, so the
-- now-orphaned objects can be unlinked afterwards.
create temporary table _candidate_lo_oids as
    select resume_file as lo_oid
    from candidates
    where resume_file is not null;

-- Rewrite the OID column in place, materializing each large object's bytes.
alter table candidates
    alter column resume_file type bytea using lo_get(resume_file);

-- Drop the now-orphaned large objects from pg_largeobject.
select lo_unlink(lo_oid)
from _candidate_lo_oids;
