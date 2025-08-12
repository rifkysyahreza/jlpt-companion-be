-- =========================================
-- V2: SRS + Sync + Passages + i18n_strings + Delta + Audit
-- =========================================

-- i18n strings (C): terkait i18n_keys
CREATE TABLE IF NOT EXISTS i18n_strings (
                                            key    TEXT NOT NULL REFERENCES i18n_keys(key) ON DELETE RESTRICT,
                                            locale TEXT NOT NULL,  -- "id","en","ja"
                                            text   TEXT NOT NULL,
                                            PRIMARY KEY (key, locale)
);

-- Passages (konten cerita/teks)
CREATE TABLE IF NOT EXISTS passages (
                                        id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                        level      jlpt_level NOT NULL,
                                        title      TEXT,
                                        body       TEXT NOT NULL,
                                        highlights JSONB,  -- [{offset,len,card_id,type}]
                                        version_id BIGINT NOT NULL REFERENCES content_versions(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_passages_version ON passages(version_id);

-- Delta tracking (D) + size_hint
CREATE TABLE IF NOT EXISTS content_deltas (
                                              id           BIGSERIAL PRIMARY KEY,
                                              from_version BIGINT NOT NULL REFERENCES content_versions(id),
                                              to_version   BIGINT NOT NULL REFERENCES content_versions(id),
                                              payload      JSONB NOT NULL,
                                              checksum     TEXT NOT NULL,
                                              size_hint    BIGINT  -- in bytes, untuk hint bandwidth
);
CREATE INDEX IF NOT EXISTS idx_content_deltas_from_to ON content_deltas(from_version, to_version);

-- SRS (user state) + logs
DO $$ BEGIN
    CREATE TYPE review_grade AS ENUM ('AGAIN','HARD','GOOD','EASY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS user_cards (
                                          id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                          user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                          card_id       UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
                                          ease          REAL NOT NULL DEFAULT 2.5,
                                          interval_days INTEGER NOT NULL DEFAULT 0,
                                          due_at        TIMESTAMPTZ,
                                          reps          INTEGER NOT NULL DEFAULT 0,
                                          lapses        INTEGER NOT NULL DEFAULT 0,
                                          suspended     BOOLEAN NOT NULL DEFAULT FALSE,
                                          UNIQUE(user_id, card_id)
);
-- (A) due index untuk query cepat
CREATE INDEX IF NOT EXISTS idx_user_cards_due ON user_cards(user_id, due_at);

CREATE TABLE IF NOT EXISTS review_logs (
                                           id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                           user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           card_id         UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
                                           reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                                           grade           review_grade NOT NULL,
                                           previous_interval INTEGER,
                                           next_interval     INTEGER,
                                           response_ms       INTEGER,
                                           device_id       UUID,
                                           payload         JSONB
);
CREATE INDEX IF NOT EXISTS idx_review_logs_user_time ON review_logs(user_id, reviewed_at DESC);
-- (B) Catatan: Saat data membesar, pertimbangkan PARTITION BY RANGE (reviewed_at) per bulan.

-- Per-device sync
CREATE TABLE IF NOT EXISTS user_devices (
                                            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                            user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                            platform    TEXT NOT NULL,           -- android/ios
                                            app_version TEXT,
                                            created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id);

CREATE TABLE IF NOT EXISTS sync_states (
                                           id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                           user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           device_id             UUID NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
                                           last_content_version  BIGINT NOT NULL DEFAULT 0,
                                           last_progress_pull_at TIMESTAMPTZ,
                                           last_logs_upload_at   TIMESTAMPTZ,
                                           UNIQUE(user_id, device_id)
);

CREATE TABLE IF NOT EXISTS change_logs (
                                           id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                           user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           device_id        UUID NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
                                           kind             TEXT NOT NULL,
                                           occurred_at      TIMESTAMPTZ NOT NULL,
                                           payload          JSONB NOT NULL,
                                           idempotency_key  TEXT NOT NULL,
                                           UNIQUE(user_id, device_id, idempotency_key)
);
-- (B) Catatan: change_logs juga kandidat partisi (berdasar occurred_at).

-- (E) Audit events (grant entitlement, publish content, webhook billing, admin ops)
CREATE TABLE IF NOT EXISTS audit_events (
                                            id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                            occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                            actor_user_id UUID,         -- null kalau system/webhook
                                            event_type TEXT NOT NULL,   -- entitlement_grant, content_publish, billing_paid, etc.
                                            target     TEXT,            -- mis.: user_id / version_id / purchase_id
                                            details    JSONB            -- payload bebas
);
CREATE INDEX IF NOT EXISTS idx_audit_events_time ON audit_events(occurred_at);

-- =========================
-- Seed N5 snapshot (idempotent)
-- =========================

-- 1) i18n key untuk 'taberu'
INSERT INTO i18n_keys(key)
SELECT 'mean.v.taberu'
WHERE NOT EXISTS (SELECT 1 FROM i18n_keys WHERE key = 'mean.v.taberu');

-- 2) Insert content version pertama
INSERT INTO content_versions(label)
SELECT 'N5 bootstrap'
WHERE NOT EXISTS (SELECT 1 FROM content_versions WHERE label='N5 bootstrap');

-- 3) Deck N5 starter
INSERT INTO decks(id, level, title, slug, version_id)
SELECT uuid_generate_v4(), 'N5', 'N5 Core Starter', 'n5-core-starter',
       (SELECT id FROM content_versions WHERE label='N5 bootstrap')
WHERE NOT EXISTS (SELECT 1 FROM decks WHERE slug='n5-core-starter');

-- 4) 1 kartu contoh
INSERT INTO cards(id, deck_id, type, headword, reading, meaning_id, metadata, version_id)
SELECT uuid_generate_v4(),
       (SELECT id FROM decks WHERE slug='n5-core-starter'),
       'VOCAB','食べる','たべる','mean.v.taberu',
       '{"jlpt":"N5","pos":"v-ichidan"}'::jsonb,
       (SELECT id FROM content_versions WHERE label='N5 bootstrap')
WHERE NOT EXISTS (
    SELECT 1 FROM cards
    WHERE headword='食べる'
      AND version_id=(SELECT id FROM content_versions WHERE label='N5 bootstrap')
);

-- 5) i18n strings untuk meaning_id tsb.
INSERT INTO i18n_strings(key, locale, text) VALUES
    ('mean.v.taberu','id','makan')
ON CONFLICT (key, locale) DO NOTHING;

INSERT INTO i18n_strings(key, locale, text) VALUES
    ('mean.v.taberu','en','to eat')
ON CONFLICT (key, locale) DO NOTHING;

-- 6) Delta snapshot from=to (D) + size_hint dihitung via CTE
WITH v AS (
    SELECT id FROM content_versions WHERE label='N5 bootstrap'
),
     payload AS (
         SELECT jsonb_build_object(
                        'decks', COALESCE((
                                              SELECT jsonb_agg(jsonb_build_object('id', d.id, 'op','upsert','level', d.level, 'title', d.title, 'slug', d.slug))
                                              FROM decks d WHERE d.version_id = (SELECT id FROM v)
                                          ), '[]'::jsonb),
                        'cards', COALESCE((
                                              SELECT jsonb_agg(jsonb_build_object('id', c.id, 'op','upsert',
                                                                                  'deckId', c.deck_id, 'type', c.type,
                                                                                  'headword', c.headword, 'reading', c.reading,
                                                                                  'meaningId', c.meaning_id, 'metadata', c.metadata))
                                              FROM cards c WHERE c.version_id = (SELECT id FROM v)
                                          ), '[]'::jsonb),
                        'passages', COALESCE((
                                                 SELECT jsonb_agg(jsonb_build_object('id', p.id, 'op','upsert',
                                                                                     'level', p.level, 'title', p.title,
                                                                                     'body', p.body, 'highlights', p.highlights))
                                                 FROM passages p WHERE p.version_id = (SELECT id FROM v)
                                             ), '[]'::jsonb),
                        'i18n', COALESCE((
                                             SELECT jsonb_agg(jsonb_build_object('key', s.key, 'locale', s.locale, 'text', s.text))
                                             FROM i18n_strings s WHERE s.key IN ('mean.v.taberu')
                                         ), '[]'::jsonb)
                ) AS p
     )
INSERT INTO content_deltas(from_version, to_version, payload, checksum, size_hint)
SELECT (SELECT id FROM v), (SELECT id FROM v), p,
       'sha256:seed',
       octet_length(p::text)
FROM payload
WHERE NOT EXISTS (
    SELECT 1 FROM content_deltas cd
    WHERE cd.from_version = (SELECT id FROM v) AND cd.to_version = (SELECT id FROM v)
);

CREATE INDEX IF NOT EXISTS idx_cards_version ON cards(version_id);
CREATE INDEX IF NOT EXISTS idx_passages_version ON passages(version_id);
CREATE INDEX IF NOT EXISTS idx_user_cards_due ON user_cards(user_id, due_at);
