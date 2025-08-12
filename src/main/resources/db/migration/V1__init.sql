CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS citext;

-- users & roles
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       email CITEXT UNIQUE NOT NULL,
                       password_hash TEXT NOT NULL,
                       display_name TEXT,
                       is_active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       name TEXT UNIQUE NOT NULL
);

CREATE TABLE user_roles (
                            user_id UUID REFERENCES users(id) ON DELETE CASCADE,
                            role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

-- entitlements
DO $$ BEGIN
    CREATE TYPE entitlement_code AS ENUM ('premium_all','level_N4','level_N3','ssw_access');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE user_entitlements (
                                   id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                   user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   code entitlement_code NOT NULL,
                                   source TEXT NOT NULL, -- purchase, admin_grant, promo
                                   starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   ends_at TIMESTAMPTZ
);

-- content versions
CREATE TABLE content_versions (
                                  id BIGSERIAL PRIMARY KEY,
                                  label TEXT,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$ BEGIN
    CREATE TYPE jlpt_level AS ENUM ('N5','N4','N3');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE decks (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       level jlpt_level NOT NULL,
                       title TEXT NOT NULL,
                       slug TEXT UNIQUE NOT NULL,
                       version_id BIGINT NOT NULL REFERENCES content_versions(id) ON DELETE RESTRICT
);

DO $$ BEGIN
    CREATE TYPE card_type AS ENUM ('VOCAB','KANJI','BUNPO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE cards (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       deck_id UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
                       type card_type NOT NULL,
                       headword TEXT NOT NULL,
                       reading TEXT,
                       meaning_id TEXT,
                       metadata JSONB,
                       version_id BIGINT NOT NULL REFERENCES content_versions(id) ON DELETE RESTRICT
);

CREATE TABLE progress_snapshots (
                                    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                    day DATE NOT NULL,
                                    stats JSONB NOT NULL,
                                    UNIQUE(user_id, day)
);

-- indexes ringkas
CREATE INDEX IF NOT EXISTS idx_decks_level ON decks(level);
CREATE INDEX IF NOT EXISTS idx_cards_deck_type ON cards(deck_id, type);
