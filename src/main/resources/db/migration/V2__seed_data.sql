-- =====================================================================
-- Resume Analyzer - Flyway V2: seed data
-- Default RBAC users, initial skill master data, and system health rows.
-- All inserts are idempotent (ON CONFLICT ... DO NOTHING) so they are
-- safe to run both on a fresh database (V1 -> V2) and on an existing
-- pre-Flyway database (baselined at V1 -> V2 only).
--
-- NOTE: unlike the legacy docker/init-rbac.sql / init-skills.sql scripts,
-- these inserts always provide id / created_at / updated_at because the
-- JPA-managed tables define these columns WITHOUT database defaults.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Default RBAC users
-- Passwords (BCrypt, cost 10):
--   admin         / Admin@123
--   recruiter     / Recruiter@123
--   hr            / HR@123
--   hiring_manager / Manager@123
-- ---------------------------------------------------------------------

INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, is_active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin',
    'admin@resume-analyzer.local',
    '$2a$10$hhS9w38LM1MVmWoZFVy4.uJtLA3744g6uMRzkusy6T2XtbW6cnpbC',
    'System',
    'Administrator',
    'ADMIN',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, is_active, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'recruiter',
    'recruiter@resume-analyzer.local',
    '$2a$10$NC/jBJn6DwYhqoy8763tMOJl7nX8LKthcwvKvB6hq7MKBwXsN2CMe',
    'Jane',
    'Recruiter',
    'RECRUITER',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM users WHERE username = 'admin')
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, is_active, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'hr',
    'hr@resume-analyzer.local',
    '$2a$10$eU9MCFy.iSmwr6iULFpE0.9DT/t//9rchNd5YB/lZInoXlBIcZcIm',
    'Bob',
    'HR',
    'HR',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM users WHERE username = 'admin')
) ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, is_active, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'hiring_manager',
    'manager@resume-analyzer.local',
    '$2a$10$7fwuDCQ6DAEz0CL7sq7aA.7aDOs3xFf86ARCUg0HP2Iy3d/QtVNwi',
    'Alice',
    'Manager',
    'HIRING_MANAGER',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM users WHERE username = 'admin')
) ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------
-- System health baseline entries
-- Service names must match com.abhishek.resumeanalyzer.services.SystemHealthService
-- (database / llm-studio / application); the app upserts these at startup.
-- ---------------------------------------------------------------------

INSERT INTO system_health (id, service_name, status, message, failure_count, last_checked_at)
VALUES
    (gen_random_uuid(), 'database', 'UP', 'PostgreSQL connection healthy', 0, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'llm-studio', 'UNKNOWN', 'LM Studio not checked yet', 0, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'application', 'UP', 'Spring Boot application running', 0, CURRENT_TIMESTAMP)
ON CONFLICT (service_name) DO UPDATE
SET status = EXCLUDED.status,
    message = EXCLUDED.message,
    failure_count = EXCLUDED.failure_count,
    last_checked_at = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- Skills master data
-- ---------------------------------------------------------------------

-- Programming Languages
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'Java', 'Programming Language', 'Object-oriented programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Python', 'Programming Language', 'High-level programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'JavaScript', 'Programming Language', 'Web programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'TypeScript', 'Programming Language', 'Typed superset of JavaScript', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'C#', 'Programming Language', '.NET programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'C++', 'Programming Language', 'Systems programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Go', 'Programming Language', 'Google programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Kotlin', 'Programming Language', 'Modern JVM language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Scala', 'Programming Language', 'Functional JVM language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Ruby', 'Programming Language', 'Dynamic programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'PHP', 'Programming Language', 'Server-side scripting language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Swift', 'Programming Language', 'Apple programming language', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Frameworks
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'Spring Boot', 'Framework', 'Java application framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Spring Framework', 'Framework', 'Java enterprise framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'React', 'Framework', 'JavaScript UI library', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Angular', 'Framework', 'TypeScript web framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Vue.js', 'Framework', 'Progressive JavaScript framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Node.js', 'Framework', 'JavaScript runtime', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Express.js', 'Framework', 'Node.js web framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Django', 'Framework', 'Python web framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Flask', 'Framework', 'Python micro-framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), '.NET Core', 'Framework', 'Microsoft application framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'ASP.NET', 'Framework', 'Microsoft web framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Hibernate', 'Framework', 'Java ORM framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'JPA', 'Framework', 'Java Persistence API', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Databases
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'PostgreSQL', 'Database', 'Relational database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'MySQL', 'Database', 'Relational database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'MongoDB', 'Database', 'NoSQL document database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Oracle Database', 'Database', 'Enterprise relational database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Microsoft SQL Server', 'Database', 'Microsoft relational database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Redis', 'Database', 'In-memory data store', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Cassandra', 'Database', 'Distributed NoSQL database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'DynamoDB', 'Database', 'AWS NoSQL database', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Elasticsearch', 'Database', 'Search and analytics engine', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Cloud Platforms
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'AWS', 'Cloud Platform', 'Amazon Web Services', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Azure', 'Cloud Platform', 'Microsoft cloud platform', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Google Cloud Platform', 'Cloud Platform', 'Google cloud services', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Docker', 'Cloud Platform', 'Container platform', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Kubernetes', 'Cloud Platform', 'Container orchestration', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Terraform', 'Cloud Platform', 'Infrastructure as Code', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Tools & Technologies
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'Git', 'Tool', 'Version control system', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Jenkins', 'Tool', 'CI/CD automation', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Maven', 'Tool', 'Java build tool', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Gradle', 'Tool', 'Build automation tool', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'GraphQL', 'Tool', 'Query language for APIs', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'REST API', 'Tool', 'RESTful web services', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Microservices', 'Architecture', 'Microservices architecture', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'JUnit', 'Tool', 'Java testing framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Mockito', 'Tool', 'Java mocking framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Agile', 'Methodology', 'Agile development methodology', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Scrum', 'Methodology', 'Scrum framework', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Web Technologies
INSERT INTO skills (id, name, category, description, is_active, created_at, updated_at) VALUES
(gen_random_uuid(), 'HTML5', 'Web Technology', 'Latest HTML standard', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'CSS3', 'Web Technology', 'Latest CSS standard', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'SASS', 'Web Technology', 'CSS preprocessor', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Webpack', 'Web Technology', 'Module bundler', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Vite', 'Web Technology', 'Frontend build tool', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), 'Redux', 'Web Technology', 'State management library', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
