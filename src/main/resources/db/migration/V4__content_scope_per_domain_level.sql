-- =========================================
-- V4: Content scoping (domain, level, version) + backfill JLPT N5
-- =========================================

-- 1) Tambah kolom scope di content_versions
ALTER TABLE content_versions
    ADD COLUMN IF NOT EXISTS domain TEXT NOT NULL DEFAULT 'JLPT',
    ADD COLUMN IF NOT EXISTS level jlpt_level,
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

-- 2) Pastikan kombinasi unik per scope
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema='public'
          AND table_name='content_versions'
          AND constraint_name='uq_content_versions_scope'
    ) THEN
        ALTER TABLE content_versions
            ADD CONSTRAINT uq_content_versions_scope UNIQUE (domain, level, version);
    END IF;
END $$;

-- 3) Backfill data existing -> JLPT N5 v1
--    (asumsi seed awal N5 bootstrap adalah versi pertama)
UPDATE content_versions
SET domain='JLPT', level='N5', version=1
WHERE label = 'N5 bootstrap' AND (domain IS NULL OR domain='JLPT');

-- 4) Index bantu query per scope
CREATE INDEX IF NOT EXISTS idx_content_versions_scope ON content_versions(domain, level, version);

-- 5) Opsional: view untuk memudahkan admin/ops
CREATE OR REPLACE VIEW vw_content_latest AS
SELECT DISTINCT ON (domain, level)
    domain, level, version, id AS version_id, label, created_at
FROM content_versions
ORDER BY domain, level, version DESC, id DESC;
