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
  response.json(result.rows);
});

app.post("/api/v1/circles", async (request, response) => {
  const ownerUserId = userId(request);
  const circle = circleSchema.parse(request.body);
  const result = await withTransaction(async (client) => {
    const owned = await client.query("SELECT id FROM characters WHERE owner_user_id=$1 AND id=ANY($2::uuid[])", [ownerUserId, circle.characterIds]);
    if (owned.rowCount !== circle.characterIds.length) throw new HttpError(400, "A circle can only contain your own characters.");
    const created = await client.query<{ id: string }>("INSERT INTO circles(owner_user_id,name) VALUES($1,$2) RETURNING id", [ownerUserId, circle.name]);
    for (const [order, characterId] of circle.characterIds.entries()) await client.query("INSERT INTO circle_members(circle_id,character_id,display_order) VALUES($1,$2,$3)", [created.rows[0].id, characterId, order]);
    return created.rows[0];
  });
  response.status(201).json(result);
});

app.put("/api/v1/circles/:id", async (request, response) => {
  const ownerUserId = userId(request);
  const circleId = uuid.parse(request.params.id);
  const circle = circleSchema.parse(request.body);
  await withTransaction(async (client) => {
    const updated = await client.query("UPDATE circles SET name=$3,updated_at=now() WHERE id=$1 AND owner_user_id=$2 RETURNING id", [circleId, ownerUserId, circle.name]);
    if (!updated.rowCount) throw new HttpError(404, "Circle not found.");
    const owned = await client.query("SELECT id FROM characters WHERE owner_user_id=$1 AND id=ANY($2::uuid[])", [ownerUserId, circle.characterIds]);
    if (owned.rowCount !== circle.characterIds.length) throw new HttpError(400, "A circle can only contain your own characters.");
    await client.query("DELETE FROM circle_members WHERE circle_id=$1", [circleId]);
    for (const [order, characterId] of circle.characterIds.entries()) await client.query("INSERT INTO circle_members(circle_id,character_id,display_order) VALUES($1,$2,$3)", [circleId, characterId, order]);
  });
  response.status(204).end();
});

app.get("/api/v1/scenarios", async (_request, response) => {
  const result = await query("SELECT id, name, description, locations, participant_min, participant_max, available_actions, heat_modifier, roleplay_compatibility FROM scenarios ORDER BY name");
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
    const session = await client.query<{ id: string }>(
      `INSERT INTO game_sessions (owner_user_id, scenario_id, mode, relationship_start, status, current_state)
       VALUES ($1,$2,$3,$4,'ACTIVE',$5) RETURNING id`,
      [ownerUserId, input.scenarioId, input.mode, input.relationshipStart, input.state]
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
  const [session, participants, relationships, events] = await Promise.all([
    query("SELECT * FROM game_sessions WHERE id=$1", [sessionId]),
    query("SELECT sp.*, c.name, c.personality, c.flirt_profile, c.intimacy_profile, c.hidden_traits FROM session_participants sp JOIN characters c ON c.id=sp.character_id WHERE sp.session_id=$1", [sessionId]),
    query("SELECT * FROM relationship_states WHERE session_id=$1", [sessionId]),
    query("SELECT * FROM game_events WHERE session_id=$1 ORDER BY created_at DESC LIMIT 100", [sessionId])
  ]);
  response.json({ session: session.rows[0], participants: participants.rows, relationships: relationships.rows, events: events.rows.reverse() });
});

app.post("/api/v1/sessions/:id/events", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  await verifyOwner(sessionId, userId(request));
  const event = eventSchema.parse(request.body);
  const result = await query(
    `INSERT INTO game_events (session_id, actor_participant_id, target_participant_id, event_type, payload)
     VALUES ($1,$2,$3,$4,$5) RETURNING *`,
    [sessionId, event.actorParticipantId ?? null, event.targetParticipantId ?? null, event.eventType, event.payload]
  );
  response.status(201).json(result.rows[0]);
});

app.post("/api/v1/sessions/:id/sync", async (request, response) => {
  const sessionId = uuid.parse(request.params.id);
  const ownerUserId = userId(request);
  const sync = syncSchema.parse(request.body);
  await verifyOwner(sessionId, ownerUserId);
  await withTransaction(async (client) => {
    await client.query("UPDATE game_sessions SET current_state=$2, updated_at=now() WHERE id=$1", [sessionId, sync.state]);
    await client.query("INSERT INTO session_snapshots (session_id,state) VALUES ($1,$2)", [sessionId, sync.state]);
    for (const event of sync.events) {
      await client.query(
        "INSERT INTO game_events (session_id,actor_participant_id,target_participant_id,event_type,payload) VALUES ($1,$2,$3,$4,$5)",
        [sessionId, event.actorParticipantId ?? null, event.targetParticipantId ?? null, event.eventType, event.payload]
      );
    }
    await syncBlindDateProjection(client, sessionId, sync.state);
  });
  response.status(204).end();
});

function score(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : fallback;
}

async function syncBlindDateProjection(client: import("pg").PoolClient, sessionId: string, state: Record<string, unknown>): Promise<void> {
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
  const relationship = typeof state.relationship === "object" && state.relationship !== null ? state.relationship as Record<string, unknown> : null;
  if (!relationship) return;
  const participants = await client.query<{ id: string; participant_type: "PLAYER" | "CHARACTER" }>(
    "SELECT id, participant_type FROM session_participants WHERE session_id=$1", [sessionId]
  );
  const player = participants.rows.find((item) => item.participant_type === "PLAYER");
  const character = participants.rows.find((item) => item.participant_type === "CHARACTER");
  if (!player || !character) return;
  const values = [
    score(relationship.attraction, 32), score(relationship.trust, 28), score(relationship.comfort, 30),
    score(relationship.curiosity, 45), score(relationship.jealousy, 0), score(relationship.tension, 18),
    score(relationship.attachment, 12), score(relationship.competition, 0), score(relationship.desire, 15), score(relationship.confidence, 50)
  ];
  await client.query(
    `UPDATE relationship_states SET attraction=$4, trust=$5, comfort=$6, curiosity=$7, jealousy=$8,
       tension=$9, attachment=$10, competition=$11, desire=$12, confidence=$13, updated_at=now()
     WHERE session_id=$1 AND source_participant_id=$2 AND target_participant_id=$3`,
    [sessionId, player.id, character.id, ...values]
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

