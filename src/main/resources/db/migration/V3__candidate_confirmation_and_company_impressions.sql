-- =====================================================================
-- Resume Analyzer - Flyway V3: candidate confirmation + company impressions
--
-- 1. Adds lifecycle status + work history + social profile URL columns to
--    candidates so AI-extracted details can be confirmed by HR before a
--    candidate becomes eligible for matching.
-- 2. Backfills all pre-existing candidates to ACTIVE.
-- 3. Creates the global company_impressions table (keyed by company name,
--    reused across candidates).
-- =====================================================================

-- ---------------------------------------------------------------------
-- candidates: new confirmation-related columns
-- ---------------------------------------------------------------------
alter table candidates
    add column status varchar(20);

alter table candidates
    add column work_history TEXT;

alter table candidates
    add column linkedin_url varchar(512);

alter table candidates
    add column github_url varchar(512);

alter table candidates
    add column twitter_url varchar(512);

-- Backfill: any candidate created before confirmation flows existed is active.
update candidates set status = 'ACTIVE' where status is null;

-- Enforce a not-null with default for future inserts.
alter table candidates
    alter column status set default 'ACTIVE';

alter table candidates
    alter column status set not null;

-- ---------------------------------------------------------------------
-- company_impressions: global, cached company due-diligence impressions
-- ---------------------------------------------------------------------
create table company_impressions (
    id uuid not null,
    company_name varchar(512) not null,
    industry varchar(255),
    verdict varchar(20) not null check (verdict in ('GENUINE','SUSPICIOUS','UNKNOWN')),
    summary TEXT,
    confidence_score double precision,
    evidence TEXT,
    created_at timestamp(6),
    updated_at timestamp(6),
    primary key (id),
    constraint uk_company_impressions_name unique (company_name)
);
