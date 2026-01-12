-- Azure PostgreSQL Database Schema for Leaderboard Platform
-- Run this script if you want to create tables manually instead of using Hibernate auto-creation
-- 
-- Usage:
--   psql -h <server-name>.postgres.database.azure.com \
--        -U <username>@<server-name> \
--        -d leaderboard_db \
--        -f create_tables.sql

-- Connect to the database (if not already connected)
\c leaderboard_db

-- ============================================
-- LEADERBOARDS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS leaderboards (
    leaderboard_id VARCHAR(255) PRIMARY KEY,
    uuid VARCHAR(255) UNIQUE NOT NULL,
    pod_id VARCHAR(255),
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    metadata JSONB
);

-- Indexes for leaderboards table
CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_uuid ON leaderboards(uuid);
CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_leaderboard_id ON leaderboards(leaderboard_id);
CREATE INDEX IF NOT EXISTS idx_leaderboard_pod_id ON leaderboards(pod_id);

-- ============================================
-- USER_SCORES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS user_scores (
    user_id VARCHAR(255) NOT NULL,
    leaderboard_id VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, leaderboard_id)
);

-- Indexes for user_scores table
CREATE INDEX IF NOT EXISTS idx_user_score_leaderboard_user ON user_scores(leaderboard_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_score_timestamp ON user_scores(timestamp);
CREATE INDEX IF NOT EXISTS idx_user_score_leaderboard_score ON user_scores(leaderboard_id, score DESC, timestamp ASC);

-- ============================================
-- VERIFICATION
-- ============================================
-- Verify tables were created
SELECT 
    table_name,
    table_type
FROM 
    information_schema.tables
WHERE 
    table_schema = 'public'
    AND table_name IN ('leaderboards', 'user_scores')
ORDER BY 
    table_name;

-- Display table structures
\d leaderboards
\d user_scores

-- Success message
\echo 'Tables created successfully!'
