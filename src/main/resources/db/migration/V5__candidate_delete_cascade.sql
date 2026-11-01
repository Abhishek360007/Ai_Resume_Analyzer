-- =====================================================================
-- Resume Analyzer - Flyway V5: cascade candidate deletion
--
-- deleteCandidate failed with INTERNAL_ERROR because resume_embeddings
-- and employees hold FK references to candidates that are not part of the
-- Candidate entity's cascaded collections (only candidate_matches and
-- candidate_external_profiles are cascade-deleted by JPA).
--
-- * resume_embeddings belong to the candidate -> ON DELETE CASCADE
-- * employees only optionally link to a candidate -> ON DELETE SET NULL
-- =====================================================================

alter table resume_embeddings
    drop constraint FKpyt883om92h115omw77vgxt1i;

alter table resume_embeddings
    add constraint FKpyt883om92h115omw77vgxt1i
        foreign key (candidate_id) references candidates
        on delete cascade;

alter table employees
    drop constraint FKqal4m9onrkpsi78cda872e16s;

alter table employees
    add constraint FKqal4m9onrkpsi78cda872e16s
        foreign key (candidate_id) references candidates
        on delete set null;
