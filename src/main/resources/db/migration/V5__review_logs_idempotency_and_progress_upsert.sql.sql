-- =========================================
-- V5: Add idempotency to existing review_logs + upsert helpers
-- =========================================

-- 1) Tambah kolom idempotency_key (nullable dulu)
ALTER TABLE review_logs
    ADD COLUMN IF NOT EXISTS idempotency_key text;

-- 2) Backfill idempotency_key hanya bila masih ada NULL
DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM review_logs WHERE idempotency_key IS NULL) THEN
            UPDATE review_logs rl
            SET idempotency_key = md5(
                    rl.user_id::text || '|' ||
                    COALESCE(rl.device_id::text,'00000000-0000-0000-0000-000000000000') || '|' ||
                    rl.card_id::text || '|' ||
                    extract(epoch from rl.reviewed_at)::text
                                  )
            WHERE rl.idempotency_key IS NULL;
        END IF;
    END $$;

-- 3) Set NOT NULL (semua row sudah punya nilai)
ALTER TABLE review_logs
    ALTER COLUMN idempotency_key SET NOT NULL;

-- 4) Unique constraint idempotensi (user, device, key)
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_review_idemp') THEN
            ALTER TABLE review_logs
                ADD CONSTRAINT uq_review_idemp UNIQUE (user_id, device_id, idempotency_key);
        END IF;
    END $$;

-- 5) Index bantu (kalau belum ada)
CREATE INDEX IF NOT EXISTS idx_review_logs_card ON review_logs(card_id);

-- 6) progress_snapshots: pastikan JSONB & unique (idempotent upsert)
ALTER TABLE progress_snapshots
    ALTER COLUMN stats SET DATA TYPE jsonb
        USING stats::jsonb;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_progress_user_day') THEN
            ALTER TABLE progress_snapshots
                ADD CONSTRAINT uq_progress_user_day UNIQUE (user_id, day);
        END IF;
    END $$;