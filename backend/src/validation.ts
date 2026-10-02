import { z } from "zod";

export const uuid = z.string().uuid();
export const jsonRecord = z.record(z.string(), z.unknown());
export const userHeader = z.object({ "x-trial-user-id": uuid });

export const bootstrapSchema = z.object({
  installId: z.string().min(16).max(256)
});

export const consentSchema = z.object({
  ageConfirmed: z.literal(true),
  dataStorageConsent: z.literal(true),
  consentVersion: z.string().min(1).max(40)
});

export const characterSchema = z.object({
  name: z.string().trim().min(1).max(60),
  age: z.number().int().min(18).max(120),
  gender: z.string().trim().min(1).max(40),
  pronouns: z.string().trim().max(50).nullable().optional(),
  avatarKey: z.string().trim().max(80).nullable().optional(),
  description: z.string().trim().max(500).nullable().optional(),
  personality: jsonRecord,
  communicationProfile: jsonRecord,
  flirtProfile: jsonRecord,
  intimacyProfile: jsonRecord,
  fantasyProfile: jsonRecord,
  boundaries: jsonRecord,
  hiddenTraits: jsonRecord.default({})
});

export const sessionSchema = z.object({
  scenarioId: uuid,
  mode: z.enum(["ONE_ON_ONE", "CIRCLE"]),
  participantCharacterIds: z.array(uuid).min(1).max(5),
  relationshipStart: z.string().min(1).max(60),
  state: jsonRecord.default({})
});

export const circleSchema = z.object({
  name: z.string().trim().min(1).max(80),
  characterIds: z.array(uuid).min(2).max(5)
});

export const eventSchema = z.object({
  actorParticipantId: uuid.nullable().optional(),
  targetParticipantId: uuid.nullable().optional(),
  eventType: z.string().regex(/^[A-Z_]{3,80}$/),
  payload: jsonRecord.default({})
});

export const syncSchema = z.object({
  state: jsonRecord,
  events: z.array(eventSchema).max(100).default([])
});

