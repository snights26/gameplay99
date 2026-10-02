import "dotenv/config";
import crypto from "node:crypto";
import cors from "cors";
import express, { type Request, type Response } from "express";
import helmet from "helmet";
import { ZodError } from "zod";
import { db, query, withTransaction } from "./db.js";
import {
  bootstrapSchema, characterSchema, circleSchema, consentSchema, eventSchema, sessionSchema, syncSchema, uuid
} from "./validation.js";

const app = express();
const port = Number(process.env.PORT ?? 3000);
const allowedOrigin = process.env.ALLOWED_ORIGIN ?? "http://localhost";

app.disable("x-powered-by");
app.use(helmet());
app.use(cors({ origin: allowedOrigin, methods: ["GET", "POST", "PUT", "DELETE"] }));
app.use(express.json({ limit: "200kb" }));

function userId(request: Request): string {
  return uuid.parse(request.header("x-trial-user-id"));
}

async function verifyOwner(sessionId: string, ownerUserId: string): Promise<void> {
  const result = await query("SELECT id FROM game_sessions WHERE id = $1 AND owner_user_id = $2", [sessionId, ownerUserId]);
  if (!result.rowCount) throw new HttpError(404, "Session not found.");
}

class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

app.get("/health", async (_request, response) => {
  await query("SELECT 1");
  response.json({ status: "ok" });
});

app.get("/api/v1/health", async (_request, response) => {
  await query("SELECT 1");
  response.json({ status: "ok" });
});

app.post("/api/v1/trial/bootstrap", async (request, response) => {
  const { installId } = bootstrapSchema.parse(request.body);
  const installHash = crypto.createHash("sha256").update(installId).digest("hex");
  const result = await query<{ id: string }>(
    `INSERT INTO trial_users (install_hash)
     VALUES ($1)
     ON CONFLICT (install_hash) DO UPDATE SET last_seen_at = now()
     RETURNING id`,
    [installHash]
  );
  const consent = await query("SELECT 1 FROM user_consents WHERE user_id=$1 AND age_confirmed=true AND data_storage_consent=true", [result.rows[0].id]);
  response.status(200).json({ userId: result.rows[0].id, hasConsent: Boolean(consent.rowCount) });
});

app.post("/api/v1/consent", async (request, response) => {
  const ownerUserId = userId(request);
  const consent = consentSchema.parse(request.body);
  await query(
    `INSERT INTO user_consents (user_id, age_confirmed, data_storage_consent, consent_version, consented_at)
     VALUES ($1, $2, $3, $4, now())
     ON CONFLICT (user_id) DO UPDATE SET
       age_confirmed = EXCLUDED.age_confirmed,
       data_storage_consent = EXCLUDED.data_storage_consent,
       consent_version = EXCLUDED.consent_version,
       consented_at = EXCLUDED.consented_at`,
    [ownerUserId, consent.ageConfirmed, consent.dataStorageConsent, consent.consentVersion]
  );
  response.status(204).end();
});

app.get("/api/v1/characters", async (request, response) => {
  const result = await query("SELECT * FROM characters WHERE owner_user_id = $1 ORDER BY updated_at DESC", [userId(request)]);
  response.json(result.rows.map(characterResponse));
});

app.post("/api/v1/characters", async (request, response) => {
  const ownerUserId = userId(request);
  const character = characterSchema.parse(request.body);
  const result = await query(
    `INSERT INTO characters (
       owner_user_id, name, age, gender, pronouns, avatar_key, description, personality,
       communication_profile, flirt_profile, intimacy_profile, fantasy_profile, boundaries, hidden_traits
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) RETURNING *`,
    [ownerUserId, character.name, character.age, character.gender, character.pronouns ?? null,
      character.avatarKey ?? null, character.description ?? null, character.personality,
      character.communicationProfile, character.flirtProfile, character.intimacyProfile,
      character.fantasyProfile, character.boundaries, character.hiddenTraits]
  );
  response.status(201).json(characterResponse(result.rows[0]));
});

app.get("/api/v1/characters/:id", async (request, response) => {
  const result = await query("SELECT * FROM characters WHERE id=$1 AND owner_user_id=$2", [uuid.parse(request.params.id), userId(request)]);
  if (!result.rowCount) throw new HttpError(404, "Character not found.");
  response.json(characterResponse(result.rows[0]));
});

app.put("/api/v1/characters/:id", async (request, response) => {
  const ownerUserId = userId(request);
  const characterId = uuid.parse(request.params.id);
  const character = characterSchema.parse(request.body);
  const result = await query(
    `UPDATE characters SET name=$3, age=$4, gender=$5, pronouns=$6, avatar_key=$7, description=$8,
      personality=$9, communication_profile=$10, flirt_profile=$11, intimacy_profile=$12,
      fantasy_profile=$13, boundaries=$14, hidden_traits=$15, updated_at=now()
     WHERE id=$1 AND owner_user_id=$2 RETURNING *`,
    [characterId, ownerUserId, character.name, character.age, character.gender, character.pronouns ?? null,
      character.avatarKey ?? null, character.description ?? null, character.personality,
      character.communicationProfile, character.flirtProfile, character.intimacyProfile,
      character.fantasyProfile, character.boundaries, character.hiddenTraits]
  );
  if (!result.rowCount) throw new HttpError(404, "Character not found.");
  response.json(characterResponse(result.rows[0]));
});

function characterResponse(character: Record<string, unknown>) {
  return {
    id: character.id,
    name: character.name,
    age: character.age,
    gender: character.gender,
    pronouns: character.pronouns,
    avatarKey: character.avatar_key,
    description: character.description,
    personality: character.personality,
    communicationProfile: character.communication_profile,
    flirtProfile: character.flirt_profile,
    intimacyProfile: character.intimacy_profile,
    fantasyProfile: character.fantasy_profile,
    boundaries: character.boundaries,
    hiddenTraits: character.hidden_traits
  };
}

function circleResponse(circle: Record<string, unknown>) {
  return {
    id: circle.id,
    name: circle.name,
    startingRelationship: circle.starting_relationship,
    configuration: circle.configuration,
    members: circle.members
  };
}

app.delete("/api/v1/characters/:id", async (request, response) => {
  const result = await query("DELETE FROM characters WHERE id=$1 AND owner_user_id=$2 RETURNING id", [uuid.parse(request.params.id), userId(request)]);
  if (!result.rowCount) throw new HttpError(404, "Character not found.");
  response.status(204).end();
});

app.get("/api/v1/circles", async (request, response) => {
  const result = await query(
    `SELECT c.*, COALESCE(json_agg(json_build_object('id',ch.id,'name',ch.name,'displayOrder',cm.display_order)
       ORDER BY cm.display_order) FILTER (WHERE ch.id IS NOT NULL), '[]') AS members
     FROM circles c LEFT JOIN circle_members cm ON cm.circle_id=c.id LEFT JOIN characters ch ON ch.id=cm.character_id
     WHERE c.owner_user_id=$1 GROUP BY c.id ORDER BY c.updated_at DESC`, [userId(request)]
  );
  response.json(result.rows.map(circleResponse));
});

app.post("/api/v1/circles", async (request, response) => {
  const ownerUserId = userId(request);
  const circle = circleSchema.parse(request.body);
  const result = await withTransaction(async (client) => {
    const owned = await client.query("SELECT id FROM characters WHERE owner_user_id=$1 AND id=ANY($2::uuid[])", [ownerUserId, circle.characterIds]);
    if (owned.rowCount !== circle.characterIds.length) throw new HttpError(400, "A circle can only contain your own characters.");
    const created = await client.query<{ id: string }>(
      "INSERT INTO circles(owner_user_id,name,starting_relationship,configuration) VALUES($1,$2,$3,$4) RETURNING id",
      [ownerUserId, circle.name, circle.startingRelationship, circle.configuration]
    );
    for (const [order, characterId] of circle.characterIds.entries()) await client.query("INSERT INTO circle_members(circle_id,character_id,display_order) VALUES($1,$2,$3)", [created.rows[0].id, characterId, order]);
    return created.rows[0];
  });
  response.status(201).json({
    id: result.id,
    name: circle.name,
    startingRelationship: circle.startingRelationship,
    configuration: circle.configuration,
    members: circle.characterIds.map((id, displayOrder) => ({ id, displayOrder }))
  });
});

app.put("/api/v1/circles/:id", async (request, response) => {
  const ownerUserId = userId(request);
  const circleId = uuid.parse(request.params.id);
  const circle = circleSchema.parse(request.body);
  await withTransaction(async (client) => {
    const updated = await client.query(
      "UPDATE circles SET name=$3,starting_relationship=$4,configuration=$5,updated_at=now() WHERE id=$1 AND owner_user_id=$2 RETURNING id",
      [circleId, ownerUserId, circle.name, circle.startingRelationship, circle.configuration]
    );
    if (!updated.rowCount) throw new HttpError(404, "Circle not found.");
    const owned = await client.query("SELECT id FROM characters WHERE owner_user_id=$1 AND id=ANY($2::uuid[])", [ownerUserId, circle.characterIds]);
    if (owned.rowCount !== circle.characterIds.length) throw new HttpError(400, "A circle can only contain your own characters.");
    await client.query("DELETE FROM circle_members WHERE circle_id=$1", [circleId]);
    for (const [order, characterId] of circle.characterIds.entries()) await client.query("INSERT INTO circle_members(circle_id,character_id,display_order) VALUES($1,$2,$3)", [circleId, characterId, order]);
  });
  response.status(204).end();
});

app.get("/api/v1/scenarios", async (_request, response) => {
  const result = await query("SELECT id, name, description, locations, participant_min, participant_max, scene_phases, event_pool, available_actions, heat_modifier, roleplay_compatibility FROM scenarios ORDER BY name");
  response.json(result.rows);
});

app.get("/api/v1/roles", async (_request, response) => {
  const result = await query("SELECT id, name, description FROM scenario_roles ORDER BY name");
  response.json(result.rows);
});

app.get("/api/v1/intimacy-cards", async (_request, response) => {
  const result = await query("SELECT id, name, category, intensity, closeness, control_balance, difficulty, experimental_score, required_heat, compatibility_tags, icon_key FROM intimacy_cards ORDER BY required_heat, name");
  response.json(result.rows);
});

app.post("/api/v1/sessions", async (request, response) => {
  const ownerUserId = userId(request);
  const input = sessionSchema.parse(request.body);
  const result = await withTransaction(async (client) => {
    const consent = await client.query("SELECT 1 FROM user_consents WHERE user_id=$1 AND age_confirmed=true AND data_storage_consent=true", [ownerUserId]);
    if (!consent.rowCount) throw new HttpError(403, "Adults-only data consent is required before starting a story.");
    const validCharacters = await client.query("SELECT id FROM characters WHERE owner_user_id=$1 AND id = ANY($2::uuid[])", [ownerUserId, input.participantCharacterIds]);
    if (validCharacters.rowCount !== input.participantCharacterIds.length) throw new HttpError(400, "One or more selected characters are invalid.");
    if (input.circleId) {
      const selectedCircle = await client.query(
        "SELECT id FROM circles WHERE id=$1 AND owner_user_id=$2",
        [input.circleId, ownerUserId]
      );
      if (!selectedCircle.rowCount) throw new HttpError(400, "The selected circle is invalid.");
      const members = await client.query<{ character_id: string }>("SELECT character_id FROM circle_members WHERE circle_id=$1", [input.circleId]);
      const memberIds = new Set(members.rows.map((member) => member.character_id));
      if (input.participantCharacterIds.some((characterId) => !memberIds.has(characterId))) {
        throw new HttpError(400, "A session loaded from a circle can only include that circle's members.");
      }
    }
    const storyState = record(input.state.story);
    const session = await client.query<{ id: string }>(
      `INSERT INTO game_sessions (owner_user_id, scenario_id, mode, relationship_start, circle_id, status, current_state, story_state)
       VALUES ($1,$2,$3,$4,$5,'ACTIVE',$6,$7) RETURNING id`,
      [ownerUserId, input.scenarioId, input.mode, input.relationshipStart, input.circleId ?? null, input.state, storyState]
    );
    const player = await client.query<{ id: string }>(
      "INSERT INTO session_participants (session_id, participant_type, display_name) VALUES ($1,'PLAYER','Player') RETURNING id",
      [session.rows[0].id]
    );
    const participants = [player.rows[0].id];
    for (const characterId of input.participantCharacterIds) {
      const participant = await client.query<{ id: string }>(
        "INSERT INTO session_participants (session_id, character_id, participant_type) VALUES ($1,$2,'CHARACTER') RETURNING id",
        [session.rows[0].id, characterId]
      );
      participants.push(participant.rows[0].id);
    }
    for (const sourceId of participants) {
      for (const targetId of participants) {
        if (sourceId === targetId) continue;
        await client.query(
          `INSERT INTO relationship_states (
             session_id, source_participant_id, target_participant_id, attraction, trust, comfort, curiosity, tension, desire, confidence
           ) VALUES ($1,$2,$3,32,28,30,45,18,15,50)`,
          [session.rows[0].id, sourceId, targetId]
        );
      }
    }
    await client.query("INSERT INTO session_snapshots (session_id, state) VALUES ($1,$2)", [session.rows[0].id, input.state]);
    await syncStoryProjections(client, session.rows[0].id, input.state);
    return session.rows[0];
  });
  response.status(201).json(result);
});

app.get("/api/v1/sessions/active", async (request, response) => {
  const result = await query(
    `SELECT gs.* FROM game_sessions gs WHERE gs.owner_user_id=$1 AND gs.status='ACTIVE'
     ORDER BY gs.updated_at DESC LIMIT 1`, [userId(request)]
  );
  response.json(result.rows[0] ?? null);
});

app.get("/api/v1/sessions/:id", async (request, response) => {
  const ownerUserId = userId(request);
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, ownerUserId);
  const [session, participants, relationships, events, story, roles, discoveries, directorCards] = await Promise.all([
    query("SELECT * FROM game_sessions WHERE id=$1", [sessionId]),
    query("SELECT sp.*, c.name, c.personality, c.communication_profile, c.flirt_profile, c.intimacy_profile, c.fantasy_profile, c.boundaries, c.hidden_traits FROM session_participants sp LEFT JOIN characters c ON c.id=sp.character_id WHERE sp.session_id=$1", [sessionId]),
    query("SELECT * FROM relationship_states WHERE session_id=$1", [sessionId]),
    query("SELECT * FROM game_events WHERE session_id=$1 ORDER BY created_at DESC LIMIT 100", [sessionId]),
    query("SELECT * FROM session_story_state WHERE session_id=$1", [sessionId]),
    query(`SELECT ra.participant_id, sr.name AS role_name FROM role_assignments ra
      JOIN scenario_roles sr ON sr.id=ra.role_id WHERE ra.session_id=$1`, [sessionId]),
    query("SELECT character_id, trait_key, discovered, discovered_at, metadata FROM session_trait_discoveries WHERE session_id=$1", [sessionId]),
    query(`SELECT dc.card_key, sdcs.is_available, sdcs.is_applied, sdcs.applied_event_number, sdcs.metadata
      FROM session_director_card_state sdcs JOIN director_cards dc ON dc.id=sdcs.director_card_id WHERE sdcs.session_id=$1`, [sessionId])
  ]);
  response.json({
    session: session.rows[0], participants: participants.rows, relationships: relationships.rows, events: events.rows.reverse(),
    story: story.rows[0] ?? null, roles: roles.rows, discoveries: discoveries.rows, directorCards: directorCards.rows
  });
});

app.post("/api/v1/sessions/:id/events", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, userId(request));
  const event = eventSchema.parse(request.body);
  const result = await query(
    `INSERT INTO game_events (session_id, client_event_id, actor_participant_id, target_participant_id, event_type, payload)
     VALUES ($1,$2,$3,$4,$5,$6)
     ON CONFLICT (session_id, client_event_id) DO NOTHING RETURNING *`,
    [sessionId, event.clientEventId ?? null, event.actorParticipantId ?? null, event.targetParticipantId ?? null, event.eventType, event.payload]
  );
  response.status(result.rowCount ? 201 : 200).json(result.rows[0] ?? { sessionId, duplicate: true });
});

app.post("/api/v1/sessions/:id/sync", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  const ownerUserId = userId(request);
  const sync = syncSchema.parse(request.body);
  await verifyOwner(sessionId, ownerUserId);
  const eventsPersisted = await withTransaction(async (client) => {
    const storyState = record(sync.state.story);
    await client.query("UPDATE game_sessions SET current_state=$2, story_state=$3, updated_at=now() WHERE id=$1", [sessionId, sync.state, storyState]);
    await client.query("INSERT INTO session_snapshots (session_id,state) VALUES ($1,$2)", [sessionId, sync.state]);
    let insertedEvents = 0;
    for (const event of sync.events) {
      const inserted = await client.query(
        `INSERT INTO game_events (session_id,client_event_id,actor_participant_id,target_participant_id,event_type,payload)
         VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (session_id,client_event_id) DO NOTHING`,
        [sessionId, event.clientEventId ?? null, event.actorParticipantId ?? null, event.targetParticipantId ?? null, event.eventType, event.payload]
      );
      insertedEvents += inserted.rowCount ?? 0;
    }
    await syncStoryProjections(client, sessionId, sync.state);
    return insertedEvents;
  });
  response.status(200).json({ sessionId, syncedAt: new Date().toISOString(), eventsPersisted });
});

function score(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : fallback;
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function text(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value.slice(0, 2000) : fallback;
}

type ParticipantRow = { id: string; character_id: string | null; participant_type: "PLAYER" | "CHARACTER" };

async function syncStoryProjections(client: import("pg").PoolClient, sessionId: string, state: Record<string, unknown>): Promise<void> {
  const participants = await client.query<ParticipantRow>(
    "SELECT id, character_id, participant_type FROM session_participants WHERE session_id=$1",
    [sessionId]
  );
  const participantByStoryId = new Map<string, string>();
  for (const participant of participants.rows) {
    participantByStoryId.set(participant.participant_type === "PLAYER" ? "PLAYER" : participant.character_id ?? participant.id, participant.id);
    if (participant.character_id) participantByStoryId.set(participant.character_id, participant.id);
  }

  const logs = Array.isArray(state.log) ? state.log : [];
  for (const rawLog of logs) {
    if (typeof rawLog !== "object" || rawLog === null) continue;
    const log = rawLog as Record<string, unknown>;
    const messageId = uuid.safeParse(log.id);
    if (!messageId.success || typeof log.speaker !== "string" || typeof log.text !== "string") continue;
    await client.query(
      `INSERT INTO messages (session_id, client_message_id, message_type, content, metadata)
       VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (session_id, client_message_id) DO NOTHING`,
      [sessionId, messageId.data, log.speaker.slice(0, 80), log.text.slice(0, 2000), {
        cue: typeof log.cue === "string" ? log.cue : "",
        isSystem: log.isSystem === true
      }]
    );
  }
  const relationships = record(state.directionalRelationships);
  for (const [direction, rawRelationship] of Object.entries(relationships)) {
    const [sourceStoryId, targetStoryId] = direction.split("->", 2);
    const sourceParticipantId = participantByStoryId.get(sourceStoryId);
    const targetParticipantId = participantByStoryId.get(targetStoryId);
    if (!sourceParticipantId || !targetParticipantId || sourceParticipantId === targetParticipantId) continue;
    await upsertRelationship(client, sessionId, sourceParticipantId, targetParticipantId, record(rawRelationship));
  }
  // Compatibility projection for snapshots made by the original one-on-one engine.
  if (Object.keys(relationships).length === 0) {
    const player = participants.rows.find((participant) => participant.participant_type === "PLAYER");
    const firstCharacter = participants.rows.find((participant) => participant.participant_type === "CHARACTER");
    if (player && firstCharacter) await upsertRelationship(client, sessionId, player.id, firstCharacter.id, record(state.relationship));
  }

  const story = record(state.story);
  const privateState = record(state.privateState);
  const currentLocation = text(privateState.active === true ? privateState.location : story.currentLocation, text(state.location));
  const phase = text(story.phase, "arrival");
  const mood = text(story.mood, "curious");
  const heat = score(state.heat, 0);
  await client.query(
    `INSERT INTO session_story_state (session_id,current_location,phase,mood,heat,progression)
     VALUES ($1,$2,$3,$4,$5,$6)
     ON CONFLICT (session_id) DO UPDATE SET current_location=EXCLUDED.current_location, phase=EXCLUDED.phase,
       mood=EXCLUDED.mood, heat=EXCLUDED.heat, progression=EXCLUDED.progression, updated_at=now()`,
    [sessionId, currentLocation, phase, mood, heat, story]
  );
  const activeScene = await client.query<{ id: string }>(
    `UPDATE scenes SET location=$2, phase=$3, mood=$4, heat=$5, is_private=$6
     WHERE id=(SELECT id FROM scenes WHERE session_id=$1 AND ended_at IS NULL ORDER BY started_at DESC LIMIT 1)
     RETURNING id`,
    [sessionId, currentLocation, phase, mood, heat, privateState.active === true]
  );
  if (!activeScene.rowCount) {
    await client.query("INSERT INTO scenes(session_id,location,phase,mood,heat,is_private) VALUES($1,$2,$3,$4,$5,$6)",
      [sessionId, currentLocation, phase, mood, heat, privateState.active === true]);
  }

  const discoveries = record(state.discoveredTraitsByCharacter);
  for (const [characterId, rawTraits] of Object.entries(discoveries)) {
    if (!participantByStoryId.has(characterId)) continue;
    for (const [traitKey, value] of Object.entries(record(rawTraits))) {
      const discovered = value === "DISCOVERED";
      await client.query(
        `INSERT INTO session_trait_discoveries(session_id,character_id,trait_key,discovered,discovered_at,metadata)
         VALUES($1,$2,$3,$4,CASE WHEN $4 THEN now() ELSE NULL END,$5)
         ON CONFLICT(session_id,character_id,trait_key) DO UPDATE SET
           discovered=session_trait_discoveries.discovered OR EXCLUDED.discovered,
           discovered_at=COALESCE(session_trait_discoveries.discovered_at, EXCLUDED.discovered_at), metadata=EXCLUDED.metadata`,
        [sessionId, characterId, traitKey.slice(0, 100), discovered, { state: text(value) }]
      );
    }
  }

  const roles = record(state.assignedRoles);
  for (const [storyParticipantId, roleKey] of Object.entries(roles)) {
    const participantId = participantByStoryId.get(storyParticipantId);
    if (!participantId || typeof roleKey !== "string") continue;
    const role = await client.query<{ id: string }>(
      "SELECT id FROM scenario_roles WHERE lower(replace(name,' ','_'))=lower($1) LIMIT 1",
      [roleKey]
    );
    if (!role.rowCount) continue;
    await client.query(
      `INSERT INTO role_assignments(session_id,participant_id,role_id) VALUES($1,$2,$3)
       ON CONFLICT(session_id,participant_id) DO UPDATE SET role_id=EXCLUDED.role_id`,
      [sessionId, participantId, role.rows[0].id]
    );
  }

  const directorCards = record(state.directorCards);
  for (const [cardKey, rawCardState] of Object.entries(directorCards)) {
    const card = await client.query<{ id: string }>("SELECT id FROM director_cards WHERE card_key=$1", [cardKey]);
    if (!card.rowCount) continue;
    const cardState = record(rawCardState);
    const applied = cardState.applied === true;
    const available = cardState.available !== false;
    const appliedAt = typeof cardState.appliedAtEvent === "number" ? Math.round(cardState.appliedAtEvent) : null;
    await client.query(
      `INSERT INTO session_director_card_state(session_id,director_card_id,is_available,is_applied,applied_event_number,metadata)
       VALUES($1,$2,$3,$4,$5,$6)
       ON CONFLICT(session_id,director_card_id) DO UPDATE SET is_available=EXCLUDED.is_available,
         is_applied=session_director_card_state.is_applied OR EXCLUDED.is_applied,
         applied_event_number=COALESCE(session_director_card_state.applied_event_number, EXCLUDED.applied_event_number),
         metadata=EXCLUDED.metadata, updated_at=now()`,
      [sessionId, card.rows[0].id, available, applied, appliedAt, cardState]
    );
  }
}

async function upsertRelationship(
  client: import("pg").PoolClient, sessionId: string, sourceParticipantId: string, targetParticipantId: string, relationship: Record<string, unknown>
): Promise<void> {
  const values = [
    score(relationship.attraction, 32), score(relationship.trust, 28), score(relationship.comfort, 30),
    score(relationship.curiosity, 45), score(relationship.jealousy, 0), score(relationship.tension, 18),
    score(relationship.attachment, 12), score(relationship.competition, 0), score(relationship.desire, 15), score(relationship.confidence, 50)
  ];
  await client.query(
    `INSERT INTO relationship_states(session_id,source_participant_id,target_participant_id,attraction,trust,comfort,curiosity,jealousy,tension,attachment,competition,desire,confidence,metadata)
     VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
     ON CONFLICT(session_id,source_participant_id,target_participant_id) DO UPDATE SET attraction=EXCLUDED.attraction,
       trust=EXCLUDED.trust,comfort=EXCLUDED.comfort,curiosity=EXCLUDED.curiosity,jealousy=EXCLUDED.jealousy,
       tension=EXCLUDED.tension,attachment=EXCLUDED.attachment,competition=EXCLUDED.competition,desire=EXCLUDED.desire,
       confidence=EXCLUDED.confidence,metadata=EXCLUDED.metadata,updated_at=now()`,
    [sessionId, sourceParticipantId, targetParticipantId, ...values, relationship]
  );
}

app.get("/api/v1/sessions/:id/messages", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, userId(request));
  const result = await query("SELECT * FROM messages WHERE session_id=$1 ORDER BY created_at", [sessionId]);
  response.json(result.rows);
});

app.get("/api/v1/sessions/:id/relationships", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, userId(request));
  const result = await query("SELECT * FROM relationship_states WHERE session_id=$1", [sessionId]);
  response.json(result.rows);
});

app.post("/api/v1/sessions/:id/end", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, userId(request));
  await query("UPDATE game_sessions SET status='ENDED', updated_at=now() WHERE id=$1", [sessionId]);
  response.status(204).end();
});

app.use((error: unknown, _request: Request, response: Response, _next: express.NextFunction) => {
  if (error instanceof ZodError) return response.status(400).json({ error: "Invalid request.", details: error.issues.map((issue) => issue.path.join(".")) });
  if (error instanceof HttpError) return response.status(error.status).json({ error: error.message });
  console.error(error);
  return response.status(500).json({ error: "Something went wrong. No sensitive information was exposed." });
});

if (!process.env.VERCEL) {
  app.listen(port, () => console.log(`Starry Nights API listening on ${port}`));
}

process.on("SIGTERM", () => db.end());

export default app;

