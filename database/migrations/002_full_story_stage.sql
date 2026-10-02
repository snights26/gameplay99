-- Additive full-story persistence. Existing trial users, characters, sessions, and snapshots remain untouched.
ALTER TABLE circles ADD COLUMN IF NOT EXISTS starting_relationship TEXT NOT NULL DEFAULT 'PARTY_GROUP';
ALTER TABLE circles ADD COLUMN IF NOT EXISTS configuration JSONB NOT NULL DEFAULT '{}';

ALTER TABLE game_sessions ADD COLUMN IF NOT EXISTS circle_id UUID REFERENCES circles(id) ON DELETE SET NULL;
ALTER TABLE game_sessions ADD COLUMN IF NOT EXISTS story_state JSONB NOT NULL DEFAULT '{}';
CREATE INDEX IF NOT EXISTS sessions_circle_idx ON game_sessions(circle_id) WHERE circle_id IS NOT NULL;

ALTER TABLE game_events ADD COLUMN IF NOT EXISTS client_event_id UUID;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'game_events_session_client_event_key') THEN
    ALTER TABLE game_events ADD CONSTRAINT game_events_session_client_event_key UNIQUE (session_id, client_event_id);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS session_story_state (
  session_id UUID PRIMARY KEY REFERENCES game_sessions(id) ON DELETE CASCADE,
  current_location TEXT NOT NULL DEFAULT '',
  phase TEXT NOT NULL DEFAULT 'arrival',
  mood TEXT NOT NULL DEFAULT 'curious',
  heat SMALLINT NOT NULL DEFAULT 0 CHECK (heat BETWEEN 0 AND 100),
  progression JSONB NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS session_director_card_state (
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  director_card_id UUID NOT NULL REFERENCES director_cards(id) ON DELETE CASCADE,
  is_available BOOLEAN NOT NULL DEFAULT true,
  is_applied BOOLEAN NOT NULL DEFAULT false,
  applied_event_number INTEGER,
  metadata JSONB NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (session_id, director_card_id)
);
CREATE INDEX IF NOT EXISTS session_director_cards_applied_idx ON session_director_card_state(session_id, is_applied);

CREATE TABLE IF NOT EXISTS session_trait_discoveries (
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
  trait_key TEXT NOT NULL,
  discovered BOOLEAN NOT NULL DEFAULT false,
  discovered_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}',
  PRIMARY KEY (session_id, character_id, trait_key)
);
CREATE INDEX IF NOT EXISTS session_trait_discoveries_character_idx ON session_trait_discoveries(session_id, character_id, discovered);

CREATE TABLE IF NOT EXISTS story_checkpoints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  checkpoint_type TEXT NOT NULL,
  chapter INTEGER NOT NULL DEFAULT 1 CHECK (chapter >= 1),
  scene_number INTEGER NOT NULL DEFAULT 1 CHECK (scene_number >= 1),
  state JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS story_checkpoints_session_created_idx ON story_checkpoints(session_id, created_at DESC);
