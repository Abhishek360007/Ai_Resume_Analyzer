-- =====================================================================
-- Resume Analyzer - Flyway V1: initial schema
-- Generated from the JPA/Hibernate 6.4 entity model (ddl-auto no longer
-- creates schema; Flyway owns it from here on).
-- =====================================================================

-- pgvector extension for semantic resume embeddings (768-dim)
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------

create table audit_log (
    id uuid not null,
    action varchar(50) not null,
    created_at timestamp(6) not null,
    details TEXT,
    entity_id uuid,
    entity_type varchar(50),
    error_message TEXT,
    ip_address varchar(45),
    success boolean not null,
    user_agent TEXT,
    username varchar(50),
    user_id uuid,
    primary key (id)
);

create table candidate_external_profiles (
    id uuid not null,
    bio TEXT,
    company varchar(255),
    created_at timestamp(6),
    display_name varchar(255),
    enriched_summary TEXT,
    error_message varchar(255),
    followers integer,
    last_fetched_at timestamp(6),
    location varchar(255),
    profile_url varchar(255),
    public_repos integer,
    repositories TEXT,
    source varchar(255) not null check (source in ('GITHUB','LINKEDIN','TWITTER','INTERNET_SEARCH')),
    status varchar(255),
    updated_at timestamp(6),
    candidate_id uuid not null,
    primary key (id),
    unique (candidate_id, source)
);

create table candidate_matches (
    id uuid not null,
    created_at timestamp(6),
    domain_score float(53),
    education_score float(53),
    experience_score float(53),
    is_selected boolean,
    is_shortlisted boolean,
    match_explanation TEXT,
    match_score float(53),
    recruiter_notes TEXT,
    skills_score float(53),
    candidate_id uuid not null,
    job_requirement_id uuid not null,
    primary key (id)
);

create table candidates (
    id uuid not null,
    academic_background TEXT,
    created_at timestamp(6),
    current_company varchar(255),
    domain_knowledge TEXT,
    education varchar(255),
    email varchar(255),
    experience integer,
    experience_summary TEXT,
    mobile varchar(255),
    name varchar(255) not null,
    resume_content TEXT,
    resume_file oid,
    resume_filename varchar(255),
    skills TEXT,
    updated_at timestamp(6),
    years_of_experience integer,
    primary key (id)
);

create table employees (
    id uuid not null,
    created_at timestamp(6) not null,
    department varchar(100),
    email varchar(255) not null unique,
    employee_id varchar(50) not null unique,
    employment_type varchar(20) not null check (employment_type in ('FULL_TIME','PART_TIME','CONTRACT','INTERN')),
    first_name varchar(100) not null,
    hire_date date not null,
    last_name varchar(100) not null,
    notes TEXT,
    phone varchar(20),
    position varchar(100),
    salary numeric(12,2),
    status varchar(20) not null check (status in ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    termination_date date,
    updated_at timestamp(6),
    candidate_id uuid,
    created_by uuid,
    manager_id uuid,
    primary key (id)
);

create table feedback (
    id uuid not null,
    comments TEXT,
    created_at timestamp(6) not null,
    entity_id uuid not null,
    entity_type varchar(20) not null check (entity_type in ('CANDIDATE','JOB_REQUIREMENT')),
    feedback_type varchar(20) not null check (feedback_type in ('SHORTLIST','REJECT','INTERVIEW','OFFER','GENERAL','TECHNICAL','CULTURAL_FIT')),
    is_visible boolean not null,
    rating integer,
    updated_at timestamp(6),
    provided_by uuid not null,
    primary key (id)
);

create table job_queue (
    id uuid not null,
    assigned_to varchar(100),
    completed_at timestamp(6),
    correlation_id varchar(255),
    created_at timestamp(6) not null,
    error_message TEXT,
    error_stack_trace TEXT,
    file_data BYTEA,
    filename varchar(500),
    heartbeat_at timestamp(6),
    job_type varchar(50) not null check (job_type in ('RESUME_PROCESSING','BATCH_EMBEDDING','DATA_MIGRATION')),
    max_retries integer,
    metadata jsonb,
    priority integer not null,
    retry_count integer,
    scheduled_for timestamp(6),
    started_at timestamp(6),
    status varchar(20) not null check (status in ('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED')),
    updated_at timestamp(6),
    version bigint,
    primary key (id)
);

create table job_requirement_skills (
    job_requirement_id uuid not null,
    skill_id uuid not null,
    primary key (job_requirement_id, skill_id)
);

create table job_requirements (
    id uuid not null,
    created_at timestamp(6),
    description TEXT,
    domain TEXT,
    domain_requirements TEXT,
    is_active boolean,
    location varchar(255),
    max_experience integer,
    max_experience_years integer,
    min_experience integer,
    min_experience_years integer,
    required_education TEXT,
    required_skills TEXT,
    title varchar(255) not null,
    updated_at timestamp(6),
    primary key (id)
);

create table match_audits (
    id uuid not null,
    average_match_score float(53),
    completed_at timestamp(6),
    created_at timestamp(6),
    duration_ms bigint,
    error_message varchar(255),
    estimated_tokens_used integer,
    highest_match_score float(53),
    initiated_at timestamp(6),
    initiated_by varchar(255),
    job_requirement_id uuid,
    job_title varchar(255),
    match_summaries TEXT,
    shortlisted_count integer,
    status varchar(255) not null,
    successful_matches integer,
    total_candidates integer,
    primary key (id)
);

create table process_tracker (
    id uuid not null,
    completed_at timestamp(6),
    correlation_id varchar(255),
    created_at timestamp(6),
    failed_files integer,
    job_id uuid,
    message TEXT,
    processed_files integer,
    status varchar(255) not null check (status in ('INITIATED','EMBED_GENERATED','VECTOR_DB_UPDATED','RESUME_ANALYZED','COMPLETED','FAILED')),
    total_files integer,
    updated_at timestamp(6),
    uploaded_filename varchar(255),
    primary key (id)
);

create table resume_embeddings (
    id uuid not null,
    content_chunk TEXT,
    created_at timestamp(6),
    embedding vector(768),
    section_type varchar(255),
    candidate_id uuid not null,
    primary key (id)
);

create table skills (
    id uuid not null,
    category varchar(255),
    created_at timestamp(6) with time zone not null,
    description TEXT,
    is_active boolean,
    name varchar(255) not null unique,
    updated_at timestamp(6) with time zone not null,
    primary key (id)
);

create table system_health (
    id uuid not null,
    details jsonb,
    failure_count integer not null,
    last_checked_at timestamp(6),
    last_failure_at timestamp(6),
    last_success_at timestamp(6),
    message TEXT,
    response_time_ms integer,
    service_name varchar(50) not null unique,
    status varchar(20) not null check (status in ('UP','DOWN','DEGRADED','UNKNOWN')),
    primary key (id)
);

create table users (
    id uuid not null,
    created_at timestamp(6) not null,
    email varchar(255) not null unique,
    first_name varchar(100),
    is_active boolean not null,
    last_login_at timestamp(6),
    last_name varchar(100),
    password_hash varchar(255) not null,
    role varchar(20) not null check (role in ('ADMIN','RECRUITER','HR','HIRING_MANAGER')),
    updated_at timestamp(6),
    username varchar(50) not null unique,
    created_by uuid,
    primary key (id)
);

-- ---------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------

create index idx_audit_log_user_id on audit_log (user_id);
create index idx_audit_log_action on audit_log (action);
create index idx_audit_log_created_at on audit_log (created_at);
create index idx_feedback_entity on feedback (entity_type, entity_id);
create index idx_feedback_provided_by on feedback (provided_by);
create index idx_job_queue_status_priority on job_queue (status, priority desc, created_at asc);
create index idx_job_queue_scheduled_for on job_queue (scheduled_for);
create index idx_job_queue_assigned_to on job_queue (assigned_to);
create index idx_job_queue_correlation_id on job_queue (correlation_id);
create index idx_job_queue_created_at on job_queue (created_at desc);

-- ---------------------------------------------------------------------
-- Foreign keys
-- ---------------------------------------------------------------------

alter table audit_log add constraint FKk4alalwu62gj4tfbgfefll3tu foreign key (user_id) references users;
alter table candidate_external_profiles add constraint FK9qi86qpsn8an2bu0ht865kdyw foreign key (candidate_id) references candidates;
alter table candidate_matches add constraint FKoqo0npyvj8omo5183fu4s5nns foreign key (candidate_id) references candidates;
alter table candidate_matches add constraint FKi5w8lmihw6iuuagngh6840qgp foreign key (job_requirement_id) references job_requirements;
alter table employees add constraint FKqal4m9onrkpsi78cda872e16s foreign key (candidate_id) references candidates;
alter table employees add constraint FKf5a2r351hvnkfxbrm60givndb foreign key (created_by) references users;
alter table employees add constraint FKi4365uo9af35g7jtbc2rteukt foreign key (manager_id) references employees;
alter table feedback add constraint FK8fu7qkw6f54rviejrvdkdko3n foreign key (provided_by) references users;
alter table job_requirement_skills add constraint FKa219y3r4j20bevndk97kgvltc foreign key (skill_id) references skills;
alter table job_requirement_skills add constraint FK5l5ubx5co1pql8fbbcg1djgu2 foreign key (job_requirement_id) references job_requirements;
alter table resume_embeddings add constraint FKpyt883om92h115omw77vgxt1i foreign key (candidate_id) references candidates;
alter table users add constraint FKibk1e3kaxy5sfyeekp8hbhnim foreign key (created_by) references users;
