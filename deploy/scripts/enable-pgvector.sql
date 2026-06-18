-- Enable pgvector on an existing PostgreSQL 16 database (upgrade from postgres:16-alpine).
-- Run against the agentpanel database before starting the panel with pgvector/pgvector:pg16.
--
-- Usage (Docker Compose volume still mounted):
--   docker compose exec postgres psql -U agentpanel -d agentpanel -f /path/to/enable-pgvector.sql
-- Or from host:
--   psql -h localhost -U agentpanel -d agentpanel -f deploy/scripts/enable-pgvector.sql

CREATE EXTENSION IF NOT EXISTS vector;

-- Verify (optional)
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
