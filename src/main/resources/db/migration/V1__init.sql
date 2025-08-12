-- =========================================
-- V1: Core schema (users, roles, entitlements, content + i18n_keys)
-- =========================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS citext;

-- Enums
DO $$ BEGIN
    CREATE TYPE jlpt_level AS ENUM ('N5','N4','N3');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE card_type AS ENUM ('VOCAB','KANJI','BUNPO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE entitlement_code AS ENUM ('premium_all','level_N4','level_N3','ssw_access');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Users & Auth
CREATE TABLE IF NOT EXISTS users (
                                     id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                     email         CITEXT UNIQUE NOT NULL,
                                     password_hash TEXT NOT NULL,
                                     display_name  TEXT,
                                     is_active     BOOLEAN NOT NULL DEFAULT TRUE,
                                     created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                                     updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS roles (
                                     id   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                     name TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                          role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                          PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                              user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                              token_hash  TEXT NOT NULL,
                                              expires_at  TIMESTAMPTZ NOT NULL,
                                              created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                              revoked_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Entitlements
CREATE TABLE IF NOT EXISTS user_entitlements (
                                                 id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                                 user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                                 code      entitlement_code NOT NULL,
                                                 source    TEXT NOT NULL, -- purchase, admin_grant, promo
                                                 starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                                 ends_at   TIMESTAMPTZ    -- NULL = lifetime
);
-- Span unik (mulai kapan) — tetap longgar untuk extend di masa depan
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema='public'
          AND table_name='user_entitlements'
          AND constraint_name='uq_user_entitlement_span'
    ) THEN
        ALTER TABLE user_entitlements
            ADD CONSTRAINT uq_user_entitlement_span UNIQUE (user_id, code, starts_at);
    END IF;
END $$;

-- entitlements aktif: bagi dua
-- 1) lifetime (ends_at IS NULL)
CREATE INDEX IF NOT EXISTS idx_entitlements_active_null
    ON user_entitlements(user_id, code)
    WHERE ends_at IS NULL;

-- 2) which has expiration date: use btree ends_at for filter > :ts
CREATE INDEX IF NOT EXISTS idx_entitlements_active_ends
    ON user_entitlements(user_id, code, ends_at);

-- Content versioning (immutable)
CREATE TABLE IF NOT EXISTS content_versions (
                                                id         BIGSERIAL PRIMARY KEY,
                                                label      TEXT NOT NULL,
                                                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema='public'
          AND table_name='content_versions'
          AND constraint_name='uq_content_versions_label'
    ) THEN
        ALTER TABLE content_versions
            ADD CONSTRAINT uq_content_versions_label UNIQUE (label);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS decks (
                                     id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                     level      jlpt_level NOT NULL,
                                     title      TEXT NOT NULL,
                                     slug       TEXT NOT NULL,
                                     version_id BIGINT NOT NULL REFERENCES content_versions(id) ON DELETE RESTRICT
);
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema='public'
          AND table_name='decks'
          AND constraint_name='uq_decks_slug'
    ) THEN
        ALTER TABLE decks
            ADD CONSTRAINT uq_decks_slug UNIQUE (slug);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_decks_level ON decks(level);
CREATE INDEX IF NOT EXISTS idx_decks_version ON decks(version_id);

-- I18N KEY REGISTRY (C) — agar cards.meaning_id bisa FK
CREATE TABLE IF NOT EXISTS i18n_keys (
                                         key TEXT PRIMARY KEY  -- contoh: 'mean.v.taberu'
);

CREATE TABLE IF NOT EXISTS cards (
                                     id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                     deck_id    UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
                                     type       card_type NOT NULL,
                                     headword   TEXT NOT NULL,
                                     reading    TEXT,
                                     meaning_id TEXT NOT NULL REFERENCES i18n_keys(key) ON DELETE RESTRICT,
                                     metadata   JSONB,
                                     version_id BIGINT NOT NULL REFERENCES content_versions(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_cards_deck_type ON cards(deck_id, type);
CREATE INDEX IF NOT EXISTS idx_cards_version   ON cards(version_id);

-- Progress snapshot (harian)
CREATE TABLE IF NOT EXISTS progress_snapshots (
                                                  id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                                  day     DATE NOT NULL,
                                                  stats   JSONB NOT NULL, -- {reviews, new, accuracy, time_sec}
                                                  UNIQUE (user_id, day)
);

-- Seed dasar roles
INSERT INTO roles(name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles(name) VALUES ('USER')  ON CONFLICT (name) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
