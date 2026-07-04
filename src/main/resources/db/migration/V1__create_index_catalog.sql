CREATE TABLE search_index
(
    id          uuid PRIMARY KEY,
    name        text NOT NULL UNIQUE,
    description text,
    fields      jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);

-- Versioned Entity pattern
-- Goals:
-- 1. Rollback
-- 2. Zero Downtime Deployment
-- 3. Kafka async support
-- 4. History log
CREATE TABLE index_version
(
    id             uuid PRIMARY KEY,
    index_id       uuid NOT NULL REFERENCES search_index (id) ON DELETE CASCADE,
    version        integer NOT NULL,
    status         text NOT NULL,
    doc_count      bigint,
    artifact_key   text,
    artifact_size  bigint,
    checksum       text,
    error_message  text,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    UNIQUE (index_id, version)
);