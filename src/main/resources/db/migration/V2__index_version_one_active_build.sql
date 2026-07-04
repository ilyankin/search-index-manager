CREATE UNIQUE INDEX uk_index_version_one_active_build
    ON index_version (index_id)
    WHERE status IN ('CREATED', 'BUILDING');
