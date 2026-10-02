-- Starry Nights private-trial schema. Apply this only from the backend/Neon console.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS trial_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  install_hash TEXT UNIQUE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_consents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES trial_users(id) ON DELETE CASCADE,
  age_confirmed BOOLEAN NOT NULL CHECK (age_confirmed),
  data_storage_consent BOOLEAN NOT NULL CHECK (data_storage_consent),
  consent_version TEXT NOT NULL,
  consented_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS characters (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL REFERENCES trial_users(id) ON DELETE CASCADE,
  name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
  age SMALLINT NOT NULL CHECK (age >= 18 AND age <= 120),
  gender TEXT NOT NULL,
  pronouns TEXT,
  avatar_key TEXT,
  description TEXT,
  personality JSONB NOT NULL DEFAULT '{}',
  communication_profile JSONB NOT NULL DEFAULT '{}',
  flirt_profile JSONB NOT NULL DEFAULT '{}',
  intimacy_profile JSONB NOT NULL DEFAULT '{}',
  fantasy_profile JSONB NOT NULL DEFAULT '{}',
  boundaries JSONB NOT NULL DEFAULT '{}',
  hidden_traits JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS characters_owner_updated_idx ON characters(owner_user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS circles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL REFERENCES trial_users(id) ON DELETE CASCADE,
  name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS circle_members (
  circle_id UUID NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
  display_order SMALLINT NOT NULL CHECK (display_order BETWEEN 0 AND 4),
  PRIMARY KEY (circle_id, character_id),
  UNIQUE (circle_id, display_order)
);

CREATE TABLE IF NOT EXISTS scenarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE,
  description TEXT NOT NULL,
  locations JSONB NOT NULL,
  participant_min SMALLINT NOT NULL CHECK (participant_min BETWEEN 1 AND 5),
  participant_max SMALLINT NOT NULL CHECK (participant_max BETWEEN participant_min AND 5),
  scene_phases JSONB NOT NULL DEFAULT '[]',
  event_pool JSONB NOT NULL DEFAULT '[]',
  available_actions JSONB NOT NULL DEFAULT '[]',
  heat_modifier SMALLINT NOT NULL DEFAULT 0 CHECK (heat_modifier BETWEEN -20 AND 20),
  roleplay_compatibility JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS scenario_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE,
  description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS game_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL REFERENCES trial_users(id) ON DELETE CASCADE,
  scenario_id UUID NOT NULL REFERENCES scenarios(id),
  mode TEXT NOT NULL CHECK (mode IN ('ONE_ON_ONE', 'CIRCLE')),
  relationship_start TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
  current_state JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sessions_active_idx ON game_sessions(owner_user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS session_participants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  character_id UUID REFERENCES characters(id) ON DELETE SET NULL,
  participant_type TEXT NOT NULL CHECK (participant_type IN ('PLAYER', 'CHARACTER')),
  display_name TEXT,
  UNIQUE (session_id, character_id)
);

CREATE TABLE IF NOT EXISTS relationship_states (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  source_participant_id UUID NOT NULL REFERENCES session_participants(id) ON DELETE CASCADE,
  target_participant_id UUID NOT NULL REFERENCES session_participants(id) ON DELETE CASCADE,
  attraction SMALLINT NOT NULL DEFAULT 0 CHECK (attraction BETWEEN 0 AND 100),
  trust SMALLINT NOT NULL DEFAULT 0 CHECK (trust BETWEEN 0 AND 100),
  comfort SMALLINT NOT NULL DEFAULT 0 CHECK (comfort BETWEEN 0 AND 100),
  curiosity SMALLINT NOT NULL DEFAULT 0 CHECK (curiosity BETWEEN 0 AND 100),
  jealousy SMALLINT NOT NULL DEFAULT 0 CHECK (jealousy BETWEEN 0 AND 100),
  tension SMALLINT NOT NULL DEFAULT 0 CHECK (tension BETWEEN 0 AND 100),
  attachment SMALLINT NOT NULL DEFAULT 0 CHECK (attachment BETWEEN 0 AND 100),
  competition SMALLINT NOT NULL DEFAULT 0 CHECK (competition BETWEEN 0 AND 100),
  desire SMALLINT NOT NULL DEFAULT 0 CHECK (desire BETWEEN 0 AND 100),
  confidence SMALLINT NOT NULL DEFAULT 0 CHECK (confidence BETWEEN 0 AND 100),
  metadata JSONB NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, source_participant_id, target_participant_id),
  CHECK (source_participant_id <> target_participant_id)
);

CREATE TABLE IF NOT EXISTS role_assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  participant_id UUID NOT NULL REFERENCES session_participants(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES scenario_roles(id),
  UNIQUE (session_id, participant_id)
);

CREATE TABLE IF NOT EXISTS scenes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  location TEXT NOT NULL,
  phase TEXT NOT NULL,
  mood TEXT NOT NULL,
  heat SMALLINT NOT NULL DEFAULT 0 CHECK (heat BETWEEN 0 AND 100),
  is_private BOOLEAN NOT NULL DEFAULT false,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  client_message_id UUID,
  scene_id UUID REFERENCES scenes(id) ON DELETE SET NULL,
  actor_participant_id UUID REFERENCES session_participants(id) ON DELETE SET NULL,
  target_participant_id UUID REFERENCES session_participants(id) ON DELETE SET NULL,
  message_type TEXT NOT NULL,
  content TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT messages_session_client_message_key UNIQUE (session_id, client_message_id)
);
CREATE INDEX IF NOT EXISTS messages_session_idx ON messages(session_id, created_at);

-- Additive migration for databases created by the first private-trial build.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS client_message_id UUID;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'messages_session_client_message_key'
  ) THEN
    ALTER TABLE messages ADD CONSTRAINT messages_session_client_message_key UNIQUE (session_id, client_message_id);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS game_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  actor_participant_id UUID REFERENCES session_participants(id) ON DELETE SET NULL,
  target_participant_id UUID REFERENCES session_participants(id) ON DELETE SET NULL,
  event_type TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS events_session_created_idx ON game_events(session_id, created_at);

CREATE TABLE IF NOT EXISTS session_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  state JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS snapshots_session_created_idx ON session_snapshots(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS unlocks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  unlock_type TEXT NOT NULL,
  unlock_key TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, unlock_type, unlock_key)
);

CREATE TABLE IF NOT EXISTS trait_definitions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  trait_key TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS dialogue_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  template_key TEXT NOT NULL UNIQUE,
  speaker_style TEXT NOT NULL,
  text TEXT NOT NULL,
  tags JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS action_definitions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  action_key TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  description TEXT NOT NULL,
  timing_window_ms INTEGER NOT NULL DEFAULT 0 CHECK (timing_window_ms BETWEEN 0 AND 10000),
  tags JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS director_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_key TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  description TEXT NOT NULL,
  effect JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS intimacy_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  intensity SMALLINT NOT NULL CHECK (intensity BETWEEN 1 AND 5),
  closeness SMALLINT NOT NULL CHECK (closeness BETWEEN 1 AND 5),
  control_balance SMALLINT NOT NULL CHECK (control_balance BETWEEN -2 AND 2),
  difficulty SMALLINT NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
  experimental_score SMALLINT NOT NULL CHECK (experimental_score BETWEEN 1 AND 5),
  required_heat SMALLINT NOT NULL CHECK (required_heat BETWEEN 0 AND 100),
  compatibility_tags JSONB NOT NULL DEFAULT '[]',
  icon_key TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS fantasy_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category_key TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  description TEXT NOT NULL
);

