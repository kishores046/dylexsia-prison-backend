-- Flyway Migration: V1__Initial_Schema
-- Date: 2026-05-19
-- Purpose: Create core tables for user management, gaze sessions, ML results, metrics, and audit logs

-- ============================================================================
-- TABLE: user_table
-- Purpose: Store user authentication and profile information
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(255) NOT NULL,
    email_id VARCHAR(255) UNIQUE,
    role VARCHAR(50),
    password VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    gender CHAR(1),
    status VARCHAR(50) NOT NULL,
    ph_no VARCHAR(20),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_at TIMESTAMP,
    blocked_by VARCHAR(255),
    block_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_email ON user_table(email_id);
CREATE INDEX idx_user_status ON user_table(status);
CREATE INDEX idx_user_role ON user_table(role);

-- ============================================================================
-- TABLE: refresh_tokens
-- Purpose: Store JWT refresh tokens for session management
-- ============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_table(id)
);

CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_expires ON refresh_tokens(expires_at);

-- ============================================================================
-- TABLE: student
-- Purpose: Store student profile information
-- ============================================================================
CREATE TABLE IF NOT EXISTS student (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    street VARCHAR(255),
    area VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(10),
    country VARCHAR(255),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES user_table(id),
    CONSTRAINT fk_student_id FOREIGN KEY (id) REFERENCES user_table(id)
);

CREATE INDEX idx_student_user_id ON student(user_id);

-- ============================================================================
-- TABLE: parent
-- Purpose: Store parent profile information
-- ============================================================================
CREATE TABLE IF NOT EXISTS parent (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    street VARCHAR(255),
    area VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(10),
    country VARCHAR(255),
    CONSTRAINT fk_parent_user FOREIGN KEY (user_id) REFERENCES user_table(id),
    CONSTRAINT fk_parent_id FOREIGN KEY (id) REFERENCES user_table(id)
);

CREATE INDEX idx_parent_user_id ON parent(user_id);

-- ============================================================================
-- TABLE: teacher
-- Purpose: Store teacher profile information
-- ============================================================================
CREATE TABLE IF NOT EXISTS teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    street VARCHAR(255),
    area VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(10),
    country VARCHAR(255),
    specialization VARCHAR(100),
    years_of_experience INT,
    CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES user_table(id)
);

CREATE INDEX idx_teacher_user_id ON teacher(user_id);

-- ============================================================================
-- TABLE: parent_student
-- Purpose: Relationship table between parents and students
-- ============================================================================
CREATE TABLE IF NOT EXISTS parent_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    relationship_type VARCHAR(100),
    primary_contact BOOLEAN,
    emergency_priority INT,
    CONSTRAINT fk_parent_student_parent FOREIGN KEY (parent_id) REFERENCES parent(id),
    CONSTRAINT fk_parent_student_student FOREIGN KEY (student_id) REFERENCES student(id)
);

CREATE INDEX idx_parent_id ON parent_student(parent_id);
CREATE INDEX idx_parent_student_student_id ON parent_student(student_id);

-- ============================================================================
-- TABLE: teacher_student
-- Purpose: Relationship table between teachers and students
-- ============================================================================
CREATE TABLE IF NOT EXISTS teacher_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    assignment_type VARCHAR(100),
    active BOOLEAN,
    assigned_at TIMESTAMP,
    removed_at TIMESTAMP,
    CONSTRAINT fk_teacher_student_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    CONSTRAINT fk_teacher_student_student FOREIGN KEY (student_id) REFERENCES student(id),
    UNIQUE KEY uk_teacher_student (teacher_id, student_id)
);

CREATE INDEX idx_teacher_student_teacher ON teacher_student(teacher_id);
CREATE INDEX idx_teacher_student_student ON teacher_student(student_id);

-- ============================================================================
-- TABLE: gaze_sessions
-- Purpose: Store gaze analysis session metadata and statistics
-- ============================================================================
CREATE TABLE IF NOT EXISTS gaze_sessions (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    session_status VARCHAR(50) NOT NULL,
    device_metadata TEXT,
    frame_count INTEGER NOT NULL DEFAULT 0,
    feature_count INTEGER NOT NULL DEFAULT 0,
    average_latency_ms DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gaze_session_id ON gaze_sessions(session_id);
CREATE INDEX idx_gaze_username ON gaze_sessions(username);
CREATE INDEX idx_gaze_status ON gaze_sessions(session_status);
CREATE INDEX idx_gaze_created_at ON gaze_sessions(created_at);

-- ============================================================================
-- TABLE: ml_results
-- Purpose: Store ML analysis results for each session
-- ============================================================================
CREATE TABLE IF NOT EXISTS ml_results (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    classification VARCHAR(100) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    rule_score DOUBLE PRECISION,
    rf_score DOUBLE PRECISION,
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ml_session_id ON ml_results(session_id);
CREATE INDEX idx_ml_classification ON ml_results(classification);
CREATE INDEX idx_ml_created_at ON ml_results(created_at);

-- ============================================================================
-- TABLE: session_metrics
-- Purpose: Store per-session performance metrics
-- ============================================================================
CREATE TABLE IF NOT EXISTS session_metrics (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL UNIQUE,
    frame_count INTEGER NOT NULL DEFAULT 0,
    feature_count INTEGER NOT NULL DEFAULT 0,
    dropped_frames INTEGER NOT NULL DEFAULT 0,
    average_latency_ms DOUBLE PRECISION,
    max_latency_ms DOUBLE PRECISION,
    min_latency_ms DOUBLE PRECISION,
    websocket_disconnects INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metrics_session_id ON session_metrics(session_id);
CREATE INDEX idx_metrics_created_at ON session_metrics(created_at);

-- ============================================================================
-- TABLE: audit_events
-- Purpose: Store audit trail for debugging and compliance
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_events (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_message TEXT,
    severity VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_session_id ON audit_events(session_id);
CREATE INDEX idx_audit_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_severity ON audit_events(severity);
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp);

-- ============================================================================
-- TABLE DESCRIPTIONS
-- ============================================================================
-- user_table: Stores user authentication and profile information
-- refresh_tokens: Stores JWT refresh tokens for session management
-- student: Stores student profile information with address
-- parent: Stores parent profile information with address
-- teacher: Stores teacher profile information with specialization
-- parent_student: Relationship table between parents and students
-- teacher_student: Relationship table between teachers and students
-- gaze_sessions: Stores complete gaze analysis session lifecycle
-- ml_results: Stores ML model predictions and confidence scores
-- session_metrics: Stores session-level performance metrics
-- audit_events: Audit trail for session events and errors

